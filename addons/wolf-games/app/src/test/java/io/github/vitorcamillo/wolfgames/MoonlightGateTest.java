package io.github.vitorcamillo.wolfgames;

import android.content.pm.PackageManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MoonlightGateTest {
    @Test public void targetStateFollowsMoonlightPresence() {
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,MoonlightGate.targetState(true));
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED,MoonlightGate.targetState(false));
    }
}
