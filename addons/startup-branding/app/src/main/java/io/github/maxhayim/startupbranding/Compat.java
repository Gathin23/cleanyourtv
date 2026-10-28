package io.github.maxhayim.startupbranding;

import java.util.ArrayList;
import java.util.List;

/**
 * What can and can't be done about the TV's startup look, from facts about this TV. Plain Java, so the answers
 * are tested on the computer; {@link DeviceInfo} gathers the facts.
 */
final class Compat {
    enum Status {
        WORKS("Works", "Works without root"),
        DEPENDS("Not here", "Depends on the TV"),
        LAUNCHER("Needs launcher", "Needs the home app's help"),
        SYSTEM("Needs system", "Needs root or a system app"),
        NO("Not possible", "Only the maker's firmware");

        final String label, heading;

        Status(String label, String heading) {
            this.label = label;
            this.heading = heading;
        }
    }

    static final class Item {
        final String name, detail;
        final Status status;

        Item(String name, Status status, String detail) {
            this.name = name;
            this.status = status;
            this.detail = detail;
        }
    }

    /** Facts about this TV. */
    static final class Facts {
        int sdk;
        boolean picker, nameSettings, bootAnimationFound;
    }

    private Compat() {
    }

    static List<Item> items(Facts f) {
        List<Item> out = new ArrayList<>();
        out.add(new Item("Welcome screen and preview", Status.WORKS,
                "Your picture, colour and text, full screen, closed with Back or OK. No root, no permissions."));
        out.add(new Item("Pictures from a computer", Status.WORKS,
                "push-image.sh copies them into this module's own folder over ADB."));
        out.add(new Item("Pictures from the TV's file picker", f.picker ? Status.WORKS : Status.DEPENDS,
                f.picker ? "This TV has a file picker." : "This TV has none (Android TV often ships only a stub)."));
        out.add(new Item("Opening by itself after boot", Status.LAUNCHER, f.sdk >= 29
                ? "Android " + release(f.sdk) + " doesn't let an app open a screen from the background at boot. It needs the home app to open it."
                : "This module doesn't open itself at boot. It needs the home app to open it."));
        out.add(new Item("Opening from a remote button", Status.LAUNCHER,
                "Map a button to .Welcome in the launcher's app_keys, once the launcher supports activity targets."));
        out.add(new Item("Renaming the TV", f.nameSettings ? Status.WORKS : Status.SYSTEM, f.nameSettings
                ? "Apps can't set the name themselves; Device Name opens the TV's own setting for it."
                : "Apps can't set it, and this TV has no settings screen for it."));
        out.add(new Item("Boot animation", Status.SYSTEM, (f.bootAnimationFound
                ? "This TV's animation lives on a read-only system partition. "
                : "")
                + "Replacing it needs root or a firmware change; this module never touches it."));
        out.add(new Item("Maker's boot logo", Status.NO,
                "Shown by the bootloader before Android starts. Only the maker's firmware changes it."));
        // Grouped by status, in the order above within each group.
        out.sort((a, b) -> a.status.ordinal() - b.status.ordinal());
        return out;
    }

    static String release(int sdk) {
        switch (sdk) {
            case 26: return "8.0";
            case 27: return "8.1";
            case 28: return "9";
            case 29: return "10";
            case 30: return "11";
            case 31: return "12";
            case 32: return "12L";
            case 33: return "13";
            case 34: return "14";
            case 35: return "15";
            case 36: return "16";
            default: return "API " + sdk;
        }
    }
}
