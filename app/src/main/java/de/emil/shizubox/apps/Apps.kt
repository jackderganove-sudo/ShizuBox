package de.emil.shizubox.apps

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import de.emil.shizubox.shizuku.ShellResult
import de.emil.shizubox.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppEntry(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    /** Disabled with `pm disable-user` ("eingefroren"). */
    val frozen: Boolean,
    /** False if uninstalled for the current user but still on the device. */
    val installed: Boolean,
    val versionName: String?,
    /** Size of all APK files of the app (base + splits). */
    val sizeBytes: Long = 0,
    val firstInstall: Long = 0,
    val lastUpdate: Long = 0,
    /** Installed as split APKs (several files). */
    val isSplit: Boolean = false,
    /** Package that installed the app (Play Store, F-Droid, …), if known. */
    val installer: String? = null,
)

/** [development]: signature|development permission like WRITE_SECURE_SETTINGS, grantable via `pm grant`. */
data class PermissionEntry(val name: String, val label: String, val granted: Boolean, val development: Boolean = false)

data class ActivityEntry(val name: String, val exported: Boolean, val label: String?)

/** Technical facts for the details page. */
data class AppTech(
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val uid: Int,
    val apkPath: String,
    val dataDir: String?,
    val signatureSha256: String?,
    val installerLabel: String?,
    /** Per-app language ("" = follows the system), null if unknown (no Shizuku). */
    val locale: String?,
    /** Languages the app declares in its localeConfig; empty if it declares none. */
    val supportedLocales: List<String>,
    val activities: List<ActivityEntry>,
)

data class AppOpEntry(val op: String, val mode: String)

enum class BatteryMode(private val labelRes: Int, private val hintRes: Int) {
    Unrestricted(R.string.bm_unrestricted, R.string.bm_unrestricted_sub),
    Optimized(R.string.bm_optimized, R.string.bm_optimized_sub),
    Restricted(R.string.bm_restricted, R.string.bm_restricted_sub),
    ;
    val label get() = t(labelRes)
    val hint get() = t(hintRes)
}

data class AppDetails(
    val entry: AppEntry,
    val permissions: List<PermissionEntry>,
    val appOps: List<AppOpEntry>,
    val battery: BatteryMode,
    val standbyBucket: Int?,
    val tech: AppTech,
)

/**
 * App management. Tested on Android 17 via Shizuku (ADB):
 * - `pm disable-user` / `pm enable` work for user and system apps.
 * - `pm uninstall -k --user 0` is refused for system apps ("only root can delete system app for a
 *   particular user"), so system apps can only be frozen. User apps can be uninstalled.
 */
object Apps {
    private lateinit var pm: PackageManager
    private lateinit var appContext: Context
    private val iconCache = LruCache<String, Bitmap>(200)

    val APP_OP_MODES = listOf("allow", "ignore", "deny", "foreground", "default")

    fun init(context: Context) {
        pm = context.packageManager
        appContext = context.applicationContext
    }

    suspend fun list(): List<AppEntry> = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
        pm.getInstalledPackages(flags).map { it.toEntry() }.sortedBy { it.label.lowercase() }
    }

    private fun PackageInfo.toEntry(): AppEntry {
        val ai = applicationInfo!!
        val apks = apkPaths(ai)
        return AppEntry(
            pkg = packageName,
            label = ai.loadLabel(pm).toString(),
            isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            frozen = !ai.enabled,
            installed = ai.flags and ApplicationInfo.FLAG_INSTALLED != 0,
            versionName = versionName,
            sizeBytes = apks.sumOf { File(it).length() },
            firstInstall = firstInstallTime,
            lastUpdate = lastUpdateTime,
            isSplit = !ai.splitSourceDirs.isNullOrEmpty(),
            installer = runCatching { pm.getInstallSourceInfo(packageName).installingPackageName }.getOrNull(),
        )
    }

    private fun PackageInfo.toTech(locale: String?): AppTech {
        val ai = applicationInfo!!
        val cert = signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }?.lastOrNull()
        val sha = cert?.let {
            java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString(":") { b -> "%02X".format(b) }
        }
        val installer = runCatching { pm.getInstallSourceInfo(packageName).installingPackageName }.getOrNull()
        val supported = runCatching {
            val lc = android.app.LocaleConfig(appContext.createPackageContext(packageName, 0))
            val list = lc.supportedLocales
            if (lc.status == android.app.LocaleConfig.STATUS_SUCCESS && list != null) List(list.size()) { list[it].toLanguageTag() } else emptyList()
        }.getOrDefault(emptyList())
        return AppTech(
            versionCode = longVersionCode,
            targetSdk = ai.targetSdkVersion,
            minSdk = ai.minSdkVersion,
            uid = ai.uid,
            apkPath = ai.sourceDir.orEmpty(),
            dataDir = ai.dataDir,
            signatureSha256 = sha,
            installerLabel = installer?.let { i -> runCatching { pm.getApplicationInfo(i, 0).loadLabel(pm).toString() }.getOrDefault(i) },
            locale = locale,
            supportedLocales = supported,
            activities = activities.orEmpty().map {
                ActivityEntry(it.name, it.exported && it.enabled, it.nonLocalizedLabel?.toString() ?: runCatching { if (it.labelRes != 0) it.loadLabel(pm).toString() else null }.getOrNull())
            }.sortedWith(compareBy({ !it.exported }, { it.name })),
        )
    }

    /** Per-app language through the system's locale manager ("" resets to the system language). */
    suspend fun setLocale(pkg: String, tag: String) = exec("cmd locale set-app-locales $pkg --locales \"$tag\"")

    /** base.apk plus all split APKs of an installed app. */
    fun apkPaths(ai: ApplicationInfo): List<String> =
        listOfNotNull(ai.sourceDir) + ai.splitSourceDirs.orEmpty()

    fun apkPaths(pkg: String): List<String> = apkPaths(pm.getApplicationInfo(pkg, 0))

    fun launchIntent(pkg: String) = pm.getLaunchIntentForPackage(pkg)

    /** Reinstalls the app from its own APK files (e.g. to repair it or reset the install source). */
    suspend fun reinstallFromApks(pkg: String) = exec("pm install -r ${apkPaths(pkg).joinToString(" ")}")

    fun icon(pkg: String): Bitmap? {
        iconCache.get(pkg)?.let { return it }
        return try {
            pm.getApplicationIcon(pkg).toBitmap(96, 96).also { iconCache.put(pkg, it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun details(pkg: String): AppDetails = withContext(Dispatchers.IO) {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(
            pkg,
            PackageManager.GET_PERMISSIONS or PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS or
                PackageManager.GET_ACTIVITIES or PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val requested = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val permissions = requested.mapIndexedNotNull { i, name ->
            val pi = try { pm.getPermissionInfo(name, 0) } catch (_: Exception) { return@mapIndexedNotNull null }
            // Runtime ("dangerous") and development permissions can be granted/revoked.
            val development = pi.protection == PermissionInfo.PROTECTION_SIGNATURE &&
                pi.protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0 &&
                // App-op permissions (overlay, usage access) are switched through AppOps instead.
                pi.protectionFlags and PermissionInfo.PROTECTION_FLAG_APPOP == 0
            if (pi.protection != PermissionInfo.PROTECTION_DANGEROUS && !development) return@mapIndexedNotNull null
            PermissionEntry(
                name = name,
                label = if (development) name.substringAfterLast('.') else pi.loadLabel(pm).toString().replaceFirstChar { it.uppercase() },
                granted = flags.getOrElse(i) { 0 } and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0,
                development = development,
            )
        }.distinctBy { it.name }.sortedWith(compareBy({ it.development }, { it.label }))

        // Without Shizuku the shell parts are simply empty: the rest of the page still works.
        val out = (if (!ShizukuManager.isReady) "" else runCatching {
            exec(
                "appops get $pkg; echo '###'; am get-standby-bucket $pkg; echo '###'; " +
                    "dumpsys deviceidle whitelist | grep -c \",$pkg,\"; echo '###'; cmd locale get-app-locales $pkg",
            ).out
        }.getOrDefault("")).split("###")
        val ops = parseAppOps(out.getOrElse(0) { "" })
        val bucket = out.getOrElse(1) { "" }.trim().toIntOrNull()
        val whitelisted = (out.getOrElse(2) { "" }.trim().toIntOrNull() ?: 0) > 0
        val runAnyIgnored = ops.any { it.op == "RUN_ANY_IN_BACKGROUND" && it.mode == "ignore" }
        AppDetails(
            entry = info.toEntry(),
            permissions = permissions,
            appOps = ops,
            battery = when {
                whitelisted -> BatteryMode.Unrestricted
                runAnyIgnored -> BatteryMode.Restricted
                else -> BatteryMode.Optimized
            },
            standbyBucket = bucket,
            tech = info.toTech(
                // "Locales for com.foo for user 0 are [de]"
                locale = out.getOrNull(3)?.let { l -> if ('[' in l) l.substringAfter('[').substringBefore(']').split(',').first().trim() else null },
            ),
        )
    }

    /** Lines look like "OP_NAME: mode; time=..." (the first one may start with "Uid mode: "). */
    private fun parseAppOps(text: String): List<AppOpEntry> {
        val regex = Regex("""([A-Z][A-Z0-9_]+): (allow|ignore|deny|default|foreground)\b""")
        return text.lines().mapNotNull { line ->
            regex.find(line)?.let { AppOpEntry(it.groupValues[1], it.groupValues[2]) }
        }.distinctBy { it.op }.sortedBy { it.op }
    }

    suspend fun freeze(pkgs: Collection<String>) =
        exec(pkgs.joinToString("; ") { "pm disable-user --user 0 $it" })

    suspend fun unfreeze(pkgs: Collection<String>) =
        exec(pkgs.joinToString("; ") { "pm enable --user 0 $it" })

    suspend fun forceStop(pkg: String) = exec("am force-stop $pkg")

    suspend fun forceStop(pkgs: Collection<String>) = exec(pkgs.joinToString("; ") { "am force-stop $it" })

    /** User apps only; returns the packages that could not be removed. */
    suspend fun uninstall(pkgs: Collection<String>): List<String> = pkgs.filterNot { uninstall(it).let { r -> r.ok && "Success" in r.out } }

    /** Only for user apps; system apps can only be frozen on this Android version. */
    suspend fun uninstall(pkg: String) = exec("pm uninstall --user 0 $pkg")

    /** Brings back an app that was uninstalled for the user but is still on the device. */
    suspend fun reinstall(pkg: String) = exec("cmd package install-existing --user 0 $pkg")

    suspend fun setPermission(pkg: String, permission: String, grant: Boolean) =
        exec("pm ${if (grant) "grant" else "revoke"} $pkg $permission")

    suspend fun setAppOp(pkg: String, op: String, mode: String) = exec("appops set $pkg $op $mode")

    /** Same three states as the system's app battery settings. */
    suspend fun setBattery(pkg: String, mode: BatteryMode) = exec(
        when (mode) {
            BatteryMode.Unrestricted -> "cmd deviceidle whitelist +$pkg; appops set $pkg RUN_ANY_IN_BACKGROUND allow"
            BatteryMode.Optimized -> "cmd deviceidle whitelist -$pkg; appops set $pkg RUN_ANY_IN_BACKGROUND allow"
            BatteryMode.Restricted -> "cmd deviceidle whitelist -$pkg; appops set $pkg RUN_ANY_IN_BACKGROUND ignore"
        },
    )

    private suspend fun exec(cmd: String): ShellResult = ShizukuManager.exec(cmd, timeoutMs = 5000)
}

fun bucketLabel(bucket: Int?) = when (bucket) {
    5 -> t(R.string.bk_exempt)
    10 -> t(R.string.bk_active)
    20 -> t(R.string.bk_working)
    30 -> t(R.string.bk_frequent)
    40 -> t(R.string.bk_rare)
    45 -> t(R.string.bk_restricted)
    50 -> t(R.string.bk_never)
    null -> "?"
    else -> bucket.toString()
}
