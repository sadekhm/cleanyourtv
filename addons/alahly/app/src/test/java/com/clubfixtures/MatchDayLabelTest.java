package com.clubfixtures;

import org.junit.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import static org.junit.Assert.assertEquals;

public class MatchDayLabelTest {
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(CAIRO).toInstant().toEpochMilli();
    }

    @Test public void tomorrowEvenWhenLessThanTwentyFourHoursAway() {
        assertEquals("TOMORROW", MatchDayLabel.day(at(2026, 10, 24, 19, 0), at(2026, 10, 25, 18, 0)));
    }

    @Test public void todayAcrossSeveralHours() {
        assertEquals("TODAY", MatchDayLabel.day(at(2026, 10, 24, 9, 0), at(2026, 10, 24, 21, 0)));
    }

    @Test public void twoCalendarDaysAcrossDaylightSavingChange() {
        assertEquals("IN 2 DAYS", MatchDayLabel.day(at(2026, 4, 23, 23, 0), at(2026, 4, 25, 1, 0)));
    }

    @Test public void pastKickoffNeverSaysToday() {
        assertEquals("AWAITING MATCH UPDATE", MatchDayLabel.day(at(2026, 10, 24, 20, 0), at(2026, 10, 24, 19, 0)));
    }
}
