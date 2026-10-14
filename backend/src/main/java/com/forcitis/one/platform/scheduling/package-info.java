/**
 * platform.scheduling — 权限流程与集成平台 BC (平台运维): runs @Scheduled jobs with ShedLock (JDBC, ADR-0002).
 * Schema platform_scheduling. Modules declare their own jobs with @Scheduled + @SchedulerLock(name = "{subdomain}.{bc}.{name}")
 * and never depend on this module (G46).
 */
package com.forcitis.one.platform.scheduling;
