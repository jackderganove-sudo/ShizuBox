/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: no Koin/Room/telemetry, German UI texts, Shizuku-only backend.
 */
package de.emil.shizubox.installer

import android.graphics.drawable.Drawable
import android.net.Uri

enum class SplitType(val label: String) {
    Base("Basis"),
    Libs("CPU-Architektur"),
    Locale("Sprache"),
    ScreenDensity("Bildschirmdichte"),
    Feature("Feature"),
    Other("Sonstige"),
}

data class SplitEntry(
    val name: String,
    val type: SplitType,
    val uri: Uri,
    val sizeBytes: Long,
    val selected: Boolean = true,
)

data class ApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val icon: Drawable?,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val fileSizeBytes: Long,
    val permissions: List<String>,
    val fileFormat: String,
    val supportedAbis: List<String>,
    val splitEntries: List<SplitEntry>,
    /** Installed version on this device, null if not installed. */
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    /** true = different signing key than the installed app (update will fail); null = unknown. */
    val signatureMismatch: Boolean? = null,
) {
    val isUpdate get() = installedVersionCode != null && versionCode > installedVersionCode
    val isDowngrade get() = installedVersionCode != null && versionCode < installedVersionCode
    val isReinstall get() = installedVersionCode != null && versionCode == installedVersionCode
}
