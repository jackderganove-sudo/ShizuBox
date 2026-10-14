/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: no Koin/Room/telemetry, German UI texts, Shizuku-only backend.
 */
package de.emil.shizubox.installer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.solrudev.ackpine.DelicateAckpineApi
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.session.progress
import ru.solrudev.ackpine.shizuku.shizuku

data class InstallOptions(
    val allowDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    /** Show "Google Play" as the installer source (some apps check this). */
    val spoofPlayStore: Boolean = false,
)

sealed interface InstallResult {
    data object Success : InstallResult
    data class Failure(val title: String, val hint: String) : InstallResult
}

/** Silent install of the selected splits through Shizuku, as one package session. */
object ShizukuApkInstaller {

    @OptIn(DelicateAckpineApi::class)
    suspend fun install(
        context: Context,
        name: String,
        uris: List<Uri>,
        options: InstallOptions,
        onProgress: (Float) -> Unit,
    ): InstallResult = coroutineScope {
        val installer = PackageInstaller.getInstance(context)
        val session = installer.createSession(uris) {
            this.name = name
            confirmation = Confirmation.IMMEDIATE
            shizuku {
                // Without it an update of an installed app fails with INSTALL_FAILED_ALREADY_EXISTS.
                replaceExisting = true
                requestDowngrade = options.allowDowngrade
                grantAllRequestedPermissions = options.grantAllPermissions
                if (options.spoofPlayStore) installerPackageName = "com.android.vending"
            }
        }
        val progressJob = session.progress
            .onEach { if (it.max > 0) onProgress(it.progress.toFloat() / it.max) }
            .launchIn(this)
        try {
            when (val state = session.await()) {
                Session.State.Succeeded -> InstallResult.Success
                is Session.State.Failed -> describe(state.failure)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            InstallResult.Failure("Installation fehlgeschlagen", e.message ?: e.toString())
        } finally {
            progressJob.cancel()
        }
    }

    private fun describe(f: InstallFailure): InstallResult.Failure {
        val raw = f.message.orEmpty()
        return when (f) {
            is InstallFailure.Conflict -> InstallResult.Failure(
                "Konflikt mit installierter App",
                "Meist eine andere Signatur. Die installierte Version zuerst deinstallieren.",
            )
            is InstallFailure.Incompatible -> InstallResult.Failure(
                "Nicht kompatibel",
                if ("ABI" in raw || "abi" in raw) "Die App ist nicht für die CPU dieses Handys gebaut." else "Android-Version oder Gerät wird nicht unterstützt. $raw",
            )
            is InstallFailure.Invalid -> InstallResult.Failure("Ungültige Datei", "Die APK ist beschädigt oder unvollständig. $raw")
            is InstallFailure.Storage -> InstallResult.Failure("Zu wenig Speicher", "Speicher freigeben und erneut versuchen.")
            is InstallFailure.Blocked -> InstallResult.Failure("Blockiert", "Das System hat die Installation verhindert. $raw")
            is InstallFailure.Aborted -> InstallResult.Failure("Abgebrochen", raw.ifBlank { "Die Installation wurde abgebrochen." })
            is InstallFailure.Timeout -> InstallResult.Failure("Zeitüberschreitung", "Erneut versuchen.")
            is InstallFailure.Exceptional -> InstallResult.Failure("Fehler", f.exception.message ?: raw)
            is InstallFailure.Generic -> InstallResult.Failure(
                "Installation fehlgeschlagen",
                if ("DOWNGRADE" in raw) "Ältere Version: „Downgrade erlauben“ einschalten." else raw.ifBlank { "Unbekannter Fehler" },
            )
            else -> InstallResult.Failure("Installation fehlgeschlagen", raw.ifBlank { f.toString() })
        }
    }
}
