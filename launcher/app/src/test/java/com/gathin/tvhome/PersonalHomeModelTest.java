package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class PersonalHomeModelTest {
    @Test public void twelveHourTimeHandlesMidnightNoonAndEvening() {
        assertEquals("12:00 AM", PersonalHomeModel.time(0, 0));
        assertEquals("09:05 AM", PersonalHomeModel.time(9, 5));
        assertEquals("12:00 PM", PersonalHomeModel.time(12, 0));
        assertEquals("09:41 PM", PersonalHomeModel.time(21, 41));
        assertEquals("11:59 PM", PersonalHomeModel.time(23, 59));
    }

    @Test public void midnightHasMoonAndNoDaylight() {
        assertEquals(0f, PersonalHomeModel.daylight(0), 0f);
        assertEquals(0f, PersonalHomeModel.daylight(23 * 60), 0f);
    }
    @Test public void dawnAndDuskFadeInsteadOfJumping() {
        assertEquals(0f, PersonalHomeModel.daylight(330), 0f);
        assertEquals(.5f, PersonalHomeModel.daylight(390), .001f);
        assertEquals(1f, PersonalHomeModel.daylight(450), 0f);
        assertEquals(.5f, PersonalHomeModel.daylight(1110), .001f);
        assertEquals(0f, PersonalHomeModel.daylight(1170), 0f);
    }
    @Test public void noonIsDayAndTimesWrap() {
        assertEquals(1f, PersonalHomeModel.daylight(720), 0f);
        assertEquals(PersonalHomeModel.daylight(720), PersonalHomeModel.daylight(2160), 0f);
    }
    @Test public void narrowPhonesDoNotOverflowWithTvColumnPreference() {
        assertEquals(2, PersonalHomeModel.columns(320, 6));
        assertEquals(3, PersonalHomeModel.columns(393, 6));
        assertEquals(6, PersonalHomeModel.columns(960, 6));
        assertEquals(4, PersonalHomeModel.columns(960, 4));
    }
    @Test public void profileNamesTrimAndLimitWholeUnicodeCharacters() {
        assertEquals("", PersonalHomeModel.name(null));
        assertEquals("Sadique", PersonalHomeModel.name("  Sadique  "));
        String emoji = "\uD83D\uDE00";
        String longName = new String(new char[40]).replace("\0", emoji);
        assertEquals(64, PersonalHomeModel.name(longName).length());
    }
}
