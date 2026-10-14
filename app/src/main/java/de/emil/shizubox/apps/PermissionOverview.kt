package de.emil.shizubox.apps

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import de.emil.shizubox.R
import de.emil.shizubox.shizuku.ShizukuManager
import de.emil.shizubox.t
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A permission category: either runtime permissions (granted per app, revoked with `pm revoke`)
 * or an app-op based special access (queried with `appops query-op`, tested on Android 17).
 */
enum class PermGroup(private val labelRes: Int, val permissions: List<String> = emptyList(), val appOp: String? = null) {
    Camera(R.string.pg_camera, listOf(Manifest.permission.CAMERA)),
    Microphone(R.string.pg_microphone, listOf(Manifest.permission.RECORD_AUDIO)),
    Location(
        R.string.pg_location,
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
    ),
    Contacts(R.string.pg_contacts, listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)),
    Phone(
        R.string.pg_phone,
        listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
    ),
    Sms(R.string.pg_sms, listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)),
    Calendar(R.string.pg_calendar, listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)),
    Media(
        R.string.pg_media,
        listOf(
            "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO",
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ),
    ),
    Nearby(R.string.pg_nearby, listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.NEARBY_WIFI_DEVICES")),
    Activity(R.string.pg_activity, listOf(Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.BODY_SENSORS)),
    Notifications(R.string.pg_notifications, listOf("android.permission.POST_NOTIFICATIONS")),
    Overlay(R.string.pg_overlay, appOp = "SYSTEM_ALERT_WINDOW"),
    InstallApps(R.string.pg_install, appOp = "REQUEST_INSTALL_PACKAGES"),
    AllFiles(R.string.pg_all_files, appOp = "MANAGE_EXTERNAL_STORAGE"),
    ;
    val label get() = t(labelRes)
}

data class PermHolder(val pkg: String, val label: String, val isSystem: Boolean, val granted: List<String>)

object PermissionOverview {
    /** Apps holding each group's permissions; special accesses need Shizuku to be listed. */
    suspend fun load(context: Context): Map<PermGroup, List<PermHolder>> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        fun label(p: PackageInfo) = p.applicationInfo?.loadLabel(pm)?.toString() ?: p.packageName
        fun system(p: PackageInfo) = (p.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0

        val ops = PermGroup.entries.mapNotNull { it.appOp }
        val opHolders: Map<String, Set<String>> = if (!ShizukuManager.isReady) emptyMap() else runCatching {
            val out = ShizukuManager.exec(ops.joinToString("; ") { "echo '#$it'; appops query-op --user 0 $it allow" }, timeoutMs = 10_000).out
            val result = mutableMapOf<String, MutableSet<String>>()
            var current = ""
            out.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                if (line.startsWith("#")) current = line.drop(1) else result.getOrPut(current) { mutableSetOf() } += line
            }
            result
        }.getOrDefault(emptyMap())

        PermGroup.entries.associateWith { g ->
            if (g.appOp != null) {
                val set = opHolders[g.appOp].orEmpty()
                packages.filter { it.packageName in set }.map { PermHolder(it.packageName, label(it), system(it), listOf(g.appOp)) }
            } else {
                packages.mapNotNull { p ->
                    val req = p.requestedPermissions ?: return@mapNotNull null
                    val flags = p.requestedPermissionsFlags ?: return@mapNotNull null
                    val granted = req.indices
                        .filter { req[it] in g.permissions && flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
                        .map { req[it] }
                    if (granted.isEmpty()) null else PermHolder(p.packageName, label(p), system(p), granted)
                }
            }.sortedBy { it.label.lowercase() }
        }
    }

    /** Takes the whole group away from the app. */
    suspend fun revoke(group: PermGroup, holder: PermHolder) = ShizukuManager.exec(
        if (group.appOp != null) "appops set ${holder.pkg} ${group.appOp} ignore"
        else holder.granted.joinToString("; ") { "pm revoke ${holder.pkg} $it" },
    )
}
