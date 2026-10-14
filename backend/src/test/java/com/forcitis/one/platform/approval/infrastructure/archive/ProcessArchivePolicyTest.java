package com.forcitis.one.platform.approval.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ProcessArchivePolicyTest {
    @Configuration
    @EnableConfigurationProperties(ProcessArchivePolicy.class)
    static class Enabled {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Enabled.class);

    private static final String[] COMPLETE = {"forcitis.platform.approval.process-archive.online-period=P3Y",
            "forcitis.platform.approval.process-archive.archive-period=P10Y", "forcitis.platform.approval.process-archive.directory=/data/forcitis-one/archive"};

    @Test
    @DisplayName("T3 the engine history policy is complete: 3 years online, 10 years archived, an archive directory")
    void completeConfigurationStarts() {
        runner.withPropertyValues(COMPLETE).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProcessArchivePolicy.class).onlinePeriod().getYears()).isEqualTo(3);
        });
    }

    @Test
    @DisplayName("T3 a missing period or archive directory stops the start-up instead of falling back to a default")
    void missingValuesFailTheStartup() {
        for (int missing = 0; missing < COMPLETE.length; missing++) {
            String[] partial = new String[COMPLETE.length - 1];
            int at = 0;
            for (int i = 0; i < COMPLETE.length; i++) {
                if (i != missing) {
                    partial[at++] = COMPLETE[i];
                }
            }
            runner.withPropertyValues(partial).run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("forcitis.platform.approval.process-archive.online-period=P3Y", "forcitis.platform.approval.process-archive.archive-period=P10Y",
                "forcitis.platform.approval.process-archive.directory=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("T3 the archive must be kept longer than the history stays online, and in an absolute directory")
    void inconsistentValuesFail() {
        runner.withPropertyValues("forcitis.platform.approval.process-archive.online-period=P3Y", "forcitis.platform.approval.process-archive.archive-period=P3Y",
                "forcitis.platform.approval.process-archive.directory=/data/a").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("forcitis.platform.approval.process-archive.online-period=P3Y", "forcitis.platform.approval.process-archive.archive-period=P10Y",
                "forcitis.platform.approval.process-archive.directory=relative/dir").run(context -> assertThat(context).hasFailed());
    }
}
