package com.clubfixtures;

/** Pure timing rules for one on-demand fixtures request. All values are epoch milliseconds. */
public final class RefreshPolicy {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private RefreshPolicy() {}

    public static long nextDue(long now, long lastAttempt, long lastSuccess,
                               long kickoff, String status) {
        if (lastAttempt == 0) return now;
        long normal;
        long untilKickoff = kickoff == Long.MAX_VALUE ? Long.MAX_VALUE : kickoff - now;
        if (isFinal(status)) {
            normal = lastAttempt + DAY;
        } else if (kickoff == Long.MAX_VALUE) {
            normal = lastAttempt + DAY;
        } else if (untilKickoff <= 0 && untilKickoff >= -3 * HOUR) {
            normal = lastAttempt + 20 * MINUTE;
        } else if (untilKickoff < -3 * HOUR && untilKickoff >= -12 * HOUR
                && !isFinal(status)) {
            normal = lastAttempt + 6 * HOUR;
        } else if (untilKickoff > 0 && untilKickoff <= 24 * HOUR) {
            long beforeKickoff = kickoff - 90 * MINUTE;
            normal = lastSuccess < beforeKickoff
                    ? Math.min(lastAttempt + DAY, beforeKickoff)
                    : kickoff;
        } else if (untilKickoff > 24 * HOUR && untilKickoff <= 7 * DAY) {
            normal = lastAttempt + DAY;
        } else if (untilKickoff > 7 * DAY) {
            normal = lastAttempt + 72 * HOUR;
        } else {
            normal = lastAttempt + DAY;
        }
        if (lastAttempt > lastSuccess) {
            long retry = lastSuccess == 0 ? 2 * HOUR : 6 * HOUR;
            normal = normal <= lastAttempt ? lastAttempt + retry : Math.min(normal, lastAttempt + retry);
        }
        return normal;
    }

    private static boolean isFinal(String status) {
        return "FT".equals(status) || "AET".equals(status) || "PEN".equals(status)
                || "AWD".equals(status) || "WO".equals(status) || "PST".equals(status)
                || "CANC".equals(status);
    }
}
