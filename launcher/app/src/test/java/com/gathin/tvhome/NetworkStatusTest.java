package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for network_status_icon: which state the pill shows for what Android reports. */
public class NetworkStatusTest {

    private static int of(boolean wifiEnabled, boolean online, boolean ethernet, boolean wifi, boolean validated,
                          boolean captive, boolean connecting) {
        return NetworkStatus.of(wifiEnabled, online, ethernet, wifi, validated, captive, connecting);
    }

    @Test
    public void aWorkingWifiShowsItsBars() {
        assertEquals(NetworkStatus.WIFI, of(true, true, false, true, true, false, false));
        assertEquals(R.drawable.ic_net_wifi_1, NetworkStatus.bars(0));
        assertEquals(R.drawable.ic_net_wifi_1, NetworkStatus.bars(1));
        assertEquals(R.drawable.ic_net_wifi_2, NetworkStatus.bars(2));
        assertEquals(R.drawable.ic_wifi, NetworkStatus.bars(3));
        assertEquals(0, NetworkStatus.label(NetworkStatus.WIFI));     // the network's name stays
    }

    @Test
    public void connectedWithoutInternetOrALoginPage() {
        assertEquals(NetworkStatus.NO_INTERNET, of(true, true, false, true, false, false, false));
        assertEquals(NetworkStatus.SIGN_IN, of(true, true, false, true, false, true, false));
        // Just joined, and Android's internet check has not finished yet.
        assertEquals(NetworkStatus.CONNECTING, of(true, true, false, true, false, false, true));
    }

    @Test
    public void notConnected() {
        assertEquals(NetworkStatus.WIFI_OFF, of(false, false, false, false, false, false, false));
        assertEquals(NetworkStatus.OFFLINE, of(true, false, false, false, false, false, false));
        assertEquals(NetworkStatus.CONNECTING, of(true, false, false, false, false, false, true));
    }

    @Test
    public void ethernetWinsOverWifi() {
        assertEquals(NetworkStatus.ETHERNET, of(true, true, true, true, true, false, false));
        assertEquals(NetworkStatus.ETHERNET, of(false, true, true, false, false, false, false));
    }
}
