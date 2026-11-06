package io.github.vitorcamillo.wolfgames;

import org.junit.Test;
import static org.junit.Assert.*;

/** The error codes that reach logcat and the dialogs stay in the safe set: nothing raw is echoed back. */
public class WolfSessionMenuCodesTest {
    @Test public void knownCodesPassThroughUnchanged() {
        for(String code:new String[]{"network_error","tls_error","unauthorized","config_error","protocol_error",
                "session_changed","busy","stop_timeout","upstream_unavailable","invalid_request"}) {
            assertEquals(code,WolfSessionMenu.safeCode(code));
        }
    }
    @Test public void anythingElseBecomesProtocolError() {
        for(String code:new String[]{"","unknown","internal detail","c:/users/secrets"}) {
            assertEquals("protocol_error",WolfSessionMenu.safeCode(code));
        }
    }
}
