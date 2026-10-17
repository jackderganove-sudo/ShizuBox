package de.emil.shizubox.updates

import de.emil.shizubox.updates.UpdateGate.Decision
import de.emil.shizubox.updates.UpdateGate.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateGateTest {
    private val self = "de.emil.shizubox"
    private fun check(tracked: String = "org.app", apk: String = "org.app", installed: Boolean = true, mismatch: Boolean? = false, gate: Boolean = true, key: Boolean = true) =
        UpdateGate.check(tracked, apk, installed, mismatch, self, gate, key)

    @Test fun otherPackageIsRefused() = assertEquals(Decision.Refuse(Reason.WrongPackage, "com.evil"), check(apk = "com.evil"))
    @Test fun notInstalledNeedsConfirmation() = assertEquals(Decision.Refuse(Reason.NotInstalled), check(installed = false))
    @Test fun unknownTrackedPackageNeedsConfirmation() = assertEquals(Decision.Refuse(Reason.NotInstalled), check(tracked = ""))
    @Test fun otherSignatureIsRefused() = assertEquals(Decision.Refuse(Reason.WrongSignature), check(mismatch = true))
    @Test fun uncheckedSignatureIsRefused() = assertEquals(Decision.Refuse(Reason.SignatureUnknown), check(mismatch = null))
    @Test fun gateWithoutKeyIsRefused() = assertEquals(Decision.Refuse(Reason.NoVirusTotalKey), check(key = false))
    @Test fun normalUpdateGoesThroughVirusTotal() = assertEquals(Decision.Install(trustedSelf = false, virusTotal = true), check())
    @Test fun selfUpdateSameKeySkipsVirusTotal() = assertEquals(Decision.Install(trustedSelf = true, virusTotal = false), check(tracked = self, apk = self, key = false))
    @Test fun selfUpdateOtherKeyIsRefused() = assertEquals(Decision.Refuse(Reason.WrongSignature), check(tracked = self, apk = self, mismatch = true))
    @Test fun foreignApkInSelfReleaseIsRefused() = assertEquals(Decision.Refuse(Reason.WrongPackage, "com.other"), check(tracked = self, apk = "com.other"))
}
