package com.forcitis.one.platform.web;

import java.time.Instant;

/** Card #1478: the moment the application is to take as now. */
record LocalClockRequest(Instant at) {
}
