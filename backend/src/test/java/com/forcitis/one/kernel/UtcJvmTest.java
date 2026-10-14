package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UtcJvmTest {
    @Test
    @DisplayName("a JVM in UTC, however the zone is spelled, may start")
    void utcPasses() {
        for (String utc : new String[] {"UTC", "Etc/UTC", "Z", "GMT", "Etc/GMT"}) {
            assertThatCode(() -> UtcJvm.require(ZoneId.of(utc, ZoneId.SHORT_IDS))).as(utc).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("any other zone refuses to start, the business zone and zones with daylight saving included, and the message says how to fix it")
    void otherZonesAreRefused() {
        for (String zone : new String[] {"Asia/Shanghai", "Europe/Berlin", "America/New_York", "Asia/Kolkata"}) {
            assertThatThrownBy(() -> UtcJvm.require(ZoneId.of(zone))).as(zone).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(zone).hasMessageContaining("-Duser.timezone=UTC");
        }
    }

    @Test
    @DisplayName("the test JVMs themselves run in UTC, as production does")
    void testJvmIsUtc() {
        assertThatCode(() -> UtcJvm.require(ZoneId.systemDefault())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("INT-P1 a machine in Asia/Shanghai (INFRA_RUNBOOK E-2) still starts: the entry point installs UTC before anything reads the zone")
    void entryPointInstallsUtc() throws Exception {
        TimeZone before = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            UtcJvm.installAsDefault();
            assertThat(ZoneId.systemDefault().getRules().getOffset(java.time.Instant.EPOCH)).isEqualTo(ZoneOffset.UTC);
            assertThatCode(() -> UtcJvm.require(ZoneId.systemDefault())).doesNotThrowAnyException();
        } finally {
            TimeZone.setDefault(before);
        }
        String main = Files.readString(Path.of("src/main/java/com/forcitis/one/OneApplication.java"));
        assertThat(main.indexOf("UtcJvm.installAsDefault()")).as("installed before the context starts").isPositive()
                .isLessThan(main.indexOf("SpringApplication.run"));
    }
}
