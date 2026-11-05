package lifeos.md.tapilot.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundScreenSetupTest {
    private val fresh = SetupSnapshot(false, false, false, false, false, false, false, false)

    @Test fun consentPrecedesEverySettingsChange() {
        assertEquals(SetupStep.CONSENT, fresh.next())
        assertEquals(SetupStep.CONSENT, fresh.copy(authorized = true).next())
        assertEquals(SetupStep.CONSENT, fresh.copy(enabled = true, connected = true, overlay = true).next())
    }
    @Test fun firstSetupAdvancesOnlyWithActualNativeState() {
        val consented = fresh.copy(authorized = true, enabled = true)
        assertEquals(SetupStep.NOTIFICATIONS, consented.next())
        val notifications = consented.copy(notifications = true)
        assertEquals(SetupStep.DEVELOPER, notifications.next())
        val developer = notifications.copy(developer = true)
        assertEquals(SetupStep.USB_DEBUGGING, developer.next())
        val usb = developer.copy(usb = true)
        assertEquals(SetupStep.WIRELESS_DEBUGGING, usb.next())
        val wireless = usb.copy(wireless = true)
        assertEquals(SetupStep.PAIRING, wireless.next())
        assertEquals(SetupStep.OVERLAY, wireless.copy(connected = true).next())
        assertEquals(SetupStep.READY, wireless.copy(connected = true, overlay = true).next())
    }
    @Test fun wifiIsRequiredBeforeNewPairingButNotBeforeConsent() {
        assertEquals(SetupStep.CONSENT, fresh.copy(wifi = false).next())
        assertEquals(SetupStep.WIFI, fresh.copy(authorized = true, enabled = true, wifi = false).next())
    }
    @Test fun existingConnectionSkipsPairingAndDebugChanges() {
        val connected = fresh.copy(authorized = true, enabled = true, connected = true)
        assertEquals(SetupStep.OVERLAY, connected.next())
        assertEquals(SetupStep.READY, connected.copy(overlay = true).next())
    }
    @Test fun lostConnectionCannotReportReadyFromStoredPreferences() {
        val ready = SetupSnapshot(true, true, true, true, true, true, true, true)
        assertEquals(SetupStep.PAIRING, ready.copy(connected = false).next())
        assertEquals(SetupStep.OVERLAY, ready.copy(overlay = false).next())
        assertEquals(SetupStep.CONSENT, ready.copy(authorized = false).next())
    }
}
