package io.github.justinnewman.bluetoothtoggle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;

import org.junit.Test;

/** The table behind the panel: words, icon and button for each adapter state, and when an app may switch. */
public class StatesTest {
    @Test
    public void wordsFollowTheAdapter() {
        assertEquals(R.string.state_on, States.words(BluetoothAdapter.STATE_ON));
        assertEquals(R.string.state_off, States.words(BluetoothAdapter.STATE_OFF));
        assertEquals(R.string.state_turning_on, States.words(BluetoothAdapter.STATE_TURNING_ON));
        assertEquals(R.string.state_turning_off, States.words(BluetoothAdapter.STATE_TURNING_OFF));
        assertEquals(R.string.state_none, States.words(States.NONE));
    }

    @Test
    public void theButtonOffersTheWayBack() {
        assertEquals(R.string.turn_off, States.action(BluetoothAdapter.STATE_ON));
        assertEquals(R.string.turn_off, States.action(BluetoothAdapter.STATE_TURNING_ON));
        assertEquals(R.string.turn_on, States.action(BluetoothAdapter.STATE_OFF));
        assertEquals(R.string.turn_on, States.action(BluetoothAdapter.STATE_TURNING_OFF));
        assertEquals(R.drawable.ic_on, States.icon(BluetoothAdapter.STATE_ON));
        assertEquals(R.drawable.ic_off, States.icon(BluetoothAdapter.STATE_OFF));
    }

    @Test
    public void onlyASettledStateIsSwitched() {
        assertTrue(States.settled(BluetoothAdapter.STATE_ON));
        assertTrue(States.settled(BluetoothAdapter.STATE_OFF));
        assertFalse(States.settled(BluetoothAdapter.STATE_TURNING_ON));
        assertFalse(States.settled(BluetoothAdapter.STATE_TURNING_OFF));
        assertFalse(States.settled(States.NONE));
    }

    @Test
    public void appsMaySwitchUpToAndroid12L() {
        assertTrue(States.appsMaySwitch(26));
        assertTrue(States.appsMaySwitch(31));
        assertTrue(States.appsMaySwitch(32));
        assertFalse(States.appsMaySwitch(33));
        assertFalse(States.appsMaySwitch(34));
    }
}
