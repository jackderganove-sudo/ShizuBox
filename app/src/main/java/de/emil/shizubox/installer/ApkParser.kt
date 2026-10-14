/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: no Koin/Room/telemetry, German UI texts, Shizuku-only backend.
 */
package de.emil.shizubox.installer

import de.emil.shizubox.R
import de.emil.shizubox.t
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.CloseableSequence
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import ru.solrudev.ackpine.splits.get
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.abs

/** Reads APK / APKS / XAPK / APKM files: app info plus every split with a smart pre-selection. */
object ApkParser {
    private const val TAG = "ApkParser"
    val BUNDLE_EXTENSIONS = setOf("apks", "xapk", "apkm", "zip")

    private val ABI_TOKENS = setOf("armeabi_v7a", "arm64_v8a", "x86_64", "armeabi", "x86", "mips64", "mips")
    private val DPI_TOKENS = setOf("ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")

    fun fileName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "app.apk"

    private fun splitProvider(context: Context, uri: Uri, extension: String): SplitPackage.Provider = when {
        extension == "apk" || context.contentResolver.getType(uri)?.lowercase() == "application/vnd.android.package-archive" ->
            SingletonApkSequence(uri, context).toSplitPackage()
        extension in BUNDLE_EXTENSIONS ->
            ZippedApkSplits.getApksForUri(uri, context).validate().toSplitPackage().filterCompatible(context)
        else -> SingletonApkSequence(uri, context).toSplitPackage()
    }

    suspend fun parse(context: Context, uri: Uri): ApkInfo = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val fileName = fileName(context, uri)
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val fileFormat = when (extension) {
            "apk" -> "APK"
            "apks" -> "APKS (Split-Paket)"
            "xapk" -> "XAPK (Split-Paket)"
            "apkm" -> "APKM (Split-Paket)"
            else -> extension.uppercase().ifEmpty { "APK" }
        }
        val fileSize = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)

        var packageName = ""
        var versionName = ""
        var versionCode = 0L
        var baseApkUri: Uri? = null
        val abis = mutableListOf<String>()
        val splits = mutableListOf<SplitEntry>()

        try {
            val sequence = splitProvider(context, uri, extension).get()
            var entries = try { sequence.toList() } finally { (sequence as? CloseableSequence<*>)?.close() }
            if (entries.isEmpty() && extension in BUNDLE_EXTENSIONS) {
                // Some bundles are really a single APK with a different extension.
                val fallback = SingletonApkSequence(uri, context).toSplitPackage().get()
                entries = try { fallback.toList() } finally { (fallback as? CloseableSequence<*>)?.close() }
            }
            for (entry in entries) {
                val apk = entry.apk
                if (packageName.isEmpty()) packageName = apk.packageName
                if (versionCode == 0L) versionCode = apk.versionCode
                when (apk) {
                    is Apk.Base -> {
                        if (versionName.isEmpty()) versionName = apk.versionName
                        baseApkUri = apk.uri
                        splits += SplitEntry(t(R.string.base_apk), SplitType.Base, apk.uri, apk.size)
                    }
                    is Apk.Libs -> {
                        abis += apk.abi.name
                        splits += SplitEntry(apk.abi.name, SplitType.Libs, apk.uri, apk.size)
                    }
                    is Apk.Localization -> splits += SplitEntry(apk.locale.toLanguageTag(), SplitType.Locale, apk.uri, apk.size)
                    is Apk.ScreenDensity -> splits += SplitEntry("${apk.dpi}dpi", SplitType.ScreenDensity, apk.uri, apk.size)
                    is Apk.Feature -> splits += SplitEntry(apk.name, SplitType.Feature, apk.uri, apk.size)
                    is Apk.Other -> {
                        val re = reclassify(apk.name)
                        if (re != null) {
                            if (re.first == SplitType.Libs) abis += re.second
                            splits += SplitEntry(re.second, re.first, apk.uri, apk.size)
                        } else {
                            splits += SplitEntry(apk.name.ifBlank { apk.uri.lastPathSegment ?: "?" }, SplitType.Other, apk.uri, apk.size)
                        }
                    }
                }
            }
            if (splits.isEmpty()) {
                splits += SplitEntry(t(R.string.base_apk), SplitType.Base, uri, fileSize)
                baseApkUri = uri
            }
            smartPick(splits, context)
        } catch (e: Exception) {
            Log.e(TAG, "Reading splits failed", e)
            if (splits.isEmpty()) splits += SplitEntry(t(R.string.base_apk), SplitType.Base, uri, fileSize)
        }

        // Full manifest info (label, icon, permissions, SDK, signature) from the base APK.
        var appName = fileName.substringBeforeLast('.')
        var icon: android.graphics.drawable.Drawable? = null
        var permissions = emptyList<String>()
        var minSdk = 0
        var targetSdk = 0
        var signatureMismatch: Boolean? = null
        val temp = File(context.cacheDir, "parse_${System.currentTimeMillis()}.apk")
        try {
            context.contentResolver.openInputStream(baseApkUri ?: uri)?.use { input ->
                temp.outputStream().use { input.copyTo(it) }
            }
            val flags = (PackageManager.GET_PERMISSIONS or SignatureCheck.archiveFlag).toLong()
            val info = runCatching { pm.getPackageArchiveInfo(temp.absolutePath, PackageManager.PackageInfoFlags.of(flags)) }.getOrNull()
            if (info != null) {
                info.applicationInfo?.sourceDir = temp.absolutePath
                info.applicationInfo?.publicSourceDir = temp.absolutePath
                appName = info.applicationInfo?.loadLabel(pm)?.toString() ?: appName
                icon = runCatching { info.applicationInfo?.loadIcon(pm) }.getOrNull()
                permissions = info.requestedPermissions?.toList().orEmpty()
                minSdk = info.applicationInfo?.minSdkVersion ?: 0
                targetSdk = info.applicationInfo?.targetSdkVersion ?: 0
                signatureMismatch = SignatureCheck.isMismatch(context, info.packageName, info)
                if (packageName.isEmpty()) packageName = info.packageName
                if (versionName.isEmpty()) versionName = info.versionName.orEmpty()
                if (versionCode == 0L) versionCode = info.longVersionCode
            }
            if (abis.isEmpty()) {
                ZipFile(temp).use { zip ->
                    val regex = Regex("^lib/([^/]+)/")
                    zip.entries().asSequence().mapNotNull { regex.find(it.name)?.groupValues?.get(1) }.toSortedSet().let(abis::addAll)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parsing manifest failed", e)
        } finally {
            temp.delete()
        }

        val installed = runCatching { pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L)) }.getOrNull()
        ApkInfo(
            appName = appName,
            packageName = packageName.ifEmpty { "?" },
            versionName = versionName,
            versionCode = versionCode,
            icon = icon,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            fileSizeBytes = fileSize,
            permissions = permissions,
            fileFormat = fileFormat,
            supportedAbis = abis.distinct(),
            splitEntries = splits,
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.longVersionCode,
            signatureMismatch = signatureMismatch,
        )
    }

    private val Apk.packageName: String
        get() = when (this) {
            is Apk.Base -> packageName
            is Apk.Libs -> packageName
            is Apk.Localization -> packageName
            is Apk.ScreenDensity -> packageName
            is Apk.Feature -> packageName
            is Apk.Other -> packageName
        }

    private val Apk.versionCode: Long
        get() = when (this) {
            is Apk.Base -> versionCode
            is Apk.Libs -> versionCode
            is Apk.Localization -> versionCode
            is Apk.ScreenDensity -> versionCode
            is Apk.Feature -> versionCode
            is Apk.Other -> versionCode
        }

    /** Splits Ackpine could not classify, recognised by their name tokens. */
    private fun reclassify(splitName: String): Pair<SplitType, String>? {
        if (splitName.isBlank()) return null
        for (token in splitName.lowercase().split('.')) {
            if (token in ABI_TOKENS) return SplitType.Libs to token.uppercase()
            if (token in DPI_TOKENS) return SplitType.ScreenDensity to "${token.uppercase()}dpi"
            val lang = token.replace('-', '_').replace('+', '_').removePrefix("b_")
            val locale = Locale.forLanguageTag(lang.replace('_', '-'))
            if (locale.language.length in 2..3 && Locale.getISOLanguages().contains(locale.language)) {
                return SplitType.Locale to locale.toLanguageTag()
            }
        }
        return null
    }

    /** Pre-selects only what this device needs: best ABI, closest density, user languages (+ en). */
    fun smartPick(entries: MutableList<SplitEntry>, context: Context) {
        if (entries.size <= 1) {
            entries.indices.forEach { entries[it] = entries[it].copy(selected = true) }
            return
        }
        val abiPriority = Build.SUPPORTED_ABIS.orEmpty().mapIndexed { i, abi -> abi.replace('-', '_').lowercase() to i }.toMap()
        val bestLibs = entries.filter { it.type == SplitType.Libs }
            .minOfOrNull { abiPriority[it.name.replace('-', '_').lowercase()] ?: Int.MAX_VALUE }
        val bestAbi = abiPriority.entries.find { it.value == bestLibs }?.key
        val deviceDpi = context.resources.displayMetrics.densityDpi
        val bestDensity = entries.filter { it.type == SplitType.ScreenDensity }
            .minByOrNull { abs((it.name.removeSuffix("dpi").toIntOrNull() ?: Int.MAX_VALUE) - deviceDpi) }?.name
        val userLangs = LocaleListCompat.getDefault().let { list ->
            (0 until list.size()).flatMap { i -> list[i]?.let { listOf(it.toLanguageTag().lowercase(), it.language.lowercase()) }.orEmpty() }
        }.toSet() + "en"

        for (i in entries.indices) {
            val e = entries[i]
            val keep = when (e.type) {
                SplitType.Base, SplitType.Feature, SplitType.Other -> true
                SplitType.Libs -> {
                    val n = e.name.replace('-', '_').lowercase()
                    (abiPriority[n] ?: Int.MAX_VALUE) == bestLibs || (bestAbi != null && n.contains(bestAbi))
                }
                SplitType.ScreenDensity -> e.name.equals(bestDensity, ignoreCase = true)
                SplitType.Locale -> {
                    val tag = e.name.lowercase()
                    tag in userLangs || tag.substringBefore('-').substringBefore('_') in userLangs
                }
            }
            entries[i] = e.copy(selected = keep)
        }
    }
}
