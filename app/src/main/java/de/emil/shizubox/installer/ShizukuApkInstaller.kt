/*
 * Ported from Universal Installer (https://github.com/pass-with-high-score/universal-installer),
 * Copyright (C) pass-with-high-score and contributors, licensed under GPL-3.0.
 * Adapted for ShizuBox: no Koin/Room/telemetry, German UI texts, Shizuku-only backend.
 */
package de.emil.shizubox.installer

import de.emil.shizubox.R
import de.emil.shizubox.t
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

/** Installs the selected splits as one package session: silently via Shizuku, or via the system dialog. */
object ShizukuApkInstaller {

    /** Whether the next install runs silently through Shizuku (per the install-method setting). */
    fun useShizuku(): Boolean = when (de.emil.shizubox.settings.AppSettings.current.installMethod) {
        de.emil.shizubox.settings.InstallMethod.Shizuku -> true
        de.emil.shizubox.settings.InstallMethod.System -> false
        de.emil.shizubox.settings.InstallMethod.Auto -> de.emil.shizubox.shizuku.ShizukuManager.isReady
    }

    @OptIn(DelicateAckpineApi::class)
    suspend fun install(
        context: Context,
        name: String,
        uris: List<Uri>,
        options: InstallOptions,
        onProgress: (Float) -> Unit,
    ): InstallResult = coroutineScope {
        val installer = PackageInstaller.getInstance(context)
        val silent = useShizuku()
        val session = installer.createSession(uris) {
            this.name = name
            confirmation = Confirmation.IMMEDIATE
            if (silent) {
                shizuku {
                    // Without it an update of an installed app fails with INSTALL_FAILED_ALREADY_EXISTS.
                    replaceExisting = true
                    requestDowngrade = options.allowDowngrade
                    grantAllRequestedPermissions = options.grantAllPermissions
                    if (options.spoofPlayStore) installerPackageName = "com.android.vending"
                }
            }
            // Otherwise: normal system installer with its confirmation dialog (works without Shizuku;
            // downgrade / grant-all / installer spoofing need privileges and are not available).
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
            InstallResult.Failure(t(R.string.inst_failed), e.message ?: e.toString())
        } finally {
            progressJob.cancel()
        }
    }

    private fun describe(f: InstallFailure): InstallResult.Failure {
        val raw = f.message.orEmpty()
        return when (f) {
            is InstallFailure.Conflict -> InstallResult.Failure(
                t(R.string.inst_conflict),
                t(R.string.inst_conflict_hint),
            )
            is InstallFailure.Incompatible -> InstallResult.Failure(
                t(R.string.inst_incompatible),
                if ("ABI" in raw || "abi" in raw) t(R.string.inst_abi) else t(R.string.inst_unsupported, raw),
            )
            is InstallFailure.Invalid -> InstallResult.Failure(t(R.string.inst_invalid), t(R.string.inst_invalid_hint, raw))
            is InstallFailure.Storage -> InstallResult.Failure(t(R.string.inst_storage), t(R.string.inst_storage_hint))
            is InstallFailure.Blocked -> InstallResult.Failure(t(R.string.inst_blocked), t(R.string.inst_blocked_hint, raw))
            is InstallFailure.Aborted -> InstallResult.Failure(t(R.string.inst_aborted), raw.ifBlank { t(R.string.inst_aborted_hint) })
            is InstallFailure.Timeout -> InstallResult.Failure(t(R.string.inst_timeout), t(R.string.retry))
            is InstallFailure.Exceptional -> InstallResult.Failure("Fehler", f.exception.message ?: raw)
            is InstallFailure.Generic -> InstallResult.Failure(
                t(R.string.inst_failed),
                if ("DOWNGRADE" in raw) t(R.string.inst_downgrade_hint) else raw.ifBlank { t(R.string.unknown_error) },
            )
            else -> InstallResult.Failure(t(R.string.inst_failed), raw.ifBlank { f.toString() })
        }
    }
}
