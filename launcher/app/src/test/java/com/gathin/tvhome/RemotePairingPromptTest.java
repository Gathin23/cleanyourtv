package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests external physical input device filtering for prompt_remote_pairing. */
public class RemotePairingPromptTest {

    @Test
    public void inputDeviceFilterRequiresExternalAndNonVirtual() {
        // Virtual or internal devices (e.g. cec_input, virtual-search, aml_keypad) must not count as physical remote
        assertFalse(MainActivity.isRemoteOrKeyboard(0x00000201, false, false));
        assertFalse(MainActivity.isRemoteOrKeyboard(0x00000201, true, true));
        assertFalse(MainActivity.isRemoteOrKeyboard(0x00000000, true, false));

        // External physical DPAD/keyboard/gamepad counts as a remote/controller
        assertTrue(MainActivity.isRemoteOrKeyboard(0x00000201, true, false));
        assertTrue(MainActivity.isRemoteOrKeyboard(0x00000101, true, false));
    }

    @Test
    public void checksOnlyOncePerKnownBoot() {
        assertTrue(RemotePairingPrompt.due(12, -1));
        assertTrue(RemotePairingPrompt.due(12, 11));
        assertFalse(RemotePairingPrompt.due(12, 12));
        assertFalse(RemotePairingPrompt.due(-1, -1));
    }

    @Test
    public void gracePeriodAccountsForTimeSinceFirstCheck() {
        assertEquals(30_000L, RemotePairingPrompt.remainingDelay(10_000L, 10_000L));
        assertEquals(20_000L, RemotePairingPrompt.remainingDelay(10_000L, 20_000L));
        assertEquals(0L, RemotePairingPrompt.remainingDelay(10_000L, 40_000L));
    }
}
