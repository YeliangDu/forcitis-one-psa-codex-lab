package com.forcitis.one.platform.numbering.api;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public enum ResetPolicy {
    NEVER, YEARLY, MONTHLY, DAILY;

    public String periodKey(LocalDate date) {
        return switch (this) {
            case NEVER -> "-";
            case YEARLY -> String.valueOf(date.getYear());
            case MONTHLY -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case DAILY -> date.format(DateTimeFormatter.BASIC_ISO_DATE);
        };
    }
}
