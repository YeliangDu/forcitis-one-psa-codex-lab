package com.forcitis.one.organization.attendance.api;

/** What a calendar date is besides the weekday rule: nothing, a holiday or a make-up workday. */
public enum DayException {
    NONE,
    HOLIDAY,
    MAKE_UP_WORKDAY
}
