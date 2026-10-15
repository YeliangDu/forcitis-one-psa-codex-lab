package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.MutableClock;
import com.forcitis.one.fixture.doubles.RecordingArchiveVault;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.audit.api.AuditLog;
import com.forcitis.one.platform.identity.api.RoleDirectory;
import com.forcitis.one.platform.integration.api.WecomGateway;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000", "forcitis.platform.approval.process-archive.enabled=true"})
@Testcontainers
@Import(ProcessArchiveJobIT.Doubles.class)
class ProcessArchiveJobIT {
    static final Instant START = Instant.parse("2026-10-14T08:00:00Z");
    static final String PROBE = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="archive-probe">
              <process id="archive_probe" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="wait"/>
                <receiveTask id="wait" name="Wait"/>
                <sequenceFlow id="f2" sourceRef="wait" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

    @TestConfiguration
    static class Doubles {
        @Bean
        @Primary
        MutableClock clock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }

        @Bean
        RecordingArchiveVault vault() {
            return new RecordingArchiveVault();
        }
    }

    @MockitoBean
    OrgDirectory orgDirectory;

    @MockitoBean
    RoleDirectory roleDirectory;

    @MockitoBean
    WecomGateway wecomGateway;

    @MockitoBean
    AuditLog auditLog;

    @MockitoSpyBean
    HistoryService history;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = TestPostgres.container();

    @Autowired
    ProcessArchiveJob job;

    @Autowired
    MutableClock clock;

    @Autowired
    RecordingArchiveVault vault;

    @Autowired
    RepositoryService repository;

    @Autowired
    RuntimeService runtime;

    @Autowired
    MeterRegistry meters;

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbc;

    @BeforeEach
    void deployProbe() {
        Mockito.reset(history);
        clock.set(START);
        vault.reset();
        if (repository.createProcessDefinitionQuery().processDefinitionKey("archive_probe").count() == 0) {
            repository.createDeployment().addString("archive_probe.bpmn20.xml", PROBE).deploy();
        }
        history.createHistoricProcessInstanceQuery().processDefinitionKey("archive_probe").list()
                .forEach(i -> runtime.createProcessInstanceQuery().processInstanceId(i.getId()).list().forEach(r -> runtime.deleteProcessInstance(r.getId(), "test cleanup")));
        history.createHistoricProcessInstanceQuery().processDefinitionKey("archive_probe").list().forEach(i -> history.deleteHistoricProcessInstance(i.getId()));
    }

    private String finishedProbe() {
        var instance = runtime.startProcessInstanceByKey("archive_probe", "document-" + System.nanoTime());
        runtime.trigger(runtime.createExecutionQuery().processInstanceId(instance.getId()).activityId("wait").singleResult().getId());
        return instance.getId();
    }

    private long finished() {
        return history.createHistoricProcessInstanceQuery().processDefinitionKey("archive_probe").finished().count();
    }

    private static Instant after3Years() {
        return ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusYears(3).plusDays(2).toInstant();
    }

    @Test
    @DisplayName("T3 a finished instance stays online for 3 years, then it is archived with its definition, links and activities and only then removed; a running one stays")
    void archivesThenDeletes() {
        finishedProbe();
        finishedProbe();
        runtime.startProcessInstanceByKey("archive_probe", "still-running");

        clock.set(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).plusYears(3).minusDays(2).toInstant());
        job.run();
        assertThat(vault.batches).isEmpty();
        assertThat(finished()).isEqualTo(2);

        clock.set(after3Years());
        job.run();

        assertThat(finished()).isZero();
        assertThat(history.createHistoricProcessInstanceQuery().processDefinitionKey("archive_probe").unfinished().count()).isGreaterThanOrEqualTo(1);
        assertThat(vault.lines()).hasSize(2).allSatisfy(line -> assertThat(line).contains("\"definitionKey\":\"archive_probe\"")
                .contains("\"definitionVersion\":").contains("\"deploymentId\":").contains("\"activityId\":\"wait\"").contains("\"identityLinks\":")
                .contains("\"variables\":"));
    }

    @Test
    @DisplayName("T3 when the vault fails (disk, upload, checksum) nothing is removed from the engine and the run fails loudly")
    void nothingIsDeletedWithoutTheVault() {
        finishedProbe();
        clock.set(after3Years());
        vault.failure = new IllegalStateException("upload failed");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> job.run()).hasMessageContaining("upload failed");


        assertThat(finished()).isEqualTo(1);
    }

    @Test
    @DisplayName("T3 an instance the engine cannot delete is skipped, logged and counted; the others of the batch are removed and the run ends")
    void aFailingDeleteDoesNotStopTheBatch() {
        String bad = finishedProbe();
        finishedProbe();
        double before = meters.counter("forcitis.platform.approval.archive.skipped").count();
        doThrow(new IllegalStateException("cannot delete")).when(history).deleteHistoricProcessInstance(bad);
        clock.set(after3Years());

        job.run();

        assertThat(finished()).isEqualTo(1);
        assertThat(history.createHistoricProcessInstanceQuery().processInstanceId(bad).count()).isEqualTo(1);
        assertThat(meters.counter("forcitis.platform.approval.archive.skipped").count()).isEqualTo(before + 1);
        assertThat(vault.batches).hasSize(1);
        assertThat(jdbc.sql("select count(*) from platform_approval.process_archive_skips where process_instance_id = cast(:id as uuid)").param("id", bad)
                .query(Long.class).single()).as("the skipped instance is recorded").isEqualTo(1L);

        job.run();
        assertThat(vault.batches).as("a skipped instance is not archived again").hasSize(1);

        Mockito.reset(history);
        history.deleteHistoricProcessInstance(bad);
    }

    @Test
    @DisplayName("T3 an instance whose engine id is not a canonical UUID is alerted and left alone; the others of the batch are archived and removed")
    void aForeignIdDoesNotStopTheBatch() {
        String foreign = finishedProbe();
        finishedProbe();
        jdbc.sql("update platform_approval.act_hi_procinst set id_ = 'not-a-uuid', proc_inst_id_ = 'not-a-uuid' where id_ = :id").param("id", foreign).update();
        clock.set(after3Years());

        job.run();

        assertThat(finished()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from platform_approval.act_hi_procinst where id_ = 'not-a-uuid'").query(Long.class).single()).isEqualTo(1L);
        assertThat(vault.lines()).hasSize(1).noneMatch(line -> line.contains("not-a-uuid"));
        jdbc.sql("delete from platform_approval.act_hi_procinst where id_ = 'not-a-uuid'").update();
    }

    @Test
    @DisplayName("T3 the write probe checks the archive target at start-up and every day; a failing probe is counted instead of stopping the application")
    void probeIsCounted() {
        double before = meters.counter("forcitis.platform.approval.archive.probe.failures").count();
        vault.failure = new IllegalStateException("bucket unreachable");

        job.probeDaily();

        assertThat(meters.counter("forcitis.platform.approval.archive.probe.failures").count()).isEqualTo(before + 1);
        assertThat(vault.probes).isGreaterThanOrEqualTo(1);
    }
}
