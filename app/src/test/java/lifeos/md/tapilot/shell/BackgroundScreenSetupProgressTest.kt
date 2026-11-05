package lifeos.md.tapilot.shell

import org.junit.Assert.*
import org.junit.Test

class BackgroundScreenSetupProgressTest {
    private val pairing = SetupSnapshot(true, true, true, true, true, true, false, false)

    @Test fun restoringWifiRestoresPairingNavigationRatherThanJustWaiting() {
        val progress = BackgroundScreenSetupProgress()
        assertEquals(SetupDecision.Enter(SetupStep.PAIRING), progress.advance(pairing, 0))
        assertEquals(SetupDecision.Wait, progress.advance(pairing, 1))
        assertEquals(SetupDecision.Enter(SetupStep.WIFI), progress.advance(pairing.copy(wifi = false), 2))
        assertEquals(SetupDecision.Enter(SetupStep.PAIRING), progress.advance(pairing, 3))
    }

    @Test fun interruptedDeveloperNavigationAndLostHelperReenterTheirRequiredPages() {
        val progress = BackgroundScreenSetupProgress()
        val developer = pairing.copy(developer = false)
        assertEquals(SetupDecision.Enter(SetupStep.DEVELOPER), progress.advance(developer, 0))
        assertEquals(SetupDecision.Enter(SetupStep.WIFI), progress.advance(developer.copy(wifi = false), 1))
        assertEquals(SetupDecision.Enter(SetupStep.DEVELOPER), progress.advance(developer, 2))
        assertEquals(SetupDecision.Enter(SetupStep.OVERLAY), progress.advance(pairing.copy(connected = true), 3))
        assertEquals(SetupDecision.Enter(SetupStep.PAIRING), progress.advance(pairing, 4))
    }

    @Test fun reentryGetsANewDeadlineButAnUnchangedStageDoesNot() {
        val progress = BackgroundScreenSetupProgress(timeoutMs = 100)
        assertEquals(SetupDecision.Enter(SetupStep.PAIRING), progress.advance(pairing, 0))
        assertEquals(SetupDecision.Wait, progress.advance(pairing, 99))
        assertEquals(SetupDecision.TimedOut(SetupStep.PAIRING), progress.advance(pairing, 100))
        assertEquals(SetupDecision.Enter(SetupStep.WIFI), progress.advance(pairing.copy(wifi = false), 101))
        assertEquals(SetupDecision.Enter(SetupStep.PAIRING), progress.advance(pairing, 102))
        assertEquals(SetupDecision.Wait, progress.advance(pairing, 201))
        assertEquals(SetupDecision.TimedOut(SetupStep.PAIRING), progress.advance(pairing, 202))
    }

    @Test fun revocationStopsInsteadOfReopeningConsentAndReadyRequiresNativeState() {
        val progress = BackgroundScreenSetupProgress()
        progress.advance(pairing, 0)
        assertEquals(SetupDecision.Revoked, progress.advance(pairing.copy(authorized = false), 1))
        val ready = BackgroundScreenSetupProgress()
        assertEquals(SetupDecision.Enter(SetupStep.OVERLAY), ready.advance(pairing.copy(connected = true), 0))
        assertEquals(SetupDecision.Ready, ready.advance(pairing.copy(connected = true, overlay = true), 1))
    }
}
