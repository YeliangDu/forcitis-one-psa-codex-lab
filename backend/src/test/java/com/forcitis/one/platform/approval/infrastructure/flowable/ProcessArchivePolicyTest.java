package com.forcitis.one.platform.approval.infrastructure.flowable;

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
        inCode.add("PROCESS:" + FlowableApprovalEngine.VARIABLE_CONTENT_DIGEST);
        inCode.add("PROCESS:" + FlowableApprovalEngine.VARIABLE_LEGAL_ENTITY);
        inCode.add("PROCESS:" + FlowableApprovalEngine.VARIABLE_SUBMITTED_ON);
        inCode.add("PROCESS:" + NodeDecisions.ROUTED + "*");
        inCode.add("TASK:" + FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE);
        inCode.add("TASK:" + FlowableApprovalEngine.VARIABLE_ADVISORY);
        inCode.add("TASK:" + FlowableApprovalEngine.VARIABLE_ASSIGNED_AT);
        assertThat(new TreeSet<>(ProcessArchiveJob.PROCESS_VARIABLES)).isEqualTo(new TreeSet<>(List.of(FlowableApprovalEngine.VARIABLE_CONTENT_DIGEST,
                FlowableApprovalEngine.VARIABLE_LEGAL_ENTITY, FlowableApprovalEngine.VARIABLE_SUBMITTED_ON)));
        assertThat(new TreeSet<>(inCode)).isEqualTo(new TreeSet<>(registered));
    }
}
