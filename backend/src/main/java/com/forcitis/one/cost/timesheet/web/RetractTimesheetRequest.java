package com.forcitis.one.cost.timesheet.web;

/** The reason is required; carries no caller identity (G33). */
record RetractTimesheetRequest(String reason) {
}
