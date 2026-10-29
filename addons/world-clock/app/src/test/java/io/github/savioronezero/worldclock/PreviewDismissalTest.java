package io.github.savioronezero.worldclock;

import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewDismissalTest {
    @Test public void openingOkReleaseCannotImmediatelyCloseThePreview() {
        assertFalse(new PreviewDismissal().key(23,1,false));
    }
    @Test public void aFreshRemotePressReturnsOnReleaseAndIsConsumed() {
        PreviewDismissal d=new PreviewDismissal();
        assertFalse(d.key(23,0,false));
        assertTrue(d.key(23,1,false));
        assertFalse(d.key(23,1,false));
    }
    @Test public void canceledPressDoesNotDismiss() {
        PreviewDismissal d=new PreviewDismissal();d.key(23,0,false);
        assertFalse(d.key(23,1,true));
        assertFalse(d.key(23,1,false));
    }
    @Test public void aDifferentKeysOrphanReleaseDoesNotDismiss() {
        PreviewDismissal d=new PreviewDismissal();d.key(23,0,false);
        assertFalse(d.key(22,1,false));
    }
    @Test public void heldArrowStillWaitsForItsRelease() {
        PreviewDismissal d=new PreviewDismissal();
        assertFalse(d.key(22,0,false));assertFalse(d.key(22,0,false));
        assertTrue(d.key(22,1,false));
    }
}
