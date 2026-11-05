package com.forcitis.one.organization.attendance.web;

/** Copies of platform.identity Permission.Codes (attendance may not depend on identity); NamingSurfaceTest G43 checks them. */
final class AttendancePermissions {
    static final String WORK_CALENDAR_READ = "organization.attendance:work-calendar:read";
    static final String WORK_CALENDAR_UPDATE = "organization.attendance:work-calendar:update";
    static final String LOCKED_DAY_UPDATE = "organization.attendance:locked-day:update";

    static final String STANDARD_HOURS_PUBLISH = "organization.attendance:standard-hours:publish";
    static final String STANDARD_HOURS_READ = "organization.attendance:standard-hours:read";

    private AttendancePermissions() {
    }
}
