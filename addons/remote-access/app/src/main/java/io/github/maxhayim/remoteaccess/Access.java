package io.github.maxhayim.remoteaccess;

/** The wording and the address checks, with no Android types, so the JVM tests cover them. */
final class Access {
    enum Adb { OFF, USB_ONLY, NETWORK }

    private Access() {
    }

    /**
     * What ADB can be reached by. adb_enabled is the developer-options switch; a TCP port (adb tcpip) or wireless
     * debugging makes it reachable over the network, which is what matters on a TV with no USB cable in.
     */
    static Adb adb(boolean enabled, boolean wireless, int tcpPort) {
        if (!enabled) return Adb.OFF;
        return wireless || tcpPort > 0 ? Adb.NETWORK : Adb.USB_ONLY;
    }

    static String adbValue(Adb a, boolean wireless, int tcpPort) {
        switch (a) {
            case NETWORK:
                return tcpPort > 0 ? "On, port " + tcpPort : wireless ? "Wireless debugging" : "On";
            case USB_ONLY:
                return "USB only";
            default:
                return "Off";
        }
    }

    /** -1 for an unset, empty or odd service.adb.tcp.port value. */
    static int port(String prop) {
        if (prop == null) return -1;
        try {
            int p = Integer.parseInt(prop.trim());
            return p > 0 && p < 65536 ? p : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Tailscale gives each device an address in 100.64.0.0/10 (the carrier-grade NAT range). */
    static boolean tailscaleAddress(String ip) {
        if (ip == null) return false;
        String[] p = ip.split("\\.");
        if (p.length != 4) return false;
        try {
            int a = Integer.parseInt(p[0]), b = Integer.parseInt(p[1]);
            return a == 100 && b >= 64 && b <= 127;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static String tailscale(boolean installed, String address) {
        if (!installed) return "Not installed";
        return address != null ? "Connected, " + address : "Installed, not connected";
    }
}
