package de.emil.shizubox.updates

/**
 * May a downloaded update be installed silently (through Shizuku, without asking)?
 * Pure logic so it can be unit-tested; found by Daniel's code review (0.11.1):
 * the package of the downloaded APK was never compared with the tracked app, and an APK whose
 * package was not installed counted as "signature fine" – a release asset of another app would have
 * been installed silently (as ShizuBox' own update even without the VirusTotal check).
 */
object UpdateGate {
    sealed interface Decision {
        /** [trustedSelf]: ShizuBox' own update, same package and same signing key. */
        data class Install(val trustedSelf: Boolean, val virusTotal: Boolean) : Decision
        data class Refuse(val reason: Reason, val detail: String = "") : Decision
    }

    enum class Reason {
        /** The APK belongs to another package than the tracked app. */
        WrongPackage,
        /** Not installed (or the tracked package is still unknown): only with the user's confirmation. */
        NotInstalled,
        /** Signed with another key than the installed app. */
        WrongSignature,
        /** The signature could not be checked – fail safe for silent installs. */
        SignatureUnknown,
        /** The VirusTotal gate is on but no API key is set: nothing could be checked. */
        NoVirusTotalKey,
    }

    /**
     * @param trackedPackage package of the tracked app ("" = not known yet)
     * @param apkPackage package inside the downloaded APK
     * @param installed whether [apkPackage] is installed on this device
     * @param signatureMismatch from [de.emil.shizubox.installer.SignatureCheck]: false = same key, true = other key, null = unknown
     */
    fun check(
        trackedPackage: String,
        apkPackage: String,
        installed: Boolean,
        signatureMismatch: Boolean?,
        selfPackage: String,
        virusTotalGate: Boolean,
        hasVirusTotalKey: Boolean,
    ): Decision {
        if (apkPackage.isBlank()) return Decision.Refuse(Reason.WrongPackage)
        if (trackedPackage.isNotBlank() && apkPackage != trackedPackage) return Decision.Refuse(Reason.WrongPackage, apkPackage)
        if (trackedPackage.isBlank() || !installed) return Decision.Refuse(Reason.NotInstalled)
        when (signatureMismatch) {
            true -> return Decision.Refuse(Reason.WrongSignature)
            null -> return Decision.Refuse(Reason.SignatureUnknown)
            false -> Unit
        }
        val trustedSelf = apkPackage == selfPackage
        if (!trustedSelf && virusTotalGate && !hasVirusTotalKey) return Decision.Refuse(Reason.NoVirusTotalKey)
        return Decision.Install(trustedSelf, virusTotal = virusTotalGate && !trustedSelf)
    }
}
