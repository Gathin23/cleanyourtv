package com.gathin.homedashboard;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PersonFocusTest {
    private JSONObject row(String id,String camera,String label,String severity)throws Exception{
        return new JSONObject().put("id",id).put("camera",camera).put("severity",severity).put("end_time",JSONObject.NULL)
            .put("data",new JSONObject().put("objects",new JSONArray().put(label)));
    }
    private void message(PersonFocus f,String type,String id,String camera,String label,String severity)throws Exception{
        f.message(new JSONObject().put("type",type).put("after",row(id,camera,label,severity)).toString());
    }
    @Test public void onlyPersonAlertsQualify()throws Exception{
        PersonFocus f=new PersonFocus();message(f,"new","car","a","car","alert");message(f,"new","person","a","person","detection");
        assertNull(f.choose(List.of("a"),0));message(f,"update","person","a","person","alert");assertEquals("a",f.choose(List.of("a"),0));
    }
    @Test public void oneCameraStaysFocusedUntilAlertEnds()throws Exception{
        PersonFocus f=new PersonFocus();message(f,"new","one","a","person","alert");
        assertEquals("a",f.choose(List.of("a","b"),0));assertEquals("a",f.choose(List.of("a","b"),999999));
        message(f,"end","one","a","person","alert");assertNull(f.choose(List.of("a","b"),1000000));
    }
    @Test public void severalCamerasCycleWithoutRepeatedUpdatesResettingTimer()throws Exception{
        PersonFocus f=new PersonFocus();message(f,"new","one","a","person","alert");message(f,"new","two","b","person","alert");
        assertEquals("a",f.choose(List.of("a","b"),0));message(f,"update","one","a","person","alert");
        assertEquals("a",f.choose(List.of("a","b"),7999));assertEquals("b",f.choose(List.of("a","b"),8000));assertEquals("a",f.choose(List.of("a","b"),16000));
    }
    @Test public void twoAlertsOnOneCameraDoNotDuplicateItsSlot()throws Exception{
        PersonFocus f=new PersonFocus();message(f,"new","one","a","person","alert");message(f,"new","two","a","person","alert");
        message(f,"end","one","a","person","alert");assertEquals("a",f.choose(List.of("a"),0));
        message(f,"end","two","a","person","alert");assertNull(f.choose(List.of("a"),1));
    }
    @Test public void unselectedCamerasDoNotTakeOver()throws Exception{
        PersonFocus f=new PersonFocus();message(f,"new","one","a","person","alert");assertNull(f.choose(List.of("b"),0));
    }
    @Test public void seedOnlyUsesOngoingReviewsAndCannotReviveEndedAlert()throws Exception{
        PersonFocus f=new PersonFocus();message(f,"end","one","a","person","alert");
        f.seed(new JSONArray().put(row("one","a","person","alert")).put(row("two","b","person","alert").put("end_time",12)));
        assertNull(f.choose(List.of("a","b"),0));f.seed(new JSONArray().put(row("three","b","person-verified","alert")));
        assertEquals("b",f.choose(List.of("a","b"),0));
    }
    @Test public void disconnectClearsFocusAndMalformedMessagesAreIgnored()throws Exception{
        PersonFocus f=new PersonFocus();f.message("not json");message(f,"new","one","a","person","alert");f.clear();assertNull(f.choose(List.of("a"),0));
    }
}
