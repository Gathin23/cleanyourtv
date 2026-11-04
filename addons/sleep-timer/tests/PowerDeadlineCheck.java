package io.github.luyzfernando08.sleeptimer;

/** Run without Android or Shizuku: java -cp work/power-deadline-check ...PowerDeadlineCheck */
public final class PowerDeadlineCheck {
    private static int checks;

    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        long issued = 1_000_000L;
        long deadline = issued + PowerDeadline.TIMEOUT_MS;
        check(PowerDeadline.active(deadline, issued), "valid request at start");
        check(PowerDeadline.active(deadline, deadline - 1), "valid one millisecond before expiry");
        check(!PowerDeadline.active(deadline, deadline), "reject on deadline");
        check(!PowerDeadline.active(deadline, deadline + 1), "reject late delivery");
        check(!PowerDeadline.active(deadline, deadline + 60_000), "reject stale queued delivery");
        check(!PowerDeadline.active(issued + PowerDeadline.TIMEOUT_MS + 1, issued),
                "reject requests exceeding the 9s bound");
        check(!PowerDeadline.active(0, issued), "reject missing deadline");
        check(!PowerDeadline.active(Long.MAX_VALUE, 0), "reject unbounded deadline");
        check(!PowerDeadline.active(Long.MIN_VALUE, 1), "reject negative overflow");
        check(!PowerDeadline.active(Long.MAX_VALUE, Long.MIN_VALUE),
                "reject wrapped subtraction");
        check(PowerDeadline.active(deadline, issued + 500), "valid during preparation");
        check(!PowerDeadline.active(deadline, issued + 9_100),
                "reject if settings reads consume the remaining deadline");
        System.out.println("Power deadline checks passed: " + checks);
    }
}
