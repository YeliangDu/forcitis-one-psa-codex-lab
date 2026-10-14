package com.forcitis.one.kernel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

/**
 * The application runs in a JVM whose default zone is UTC. Nothing here reads the default zone on purpose, but a library that does (a JDBC
 * driver's session zone, a log timestamp, java.util.Date) would then differ from machine to machine, so a node started without
 * -Duser.timezone=UTC refuses to start rather than run with the wrong zone. The entry point sets UTC itself before anything else runs, so a
 * machine whose zone is Asia/Shanghai (INFRA_RUNBOOK E-2) still starts; the check stays for any other way the context is created.
 */
public final class UtcJvm {
    private UtcJvm() {
    }

    public static void installAsDefault() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    static void require(ZoneId defaultZone) {
        var rules = defaultZone.getRules();
        if (!rules.isFixedOffset() || !rules.getOffset(Instant.EPOCH).equals(ZoneOffset.UTC)) {
            throw new IllegalStateException("the JVM default time zone is " + defaultZone + ", not UTC: start the JVM with -Duser.timezone=UTC (INFRA_RUNBOOK E-6)");
        }
    }
}
