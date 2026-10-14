package com.forcitis.one.platform.integration.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchiveKeepPeriodTest {
    private static final Path CONFIG = Path.of("src/main/resources/config");

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(CONFIG.resolve(file))));
        return properties;
    }

    @Test
    @DisplayName("data scorecard T3: for process history and audit alike, online period plus WORM days is the one total keep period of 10 years, in the shipped configuration")
    void shippedConfigurationAddsUpToTenYears() throws IOException {
        Properties integration = load("platform_integration.properties");
        int total = Integer.parseInt(integration.getProperty("forcitis.platform.integration.archive-store.total-keep-years"));
        Matcher worm = Pattern.compile("\\$\\{[A-Z_]+:(\\d+)}").matcher(integration.getProperty("forcitis.platform.integration.archive-store.worm-lock-days"));
        assertThat(worm.matches()).as("worm-lock-days has a shipped default").isTrue();
        int lockDays = Integer.parseInt(worm.group(1));
        Period processOnline = Period.parse(load("platform_approval.properties").getProperty("forcitis.platform.approval.process-archive.online-period"));
        Period auditOnline = Period.parse(load("platform_audit.properties").getProperty("forcitis.platform.audit.archive.online-period"));

        assertThat(total).isEqualTo(10);
        assertThat(lockDays).isEqualTo(2557);
        KeepPeriodRule.require("process history", processOnline, lockDays, total);
        KeepPeriodRule.require("audit", auditOnline, lockDays, total);
    }

    @Test
    @DisplayName("changing the online period, the WORM days or the total alone fails, whatever job it is")
    void changingOneOfThreeFails() {
        KeepPeriodRule.require("x", Period.ofYears(3), 2557, 10);
        KeepPeriodRule.require("x", Period.ofYears(3), 2556, 10);

        assertThatThrownBy(() -> KeepPeriodRule.require("x", Period.ofYears(2), 2557, 10)).isInstanceOf(IllegalStateException.class).hasMessageContaining("change them together");
        assertThatThrownBy(() -> KeepPeriodRule.require("x", Period.ofYears(3), 3650, 10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> KeepPeriodRule.require("x", Period.ofYears(3), 2557, 12)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> KeepPeriodRule.require("x", Period.ofYears(3), 2400, 10)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> KeepPeriodRule.require("x", Period.ofMonths(30), 2557, 10)).isInstanceOf(IllegalStateException.class).hasMessageContaining("whole years");
    }
}
