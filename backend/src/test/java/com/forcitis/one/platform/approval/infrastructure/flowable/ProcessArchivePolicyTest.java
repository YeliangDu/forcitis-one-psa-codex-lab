package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.BusinessDay;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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

    @Test
    @DisplayName("T3 the engine history stays online 3 years; a missing or non-positive period stops the start-up")
    void onlinePeriodIsRequired() {
        runner.withPropertyValues("forcitis.platform.approval.process-archive.online-period=P3Y").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProcessArchivePolicy.class).onlinePeriod().getYears()).isEqualTo(3);
        });
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("forcitis.platform.approval.process-archive.online-period=P0D").run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("T3 the variables an archive may carry are the engine's own constants and exactly the ones registered in architecture/archive-variables.txt (data classification)")
    void archivedVariablesAreRegistered() throws Exception {
        List<String> registered = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("architecture/archive-variables.txt"))) {
            if (!line.isBlank() && !line.startsWith("#")) {
                String[] parts = line.split("\\s*\\|\\s*");
                assertThat(parts).as(line).hasSize(4);
                registered.add(parts[0] + ":" + parts[1]);
            }
        }
        List<String> inCode = new ArrayList<>();
        ProcessArchiveJob.PROCESS_VARIABLES.forEach(name -> inCode.add("PROCESS:" + name));
        inCode.add("PROCESS:" + ProcessArchiveJob.ROUTING_VARIABLE_PREFIX + "*");
        ProcessArchiveJob.TASK_VARIABLES.forEach(name -> inCode.add("TASK:" + name));
        assertThat(ProcessArchiveJob.PROCESS_VARIABLES).as("process variables are the engine's constants").containsExactly(FlowableApprovalEngine.VARIABLE_CONTENT_HASH,
                FlowableApprovalEngine.VARIABLE_LEGAL_ENTITY);
        assertThat(ProcessArchiveJob.ROUTING_VARIABLE_PREFIX).as("the routing prefix is the engine's").isEqualTo(FlowableApprovalEngine.VARIABLE_ROUTED_PREFIX);
        assertThat(ProcessArchiveJob.TASK_VARIABLES).as("task variables are the engine's constants").containsExactly(FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE,
                FlowableApprovalEngine.VARIABLE_ADVISORY, FlowableApprovalEngine.VARIABLE_ASSIGNED_AT);
        assertThat(new TreeSet<>(inCode)).isEqualTo(new TreeSet<>(registered));
    }

    @Test
    @DisplayName("T3 the job refuses to start when the vault says online period plus WORM days is not the total keep period")
    void jobChecksTheTotalKeepPeriod() {
        var vault = new com.forcitis.one.fixture.doubles.RecordingArchiveVault();
        vault.retentionFailure = new IllegalStateException("not the total keep period");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new ProcessArchiveJob(org.mockito.Mockito.mock(org.springframework.jdbc.core.simple.JdbcClient.class), com.forcitis.one.TestApprovalIds.generator(),
                org.mockito.Mockito.mock(org.flowable.engine.HistoryService.class), vault, BusinessDay.fixedClock(),
                new com.forcitis.one.kernel.BusinessZone(java.time.ZoneId.of("Asia/Shanghai")), new ProcessArchivePolicy(java.time.Period.ofYears(3)),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry())).hasMessageContaining("total keep period");
    }
}
