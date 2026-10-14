package com.forcitis.one.platform.integration.infrastructure.archive;

import java.time.Period;

/** Time online plus the WORM lock must be the total keep period; a year is 365.25 days, so 7 years are 2557 days. */
final class KeepPeriodRule {
    private KeepPeriodRule() {
    }

    static void require(String what, Period online, int wormLockDays, int totalKeepYears) {
        if (online.getDays() != 0 || online.toTotalMonths() % 12 != 0) {
            throw new IllegalStateException(what + ": the online period " + online + " must be whole years");
        }
        long onlineYears = online.toTotalMonths() / 12;
        long lockYears = Math.round(wormLockDays / 365.25);
        if (onlineYears + lockYears != totalKeepYears || Math.abs(wormLockDays - Math.round(lockYears * 365.25)) > 1) {
            throw new IllegalStateException(what + ": online " + online + " plus WORM lock " + wormLockDays + " days is not the total keep period of "
                    + totalKeepYears + " years; change them together");
        }
    }
}
