package io.github.savioronezero.worldclock;

import org.junit.Test;
import static org.junit.Assert.*;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ClockModelTest {
    private ClockModel.Reading read(String id, String instant, String local) {
        return ClockModel.at(id, Instant.parse(instant), ZoneId.of(local), true, Locale.US);
    }
    @Test public void firstRunUsesChosenCities() {
        assertEquals(Arrays.asList("@local", "Europe/London", "Australia/Sydney"), ClockModel.restore(null));
    }
    @Test public void winterUsesSouthernSummerOffset() {
        assertEquals("12:00", read("Europe/London", "2026-01-15T12:00:00Z", "America/New_York").time);
        ClockModel.Reading s=read("Australia/Sydney", "2026-01-15T12:00:00Z", "America/New_York");
        assertEquals("23:00", s.time); assertEquals("UTC+11:00", s.offset);
    }
    @Test public void summerUsesNorthernSummerOffset() {
        assertEquals("13:00", read("Europe/London", "2026-07-15T12:00:00Z", "America/New_York").time);
        ClockModel.Reading s=read("Australia/Sydney", "2026-07-15T12:00:00Z", "America/New_York");
        assertEquals("22:00", s.time); assertEquals("UTC+10:00", s.offset);
    }
    @Test public void usSpringSkipsTheMissingHour() {
        assertEquals("01:59", read("@local", "2026-03-08T06:59:00Z", "America/New_York").time);
        assertEquals("03:00", read("@local", "2026-03-08T07:00:00Z", "America/New_York").time);
    }
    @Test public void usFallRepeatsHourWithDifferentOffset() {
        ClockModel.Reading before=read("@local", "2026-11-01T05:59:00Z", "America/New_York");
        ClockModel.Reading after=read("@local", "2026-11-01T06:00:00Z", "America/New_York");
        assertEquals("01:59",before.time); assertEquals("UTC-04:00",before.offset);
        assertEquals("01:00",after.time); assertEquals("UTC-05:00",after.offset);
    }
    @Test public void differentDstTransitionDatesAreNotAFixedTimeDifference() {
        assertEquals("08:00",read("@local", "2026-03-15T12:00:00Z", "America/New_York").time);
        assertEquals("12:00",read("Europe/London", "2026-03-15T12:00:00Z", "America/New_York").time);
    }
    @Test public void sydneySpringSkipsItsMissingHour() {
        assertEquals("01:59", read("Australia/Sydney", "2026-10-03T15:59:00Z", "UTC").time);
        assertEquals("03:00", read("Australia/Sydney", "2026-10-03T16:00:00Z", "UTC").time);
    }
    @Test public void nextDayIsRelativeToLocalDate() {
        ClockModel.Reading s=read("Australia/Sydney", "2026-10-23T20:00:00Z", "America/New_York");
        assertEquals(1,s.dayDifference); assertEquals("Sat, 24 Oct",s.date);
    }
    @Test public void previousDayCanBeShown() {
        assertEquals(-1,read("Pacific/Honolulu", "2026-10-23T15:30:00Z", "Asia/Tokyo").dayDifference);
    }
    @Test public void internationalDateLineCanBeTwoCalendarDaysApart() {
        assertEquals(2,read("Pacific/Kiritimati", "2026-10-23T10:30:00Z", "Pacific/Pago_Pago").dayDifference);
    }
    @Test public void fractionalHourOffsetIsPreserved() {
        ClockModel.Reading n=read("Asia/Kathmandu", "2026-10-23T00:00:00Z", "UTC");
        assertEquals("05:45",n.time); assertEquals("UTC+05:45",n.offset);
    }
    @Test public void localFollowsChangedTvZoneWithoutChangingSavedId() {
        assertEquals("00:00",read("@local", "2026-10-23T00:00:00Z", "UTC").time);
        assertEquals("09:00",read("@local", "2026-10-23T00:00:00Z", "Asia/Tokyo").time);
        assertEquals("01:00",read("Europe/London", "2026-10-23T00:00:00Z", "Asia/Tokyo").time);
    }
    @Test public void twelveHourMidnightHasPeriod() {
        ClockModel.Reading m=ClockModel.at("UTC", Instant.parse("2026-10-23T00:00:00Z"), ZoneId.of("UTC"), false, Locale.US);
        assertEquals("12:00",m.time); assertEquals("AM",m.period);
    }
    @Test public void twentyFourHourTimeHasNoPeriod() {
        ClockModel.Reading m=read("UTC", "2026-10-23T00:00:00Z", "UTC");
        assertEquals("00:00",m.time); assertEquals("",m.period); assertEquals("UTC",m.offset);
    }
    @Test public void corruptAndDuplicateSavedIdsAreNotShownAsGmt() {
        assertFalse(ClockModel.valid("Not/A_Zone"));
        assertEquals(Arrays.asList("UTC","Europe/London"), ClockModel.restore(Arrays.asList("Not/A_Zone", null,"UTC","UTC","Europe/London")));
        assertEquals(ClockModel.DEFAULTS,ClockModel.restore(Collections.singletonList("broken")));
    }
    @Test public void restoredOrderSurvivesAndIsBounded() {
        List<String> ids=Arrays.asList("Asia/Tokyo","UTC","Europe/London","Australia/Sydney","America/New_York","Asia/Kathmandu","Europe/Paris");
        assertEquals(ids.subList(0,6),ClockModel.restore(ids));
    }
    @Test public void addingBeyondLimitOrAddingDuplicateDoesNotDiscardAnything() {
        List<String> ids=ClockModel.restore(Arrays.asList("Asia/Tokyo","UTC","Europe/London","Australia/Sydney","America/New_York","Asia/Kathmandu"));
        assertEquals(ids,ClockModel.add(ids,"Europe/Paris"));
        assertEquals(ids,ClockModel.add(ids,"UTC"));
    }
    @Test public void replacementCannotCreateDuplicate() {
        assertEquals(ClockModel.DEFAULTS,ClockModel.replace(ClockModel.DEFAULTS,1,"Australia/Sydney"));
        assertEquals(Arrays.asList("@local","UTC","Australia/Sydney"),ClockModel.replace(ClockModel.DEFAULTS,1,"UTC"));
    }
    @Test public void lastClockCannotBeRemoved() {
        assertEquals(Collections.singletonList("UTC"),ClockModel.remove(Collections.singletonList("UTC"),0));
        assertEquals(Arrays.asList("@local","Australia/Sydney"),ClockModel.remove(ClockModel.DEFAULTS,1));
    }
    @Test public void movingAcrossRowsKeepsEveryClockAndOrder() {
        List<String> ids=Arrays.asList("UTC","Asia/Tokyo","Europe/London","Australia/Sydney");
        assertEquals(Arrays.asList("UTC","Asia/Tokyo","Australia/Sydney","Europe/London"),ClockModel.move(ids,3,2));
        assertEquals(ids,ClockModel.move(ids,0,-1));
    }
    @Test public void searchHandlesSpacesAccentsAndRegion() {
        List<String> all=Arrays.asList("America/New_York","America/Sao_Paulo","Asia/Tokyo");
        assertEquals(Collections.singletonList("America/New_York"),ClockModel.search(all,"newyork",Collections.emptyList(),-1));
        assertEquals(Collections.singletonList("America/Sao_Paulo"),ClockModel.search(all,"São Paulo",Collections.emptyList(),-1));
        assertEquals(Collections.singletonList("Asia/Tokyo"),ClockModel.search(all,"Asia",Collections.emptyList(),-1));
    }
    @Test public void legacyPlatformZoneSpellingStillMatchesTheCityName() {
        assertEquals("Kathmandu", ClockModel.city("Asia/Katmandu"));
        assertEquals(Collections.singletonList("Asia/Katmandu"), ClockModel.search(
                Collections.singletonList("Asia/Katmandu"), "Kathmandu", Collections.emptyList(), -1));
    }
    @Test public void pickerExcludesExistingExceptTheCityBeingReplaced() {
        List<String> all=Arrays.asList("Europe/London","Australia/Sydney","UTC");
        assertEquals(Collections.singletonList("UTC"),ClockModel.search(all,"",ClockModel.DEFAULTS,-1));
        assertEquals(Arrays.asList("Europe/London","UTC"),ClockModel.search(all,"",ClockModel.DEFAULTS,1));
    }
    @Test public void refreshDelayCannotSpinAtMinuteBoundaryOrAfterClockAdjustment() {
        assertEquals(60_000,ClockModel.nextMinute(120_000));
        assertEquals(1,ClockModel.nextMinute(119_999));
        assertEquals(59_000,ClockModel.nextMinute(121_000));
        assertEquals(1,ClockModel.nextMinute(-1));
    }
}
