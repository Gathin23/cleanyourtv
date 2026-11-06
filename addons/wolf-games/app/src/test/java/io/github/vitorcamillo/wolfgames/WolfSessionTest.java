package io.github.vitorcamillo.wolfgames;

import org.junit.Test;
import java.util.Collections;
import java.util.Random;
import static org.junit.Assert.*;

public class WolfSessionTest {
    private static final String REV=String.join("",Collections.nCopies(64,"a"));
    @Test public void stateDrivesLaunchResumeOrConfirmation() {
        WolfSession active=new WolfSession(true,"10","Mario Kart",REV);
        assertEquals(WolfSession.Decision.LAUNCH,WolfSession.inactive().forTarget("10"));
        assertEquals(WolfSession.Decision.RESUME_MENU,active.forTarget("10"));
        assertEquals(WolfSession.Decision.CONFIRM_SWITCH,active.forTarget("20"));
        assertTrue(active.active);assertEquals("Mario Kart",active.appName);assertEquals(REV,active.revision);
        assertFalse(WolfSession.inactive().active);assertNull(WolfSession.inactive().appId);
    }
    @Test public void everyDifferentGameRequiresConfirmation() {
        Random random=new Random(7241);
        for(int i=0;i<1000;i++) {
            String source=String.valueOf(1+random.nextInt(Integer.MAX_VALUE-1));
            String target=String.valueOf(1+random.nextInt(Integer.MAX_VALUE-1));
            WolfSession session=new WolfSession(true,source,"Game",REV);
            assertEquals(source.equals(target)?WolfSession.Decision.RESUME_MENU:WolfSession.Decision.CONFIRM_SWITCH,session.forTarget(target));
        }
    }
    @Test public void idsAreStrictAndBounded() {
        for(String id:new String[]{null,"","0","-1","01","2147483648","99999999999","1;cmd","1.0"," 1"}) {
            assertThrows(IllegalArgumentException.class,()->new WolfSession(true,id,"Game",REV));
            assertThrows(IllegalArgumentException.class,()->WolfSession.inactive().forTarget(id));
        }
        assertEquals("2147483647",new WolfSession(true,"2147483647","Game",REV).appId);
        assertEquals(WolfSession.Decision.LAUNCH,WolfSession.inactive().forTarget("1"));
    }
    @Test public void malformedRevisionAndTitlesAreRejected() {
        for(String rev:new String[]{null,"",REV.substring(1),REV+"a",REV.toUpperCase(),"x"+REV.substring(1)})
            assertThrows(IllegalArgumentException.class,()->new WolfSession(true,"1","Game",rev));
        for(String name:new String[]{null,""," Game","Game ","x\ny","x\u0000y",String.join("",Collections.nCopies(121,"x"))})
            assertThrows(IllegalArgumentException.class,()->new WolfSession(true,"1",name,REV));
        assertEquals(120,new WolfSession(true,"1",String.join("",Collections.nCopies(120,"x")),REV).appName.length());
    }
    @Test public void inactiveCannotPretendToContainAnApp() {
        assertThrows(IllegalArgumentException.class,()->new WolfSession(false,"1",null,null));
        assertThrows(IllegalArgumentException.class,()->new WolfSession(false,null,"Game",null));
        assertThrows(IllegalArgumentException.class,()->new WolfSession(false,null,null,REV));
    }
}
