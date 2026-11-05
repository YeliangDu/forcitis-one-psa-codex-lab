package com.forcitis.one.platform.web;

import java.time.Instant;

/** Card #1478: the moment the application takes as now, and whether it was moved off the real time. */
record LocalClockResponse(Instant instant, boolean adjusted) {
}
