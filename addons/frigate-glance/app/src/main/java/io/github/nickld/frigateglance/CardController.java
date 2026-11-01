package io.github.nickld.frigateglance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * When a card shows, changes and leaves. One card at a time: a new matching review replaces it. It leaves
 * dismissSeconds after its objects have all gone (ended, or parked when dismissOnStationary), as Peek does:
 * Frigate ends the review itself only after its cutoff, some 30 to 40 s later. An object that comes back
 * while the card counts down stops the countdown. The review's "end" starts it too, for lost event
 * messages, and the card leaves at the latest 120 s after its last news, so a lost "end" can never leave it
 * on screen. Not thread-safe: the service calls it from one thread.
 */
public final class CardController {
    static final long HARD_LIMIT_MS = 120_000;
    /** A replay's camera can vanish without an "end"; its card leaves this long after its last message. */
    static final long REPLAY_QUIET_MS = 15_000;
    static final String TEST_ID = "test";
    private static final long TEST_EXTRA_MS = 10_000;

    private final CardView view;
    private final ReviewFilter filter;
    private final Map<String, String> displayNames;
    private final int dismissSeconds;
    private final boolean dismissOnStationary;
    private final LongSupplier clock;

    private Review shown;
    private final Map<String, Detection> detections = new LinkedHashMap<>();
    private long deadline;
    /** True once the shown card is counting down to leaving (its objects left, its review ended, or a test card). */
    private boolean ending;
    /** Frigate ended the shown review (or it is a test card): no object can stop the countdown any more. */
    private boolean reviewEnded;
    /** The shown review's objects Frigate stopped tracking. */
    private final Set<String> endedIds = new java.util.HashSet<>();
    private long snoozedUntil;
    /** Reviews that had a card which then left (dismissed, ended, replaced): their updates never reopen one. */
    private final java.util.Set<String> closed = java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
                    return size() > 64;
                }
            });

    public CardController(CardView view, ReviewFilter filter, Map<String, String> displayNames, int dismissSeconds,
            boolean dismissOnStationary, LongSupplier clock) {
        this.view = view;
        this.filter = filter;
        this.displayNames = displayNames;
        this.dismissSeconds = dismissSeconds;
        this.dismissOnStationary = dismissOnStationary;
        this.clock = clock;
    }

    public void onReview(Review r) {
        long now = clock.getAsLong();
        boolean isShown = shown != null && shown.id.equals(r.id);
        // An update opens a card too when nothing is up and this review never had one: the add-on connected
        // mid-event (a reconnect, a reinstall, a Frigate debug replay already running).
        boolean late = "update".equals(r.type) && !isShown && shown == null && !closed.contains(r.id);
        if ("new".equals(r.type) || late) {
            if (!filter.shows(r, now, snoozedUntil)) return;
            if (shown != null) closed.add(shown.id);
            shown = r;
            detections.clear();
            endedIds.clear();
            deadline = now + quietLimit(r);
            ending = false;
            reviewEnded = false;
            settle(now);
            view.show(model());
        } else if (isShown && "update".equals(r.type)) {
            shown = r;
            if (!ending) deadline = now + quietLimit(r);
            settle(now);
            view.update(model());
        } else if (isShown && "end".equals(r.type)) {
            shown = r;
            if (!ending) deadline = now + dismissSeconds * 1000L;
            ending = true;
            reviewEnded = true;
            view.update(model());
        }
    }

    /** Starts the countdown when the review's objects have all gone, and stops it when one is back. */
    private void settle(long now) {
        if (reviewEnded) return;
        boolean gone = objectsGone();
        if (gone && !ending) {
            ending = true;
            deadline = now + dismissSeconds * 1000L;
        } else if (!gone && ending) {
            ending = false;
            deadline = now + quietLimit(shown);
        }
    }

    /**
     * Every object the review lists has ended or parked, and at least one was seen. One listed but not yet
     * heard of counts as present: its event may come after the review update that names it.
     */
    private boolean objectsGone() {
        if (shown.detectionIds.isEmpty()) return false;
        for (String id : shown.detectionIds) {
            if (endedIds.contains(id)) continue;
            Detection d = detections.get(id);
            if (d == null || !(dismissOnStationary && d.stationary)) return false;
        }
        return true;
    }

    private static long quietLimit(Review r) {
        return r.camera.startsWith(ReviewFilter.REPLAY_PREFIX) ? REPLAY_QUIET_MS : HARD_LIMIT_MS;
    }

    public void onDetection(Detection d) {
        if (shown == null || !shown.detectionIds.contains(d.id)) return;
        if (d.ended) {
            detections.remove(d.id);
            endedIds.add(d.id);
        } else {
            detections.put(d.id, d);
        }
        settle(clock.getAsLong());
        view.update(model());
    }

    public void onAvailable(boolean online) {
        if (!online) hideNow();
    }

    /** A card that does not come from Frigate, to check placement and the buttons. */
    public void showTest(String camera) {
        String after = "{\"id\":\"" + TEST_ID + "\",\"camera\":\"" + camera + "\",\"severity\":\"alert\",\"data\":{}}";
        shown = Review.parse("{\"type\":\"new\",\"after\":" + after + "}");
        detections.clear();
        endedIds.clear();
        deadline = clock.getAsLong() + dismissSeconds * 1000L + TEST_EXTRA_MS;
        ending = true;
        reviewEnded = true;
        view.show(model());
    }

    public void dismiss() {
        hideNow();
    }

    public void snooze(int minutes) {
        snoozedUntil = clock.getAsLong() + minutes * 60_000L;
        hideNow();
    }

    public void resume() {
        snoozedUntil = 0;
    }

    public long snoozedUntil() {
        return snoozedUntil > clock.getAsLong() ? snoozedUntil : 0;
    }

    /** The camera of the card on screen, or null. */
    public String shownCamera() {
        return shown == null ? null : shown.camera;
    }

    public boolean isShowing() {
        return shown != null;
    }

    /** Called once a second. */
    public void tick() {
        if (shown != null && clock.getAsLong() >= deadline) hideNow();
    }

    private void hideNow() {
        if (shown == null) return;
        closed.add(shown.id);
        shown = null;
        detections.clear();
        view.hide();
    }

    private CardModel model() {
        List<float[]> boxes = new ArrayList<>();
        for (Detection d : detections.values()) if (d.box != null) boxes.add(d.box);
        long countdown = ending ? Math.max(0, deadline - clock.getAsLong()) : 0;
        return new CardModel(shown.id, shown.camera, displayName(shown.camera), detail(), boxes, countdown);
    }

    private String detail() {
        if (TEST_ID.equals(shown.id)) return "Test card";
        Set<String> parts = new LinkedHashSet<>();
        for (String o : shown.objects) parts.add(pretty(o));
        parts.addAll(shown.subLabels);
        for (Detection d : detections.values()) {
            if (d.subLabel != null) parts.add(d.subLabel);
            if (d.plate != null) parts.add(d.plate);
        }
        for (String z : shown.zones) parts.add(pretty(z));
        return String.join(" · ", parts);
    }

    private String displayName(String camera) {
        String base = ReviewFilter.baseCamera(camera);
        if (!base.equals(camera)) return displayName(base) + " (replay)";
        String n = displayNames.get(camera);
        return n != null && !n.isEmpty() ? n : pretty(camera);
    }

    /** Peek's prettyName: separators become spaces, each word capitalised. */
    static String pretty(String name) {
        StringBuilder out = new StringBuilder();
        for (String w : name.split("[_\\-\\s]+")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return out.toString();
    }
}
