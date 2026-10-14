package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.platform.integration.api.ArchiveVault;
import com.forcitis.one.platform.integration.api.ArchivedObject;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.flowable.engine.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Data scorecard T3: finished process instances stay in ACT_HI_* for the online period (3 years). Each batch is written as one
 * immutable object through the archive vault (object store with WORM retention; the application never deletes archives) and only
 * after the vault confirmed it are the instances removed from the engine. An instance the engine cannot delete is skipped, logged
 * and counted, so one bad instance does not stop the batch; a batch identical to the previous one ends the run. An instance written
 * but not deleted is written again by a later run, so readers deduplicate by instanceId.
 * The archive holds ids, the definition key, version and deployment, times, assignees, candidates and delegations, the delete reason
 * and the whitelisted variables in architecture/archive-variables.txt (the names are the engine constants; routing flags only as booleans), never other variables.
 */
@Component
@EnableConfigurationProperties(ProcessArchivePolicy.class)
@ConditionalOnProperty(prefix = "forcitis.platform.approval.process-archive", name = "enabled", havingValue = "true", matchIfMissing = true)
class ProcessArchiveJob implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessArchiveJob.class);
    static final String KIND = "process";
    static final int BATCH = 100;
    static final List<String> PROCESS_VARIABLES = List.of(FlowableApprovalEngine.VARIABLE_CONTENT_DIGEST, FlowableApprovalEngine.VARIABLE_LEGAL_ENTITY,
            FlowableApprovalEngine.VARIABLE_SUBMITTED_ON);
    static final String ROUTING_VARIABLE_PREFIX = NodeDecisions.ROUTED;
    static final List<String> TASK_VARIABLES = List.of(FlowableApprovalEngine.VARIABLE_SYSTEM_COMPLETE, FlowableApprovalEngine.VARIABLE_ADVISORY,
            FlowableApprovalEngine.VARIABLE_ASSIGNED_AT);

    record ArchivedTask(String taskId, String name, String taskDefinitionKey, String assignee, String createdAt, String claimedAt,
                        String endedAt, String dueAt, String deleteReason) {
    }

    record ArchivedActivity(String activityId, String activityType, String activityName, String assignee, String startedAt, String endedAt) {
    }

    record ArchivedIdentityLink(String linkType, String userId, String groupId, String taskId, String createdAt) {
    }

    record ArchivedVariable(String name, String taskId, String value) {
    }

    record ArchivedProcess(String instanceId, String definitionKey, Integer definitionVersion, String deploymentId, String businessKey,
                           String startedAt, String endedAt, String starterId, String deleteReason, List<ArchivedTask> tasks,
                           List<ArchivedActivity> activities, List<ArchivedIdentityLink> identityLinks, List<ArchivedVariable> variables) {
    }

    private record Finished(String instanceId, ArchivedProcess process) {
    }

    private final JdbcClient jdbc;
    private final HistoryService engineHistory;
    private final ArchiveVault vault;
    private final Clock clock;
    private final BusinessZone zone;
    private final ProcessArchivePolicy policy;
    private final Counter skipped;
    private final Counter probeFailures;
    private final JsonMapper json = JsonMapper.builder().build();

    ProcessArchiveJob(JdbcClient jdbc, HistoryService engineHistory, ArchiveVault vault, Clock clock, BusinessZone zone, ProcessArchivePolicy policy,
                      MeterRegistry meters) {
        this.jdbc = jdbc;
        this.engineHistory = engineHistory;
        this.vault = vault;
        this.clock = clock;
        this.zone = zone;
        this.policy = policy;
        this.skipped = Counter.builder("forcitis.platform.approval.archive.skipped")
                .description("Finished process instances the engine could not delete after they were archived").register(meters);
        this.probeFailures = Counter.builder("forcitis.platform.approval.archive.probe.failures")
                .description("Failed write probes of the process archive target").register(meters);
    }

    @Override
    public void afterSingletonsInstantiated() {
        probe();
    }

    @Scheduled(cron = "${forcitis.platform.approval.process-archive.probe-cron}")
    @SchedulerLock(name = "platform.approval.process.archive.probe", lockAtMostFor = "PT10M")
    void probeDaily() {
        probe();
    }

    void probe() {
        try {
            vault.probe(KIND);
        } catch (RuntimeException failure) {
            LOG.error("ALERT platform.approval the process archive target is not usable: {}", failure.getMessage(), failure);
            probeFailures.increment();
        }
    }

    @Scheduled(cron = "${forcitis.platform.approval.process-archive.cron}")
    @SchedulerLock(name = "platform.approval.process.archive", lockAtMostFor = "PT2H")
    void run() {
        LocalDate today = zone.today(clock);
        int archived = archiveFinishedBefore(today.minus(policy.onlinePeriod()));
        LOG.info("platform.approval archived {} finished process instances", archived);
    }

    int archiveFinishedBefore(LocalDate cutoffDay) {
        Timestamp cutoff = Timestamp.from(cutoffDay.atStartOfDay(zone.zone()).toInstant());
        Set<String> notDeleted = new LinkedHashSet<>();
        List<String> previous = List.of();
        int total = 0;
        while (true) {
            List<Finished> batch = jdbc.sql("""
                            select p.id_, p.business_key_, p.start_user_id_, p.delete_reason_, p.start_time_, p.end_time_, d.key_, d.version_, d.deployment_id_
                              from platform_approval.act_hi_procinst p
                              left join platform_approval.act_re_procdef d on d.id_ = p.proc_def_id_
                             where p.end_time_ is not null and p.end_time_ < :cutoff
                             order by p.end_time_, p.id_ limit :batch offset :offset
                            """)
                    .param("cutoff", cutoff).param("batch", BATCH).param("offset", notDeleted.size()).query((rs, n) -> new Finished(rs.getString("id_"),
                            new ArchivedProcess(rs.getString("id_"), rs.getString("key_"), (Integer) rs.getObject("version_"), rs.getString("deployment_id_"),
                                    rs.getString("business_key_"), text(rs.getTimestamp("start_time_")), text(rs.getTimestamp("end_time_")),
                                    rs.getString("start_user_id_"), rs.getString("delete_reason_"), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                                    new ArrayList<>()))).list();
            List<String> ids = batch.stream().map(Finished::instanceId).toList();
            if (batch.isEmpty()) {
                return total;
            }
            if (ids.equals(previous)) {
                LOG.error("ALERT platform.approval the same {} finished instances came back after the engine deleted them: archiving stops", ids.size());
                return total;
            }
            previous = ids;
            List<ArchivedProcess> processes = withChildren(batch);
            ArchivedObject stored = vault.archive(KIND, processes.stream().map(this::line).toList());
            LOG.info("platform.approval archived {} finished process instances as {}", processes.size(), stored.key());
            for (String id : ids) {
                try {
                    engineHistory.deleteHistoricProcessInstance(id);
                    total++;
                } catch (RuntimeException failure) {
                    notDeleted.add(id);
                    skipped.increment();
                    LOG.error("ALERT platform.approval process instance {} is archived but the engine cannot delete it: {}", id, failure.getMessage());
                }
            }
        }
    }

    private String line(ArchivedProcess process) {
        return json.writeValueAsString(process);
    }

    private List<ArchivedProcess> withChildren(List<Finished> batch) {
        List<String> ids = batch.stream().map(Finished::instanceId).toList();
        Map<String, ArchivedProcess> byId = new LinkedHashMap<>();
        batch.forEach(f -> byId.put(f.instanceId(), f.process()));
        jdbc.sql("""
                select proc_inst_id_, id_, name_, task_def_key_, assignee_, start_time_, claim_time_, end_time_, due_date_, delete_reason_
                  from platform_approval.act_hi_taskinst where proc_inst_id_ in (:ids) order by start_time_, id_
                """).param("ids", ids).query((t, n) -> {
            byId.get(t.getString("proc_inst_id_")).tasks().add(new ArchivedTask(t.getString("id_"), t.getString("name_"), t.getString("task_def_key_"),
                    t.getString("assignee_"), text(t.getTimestamp("start_time_")), text(t.getTimestamp("claim_time_")), text(t.getTimestamp("end_time_")),
                    text(t.getTimestamp("due_date_")), t.getString("delete_reason_")));
            return null;
        }).list();
        jdbc.sql("""
                select proc_inst_id_, act_id_, act_type_, act_name_, assignee_, start_time_, end_time_
                  from platform_approval.act_hi_actinst where proc_inst_id_ in (:ids) order by start_time_, id_
                """).param("ids", ids).query((a, n) -> {
            byId.get(a.getString("proc_inst_id_")).activities().add(new ArchivedActivity(a.getString("act_id_"), a.getString("act_type_"),
                    a.getString("act_name_"), a.getString("assignee_"), text(a.getTimestamp("start_time_")), text(a.getTimestamp("end_time_"))));
            return null;
        }).list();
        jdbc.sql("""
                select proc_inst_id_, type_, user_id_, group_id_, task_id_, create_time_
                  from platform_approval.act_hi_identitylink where proc_inst_id_ in (:ids) order by create_time_, id_
                """).param("ids", ids).query((l, n) -> {
            byId.get(l.getString("proc_inst_id_")).identityLinks().add(new ArchivedIdentityLink(l.getString("type_"), l.getString("user_id_"),
                    l.getString("group_id_"), l.getString("task_id_"), text(l.getTimestamp("create_time_"))));
            return null;
        }).list();
        jdbc.sql("""
                select proc_inst_id_, name_, task_id_, coalesce(text_, cast(long_ as text), cast(double_ as text)) as value_
                  from platform_approval.act_hi_varinst
                 where proc_inst_id_ in (:ids)
                   and ((task_id_ is null and (name_ in (:processNames) or (name_ like :routing and var_type_ = 'boolean')))
                        or (task_id_ is not null and name_ in (:taskNames)))
                 order by id_
                """).param("ids", ids).param("processNames", PROCESS_VARIABLES).param("routing", ROUTING_VARIABLE_PREFIX.replace("_", "\\_") + "%").param("taskNames", TASK_VARIABLES)
                .query((v, n) -> {
                    byId.get(v.getString("proc_inst_id_")).variables().add(new ArchivedVariable(v.getString("name_"), v.getString("task_id_"), v.getString("value_")));
                    return null;
                }).list();
        return new ArrayList<>(byId.values());
    }

    private static String text(Timestamp time) {
        return time == null ? null : time.toInstant().toString();
    }
}
