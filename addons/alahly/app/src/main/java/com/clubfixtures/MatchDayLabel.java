package com.clubfixtures;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Calendar-day wording in Cairo, independent of the TV's configured timezone. */
public final class MatchDayLabel {
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private MatchDayLabel() {}

    public static String day(long now, long kickoff) {
        if (kickoff < now) return "AWAITING MATCH UPDATE";
        LocalDate today = Instant.ofEpochMilli(now).atZone(CAIRO).toLocalDate();
        LocalDate matchDay = Instant.ofEpochMilli(kickoff).atZone(CAIRO).toLocalDate();
        long days = ChronoUnit.DAYS.between(today, matchDay);
        if (days == 0) return "TODAY";
        if (days == 1) return "TOMORROW";
        return "IN " + days + " DAYS";
    }
}
