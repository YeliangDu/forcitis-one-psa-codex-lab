package com.forcitis.one.cost.timesheet.web;

import java.math.BigDecimal;

record SubmitTimesheetRequest(long workPackageId, BigDecimal hours, long projectManagerId) {
}
