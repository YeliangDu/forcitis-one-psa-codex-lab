package com.forcitis.one.platform.approval.infrastructure.archive;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "forcitis.platform.approval.assignment.process-administrator-user-id=9001",
        "spring.datasource.hikari.connection-timeout=2000", "forcitis.platform.approval.process-archive.enabled=true"})
@Testcontainers
@Import(ProcessArchiveJobIT.FixedClock.class)
class ProcessArchiveJobIT {
    static final Instant START = Instant.parse("2026-10-14T08:00:00Z");
    static final String PROBE = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="retention-probe">
              <process id="retention_probe" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="wait"/>
                <receiveTask id="wait" name="Wait"/>
                <sequenceFlow id="f2" sourceRef="wait" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }
    }

    static Path archive;

    @DynamicPropertySource
    static void archiveDirectory(DynamicPropertyRegistry registry) throws IOException {
        archive = Files.createTempDirectory("act-hi-archive");
        registry.add("forcitis.platform.approval.process-archive.directory", archive::toString);
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired
    ProcessArchiveJob job;

    @Autowired
    MutableClock clock;

    @Autowired
    RepositoryService repository;

    @Autowired
    RuntimeService runtime;

    @Autowired
    HistoryService history;

    @BeforeEach
    void deployProbe() throws IOException {
        clock.set(START);
        if (repository.createProcessDefinitionQuery().processDefinitionKey("retention_probe").count() == 0) {
            repository.createDeployment().addString("retention_probe.bpmn20.xml", PROBE).deploy();
        }
        try (var files = Files.list(archive)) {
            files.forEach(file -> file.toFile().delete());
        }
    }

    private long finishedProbes() {
        return history.createHistoricProcessInstanceQuery().processDefinitionKey("retention_probe").finished().count();
    }

    private void finishOneProbe() {
        var instance = runtime.startProcessInstanceByKey("retention_probe", "document-" + System.nanoTime());
        runtime.trigger(runtime.createExecutionQuery().processInstanceId(instance.getId()).activityId("wait").singleResult().getId());
    }

    private List<String> archivedLines(Path file) {
        try (var in = new GZIPInputStream(Files.newInputStream(file))) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Instant at(java.time.ZonedDateTime moment) {
        return moment.toInstant();
    }

    @Test
    @DisplayName("T3 a finished instance stays online for 3 years, is then written to the archive and removed from ACT_HI_*; a running one is never touched")
    void archivesAfterThreeYears() throws IOException {
        java.time.ZonedDateTime base = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        long finishedBefore = finishedProbes();
        long runningBefore = history.createHistoricProcessInstanceQuery().processDefinitionKey("retention_probe").unfinished().count();
        finishOneProbe();
        finishOneProbe();
        runtime.startProcessInstanceByKey("retention_probe", "still-running");

        clock.set(at(base.plusYears(3).minusDays(2)));
        job.run();
        assertThat(finishedProbes()).as("not yet three years").isEqualTo(finishedBefore + 2);
        try (var files = Files.list(archive)) {
            assertThat(files).isEmpty();
        }

        clock.set(at(base.plusYears(3).plusDays(2)));
        job.run();

        assertThat(finishedProbes()).isEqualTo(0);
        assertThat(history.createHistoricProcessInstanceQuery().processDefinitionKey("retention_probe").unfinished().count()).isEqualTo(runningBefore + 1);
        Path file = archive.resolve("act_hi-" + base.getYear() + ".ndjson.gz");
        assertThat(file).exists();
        List<String> lines = archivedLines(file);
        assertThat(lines).hasSize((int) (finishedBefore + 2));
        assertThat(lines.getLast()).contains("\"definitionKey\":\"retention_probe\"").contains("\"activityId\":\"wait\"").contains("\"endedAt\":\"");
    }

    @Test
    @DisplayName("T3 an archive file is removed once its whole finish year is older than 10 years, not before")
    void removesArchivesAfterTenYears() throws IOException {
        java.time.ZonedDateTime base = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"));
        finishOneProbe();
        clock.set(at(base.plusYears(3).plusDays(2)));
        job.run();
        Path file = archive.resolve("act_hi-" + base.getYear() + ".ndjson.gz");
        assertThat(file).exists();
        java.time.LocalDate expiry = java.time.LocalDate.of(base.getYear() + 1, 1, 1).plusYears(10);

        clock.set(at(expiry.minusDays(1).atTime(12, 0).atZone(java.time.ZoneId.of("Asia/Shanghai"))));
        job.run();
        assertThat(file).exists();

        clock.set(at(expiry.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))));
        job.run();
        assertThat(file).doesNotExist();
    }
}
