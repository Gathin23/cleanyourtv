package com.oneyuv.tv.nepalicalendar;

import static org.junit.Assert.assertEquals;

import org.json.JSONException;
import org.junit.Test;

public class NepaliCalendarApiTest {
    @Test public void findsTithiByTheBikramSambatDay() throws JSONException {
        String payload = "{\"days\":["
                + "{\"n\":\"११\",\"t\":\"एकादशी\"},"
                + "{\"n\":\"१२\",\"t\":\"चतुर्थी\"}]}";
        assertEquals("चतुर्थी", NepaliCalendarApi.tithi(payload, 12));
    }

    @Test(expected = JSONException.class)
    public void missingDayDoesNotInventATithi() throws JSONException {
        NepaliCalendarApi.tithi("{\"days\":[{\"n\":\"११\",\"t\":\"एकादशी\"}]}", 12);
    }
}
