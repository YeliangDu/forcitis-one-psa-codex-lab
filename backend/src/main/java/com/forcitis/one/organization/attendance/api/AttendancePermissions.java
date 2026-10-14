package com.forcitis.one.organization.attendance.api;

/** Copies of platform.identity Permission.Codes (attendance may not depend on identity); NamingSurfaceTest G43 checks them. */
public interface AttendancePermissions {
    String WORK_CALENDAR_READ = "organization.attendance:work-calendar:read";
    String WORK_CALENDAR_UPDATE = "organization.attendance:work-calendar:update";
    String WORK_CALENDAR_UPDATE_LOCKED_DAYS = "organization.attendance:locked-day:update";
}
