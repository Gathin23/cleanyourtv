package com.gathin.tvhome;

/**
 * network_status_icon: what the network pill says about the connection, from what Android reports. Wi-Fi shows its
 * signal in bars (weak, medium, strong); the other states each have an icon and words of their own.
 */
final class NetworkStatus {
    static final int WIFI_OFF = 0;
    static final int OFFLINE = 1;
    static final int CONNECTING = 2;
    static final int SIGN_IN = 3;
    static final int NO_INTERNET = 4;
    static final int WIFI = 5;
    static final int ETHERNET = 6;
    /** Online some other way (USB tethering, mobile): the plain icon and the transport's name. */
    static final int OTHER = 7;
    /** Signal levels WifiManager.calculateSignalLevel is asked for: 0 and 1 weak, 2 medium, 3 strong. */
    static final int LEVELS = 4;

    private NetworkStatus() {
    }

    /**
     * The state, most specific first. connecting: Wi-Fi is joining a network (handshake, or joined with no address
     * yet), or joined and Android is still checking for internet; captive: the network wants a login page;
     * validated: Android reached the internet through it.
     */
    static int of(boolean wifiEnabled, boolean online, boolean ethernet, boolean wifi, boolean validated,
                  boolean captive, boolean connecting) {
        if (ethernet) return ETHERNET;
        if (online && captive) return SIGN_IN;
        if (online && !validated) return connecting ? CONNECTING : NO_INTERNET;
        if (online) return wifi ? WIFI : OTHER;
        if (!wifiEnabled) return WIFI_OFF;
        return connecting ? CONNECTING : OFFLINE;
    }

    /** The pill's icon; level is the Wi-Fi signal, 0 to LEVELS - 1. */
    static int icon(int state, int level) {
        switch (state) {
            case WIFI_OFF:
                return R.drawable.ic_net_wifi_off;
            case OFFLINE:
            case CONNECTING:
                return R.drawable.ic_net_wifi_0;
            case SIGN_IN:
            case NO_INTERNET:
                return R.drawable.ic_net_wifi_alert;
            case WIFI:
                return bars(level);
            case ETHERNET:
                return R.drawable.ic_ethernet;
            default:
                return R.drawable.ic_wifi;
        }
    }

    /** The bars for a signal level: one while weak, two at medium, all three while strong. */
    static int bars(int level) {
        return level >= LEVELS - 1 ? R.drawable.ic_wifi : level == LEVELS - 2 ? R.drawable.ic_net_wifi_2
                : R.drawable.ic_net_wifi_1;
    }

    /** The words in place of the network name, or 0 to keep the name. */
    static int label(int state) {
        switch (state) {
            case WIFI_OFF:
                return R.string.wifi_off;
            case OFFLINE:
                return R.string.offline;
            case CONNECTING:
                return R.string.connecting;
            case SIGN_IN:
                return R.string.wifi_sign_in;
            case NO_INTERNET:
                return R.string.no_internet;
            case ETHERNET:
                return R.string.ethernet;
            default:
                return 0;
        }
    }
}
