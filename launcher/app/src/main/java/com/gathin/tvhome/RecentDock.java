package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * dock_order: "fixed" (the default) keeps dock_apps as written; "recent" fills the dock with the apps Home opened
 * last (LastOpened), newest first, and lets dock_apps fill the slots no recent app takes, in their own order. Only
 * launches from Home count, as for the cinematic shelf's "Last opened" line, so an app a remote button starts
 * keeps its place until Home opens it.
 */
final class RecentDock {
    static final String FIXED = "fixed";
    static final String RECENT = "recent";

    private RecentDock() {
    }

    /**
     * The dock's packages, at most {@code slots}: installed apps Home opened, most recent first (a tie keeps the
     * dock_apps order, then the name), then the installed dock_apps entries not already in it.
     */
    static List<String> order(List<String> dockApps, Map<String, Long> lastOpened, Collection<String> installed,
                              int slots) {
        if (slots <= 0) return Collections.emptyList();
        List<String> recent = new ArrayList<>();
        for (Map.Entry<String, Long> e : lastOpened.entrySet()) {
            Long when = e.getValue();
            if (when != null && when > 0 && installed.contains(e.getKey())) recent.add(e.getKey());
        }
        recent.sort((a, b) -> {
            int byTime = Long.compare(lastOpened.get(b), lastOpened.get(a));
            if (byTime != 0) return byTime;
            int ia = dockApps.indexOf(a), ib = dockApps.indexOf(b);
            if (ia != ib) return ia < 0 ? 1 : ib < 0 ? -1 : Integer.compare(ia, ib);
            return a.compareTo(b);
        });
        List<String> dock = new ArrayList<>();
        for (String pkg : recent) {
            if (dock.size() == slots) return dock;
            dock.add(pkg);
        }
        for (String pkg : dockApps) {
            if (dock.size() == slots) break;
            if (installed.contains(pkg) && !dock.contains(pkg)) dock.add(pkg);
        }
        return dock;
    }
}
