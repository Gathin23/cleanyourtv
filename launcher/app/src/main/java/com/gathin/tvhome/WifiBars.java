package com.gathin.tvhome;

/** wifi_signal_bars: the Wi-Fi icon with 1, 2 or 3 bars lit for a weak, moderate or strong signal. */
final class WifiBars {
    private WifiBars() {
    }

    static int count(int rssi) {
        return rssi >= -60 ? 3 : rssi >= -70 ? 2 : 1;
    }

    static int icon(int rssi) {
        switch (count(rssi)) {
            case 1: return R.drawable.ic_wifi_1;
            case 2: return R.drawable.ic_wifi_2;
            default: return R.drawable.ic_wifi;
        }
    }
}
