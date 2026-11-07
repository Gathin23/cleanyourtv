package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ServiceRepairTest {
    private static final String INPUT = "com.gathin.tvhome.InputKeyService";
    private static final String HOME = "com.gathin.tvhome.HomeKeyService";
    private static final List<String> NONE = Collections.emptyList();

    @Test
    public void listedRecordedHelperThatIsNotBoundNeedsTheCycle() {
        // Hisense songshan standby: the helper stays listed, sits under "Crashed services", nothing is bound.
        assertTrue(ServiceRepair.listedButUnbound(Collections.singletonList(INPUT),
                Collections.singletonList(INPUT), NONE));
    }

    @Test
    public void boundHelperIsLeftAlone() {
        assertFalse(ServiceRepair.listedButUnbound(Collections.singletonList(INPUT),
                Collections.singletonList(INPUT), Collections.singletonList(INPUT)));
    }

    @Test
    public void helperMissingFromTheListIsNotThisCase() {
        // Off the list is the force-stop path, which restore() already handles on its own.
        assertFalse(ServiceRepair.listedButUnbound(NONE, Collections.singletonList(INPUT), NONE));
    }

    @Test
    public void unrecordedHelperIsNeverRepaired() {
        // A helper the user turned off drops its record; a listed one without a record is not ours to cycle.
        assertFalse(ServiceRepair.listedButUnbound(Collections.singletonList(HOME), NONE, NONE));
    }

    @Test
    public void oneUnboundHelperOfTwoIsEnough() {
        List<String> both = Arrays.asList(INPUT, HOME);
        assertTrue(ServiceRepair.listedButUnbound(both, both, Collections.singletonList(HOME)));
        assertFalse(ServiceRepair.listedButUnbound(both, both, both));
    }
}
