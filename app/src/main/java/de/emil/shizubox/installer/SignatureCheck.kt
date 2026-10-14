/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: no Koin/Room/telemetry, German UI texts, Shizuku-only backend.
 */
package de.emil.shizubox.installer

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

/**
 * Answers whether an APK can update the installed copy, or whether Android will reject it because
 * both were signed with different keys. A mismatch is the one failure no install option bypasses.
 */
object SignatureCheck {

    const val archiveFlag = PackageManager.GET_SIGNING_CERTIFICATES

    /** false = fine (or not installed), true = different key, null = could not tell (no warning). */
    fun isMismatch(context: Context, packageName: String, archive: PackageInfo?): Boolean? {
        if (packageName.isBlank() || archive == null) return null
        val pm = context.packageManager
        if (!isInstalled(pm, packageName)) return false
        return try {
            val signers = archive.signingInfo?.apkContentsSigners
            if (signers.isNullOrEmpty()) return null
            // Ask the platform instead of comparing bytes: it also accepts rotated signing keys.
            !signers.any { pm.hasSigningCertificate(packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509) }
        } catch (_: Exception) {
            null
        }
    }

    private fun isInstalled(pm: PackageManager, packageName: String) = try {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
