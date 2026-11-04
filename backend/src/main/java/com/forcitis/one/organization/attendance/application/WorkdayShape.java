package com.forcitis.one.organization.attendance.application;

/** What the work calendar makes of one day: not worked (weekend, holiday), an ordinary workday, or a make-up workday. */
enum WorkdayShape {
    REST_DAY,
    WORKDAY,
    MAKE_UP_WORKDAY
}
