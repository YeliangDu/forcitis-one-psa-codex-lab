package com.forcitis.one.platform.approval.infrastructure.archive;

import com.forcitis.one.kernel.BusinessZone;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.flowable.engine.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Data scorecard T3: finished process instances stay in ACT_HI_* for the online period (3 years), then are written to a gzip
 * NDJSON file per finish year and deleted through the engine; a file goes when its whole year is older than the archive period
 * (10 years). The archive holds ids, definition keys, times, assignees and the delete reason, never variables: the approval
 * outcome and its content live in the approval tables and the audit trail. An instance written but not yet deleted when the job
 * stops is written again on the next run, so readers deduplicate by instanceId. Times are read as the engine wrote them (JVM zone).
 */
@Component
@EnableConfigurationProperties(ProcessArchivePolicy.class)
@ConditionalOnProperty(prefix = "forcitis.platform.approval.process-archive", name = "enabled", havingValue = "true", matchIfMissing = true)
class ProcessArchiveJob {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessArchiveJob.class);
    private static final Pattern ARCHIVE_FILE = Pattern.compile("act_hi-(\\d{4})\\.ndjson\\.gz");
    private static final int BATCH = 100;

    record ArchivedTask(String taskId, String name, String taskDefinitionKey, String assignee, String createdAt, String claimedAt,
                        String endedAt, String dueAt, String deleteReason) {
    }

    record ArchivedActivity(String activityId, String activityType, String activityName, String assignee, String startedAt, String endedAt) {
    }

    record ArchivedProcess(String instanceId, String definitionKey, String businessKey, String startedAt, String endedAt,
                           String starterId, String deleteReason, List<ArchivedTask> tasks, List<ArchivedActivity> activities) {
    }

    private record Finished(String instanceId, int finishYear, ArchivedProcess process) {
    }

    private final JdbcClient jdbc;
    private final HistoryService engineHistory;
    private final Clock clock;
    private final BusinessZone zone;
    private final ProcessArchivePolicy policy;
    private final JsonMapper json = JsonMapper.builder().build();

    ProcessArchiveJob(JdbcClient jdbc, HistoryService engineHistory, Clock clock, BusinessZone zone, ProcessArchivePolicy policy) {
        this.jdbc = jdbc;
        this.engineHistory = engineHistory;
        this.clock = clock;
        this.zone = zone;
        this.policy = policy;
    }

    @Scheduled(cron = "${forcitis.platform.approval.process-archive.cron}")
    @SchedulerLock(name = "platform.approval.process.archive", lockAtMostFor = "PT2H")
    void run() {
        LocalDate today = zone.today(clock);
        int archived = archiveFinishedBefore(today.minus(policy.onlinePeriod()));
        int removed = removeExpiredFiles(today);
        LOG.info("platform.approval archived {} finished process instances, removed {} archive files past {}", archived, removed, policy.archivePeriod());
    }

    int archiveFinishedBefore(LocalDate cutoffDay) {
        Timestamp cutoff = Timestamp.from(cutoffDay.atStartOfDay(zone.zone()).toInstant());
        int total = 0;
        while (true) {
            List<Finished> batch = jdbc.sql("""
                            select p.id_, p.business_key_, p.start_user_id_, p.delete_reason_, p.start_time_, p.end_time_, d.key_
                              from platform_approval.act_hi_procinst p
                              left join platform_approval.act_re_procdef d on d.id_ = p.proc_def_id_
                             where p.end_time_ is not null and p.end_time_ < :cutoff
                             order by p.end_time_ limit :batch
                            """)
                    .param("cutoff", cutoff).param("batch", BATCH).query((rs, n) -> finished(rs)).list();
            if (batch.isEmpty()) {
                return total;
            }
            Map<Integer, List<ArchivedProcess>> byYear = new LinkedHashMap<>();
            batch.forEach(f -> byYear.computeIfAbsent(f.finishYear(), y -> new ArrayList<>()).add(f.process()));
            byYear.forEach(this::append);
            batch.forEach(f -> engineHistory.deleteHistoricProcessInstance(f.instanceId()));
            total += batch.size();
        }
    }

    int removeExpiredFiles(LocalDate today) {
        int removed = 0;
        try (Stream<Path> files = Files.list(policy.directory())) {
            for (Path file : files.toList()) {
                Matcher name = ARCHIVE_FILE.matcher(file.getFileName().toString());
                if (name.matches() && !LocalDate.of(Integer.parseInt(name.group(1)) + 1, 1, 1).plus(policy.archivePeriod()).isAfter(today)) {
                    Files.delete(file);
                    removed++;
                }
            }
        } catch (java.nio.file.NoSuchFileException none) {
            return 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return removed;
    }

    private Finished finished(ResultSet rs) throws SQLException {
        String id = rs.getString("id_");
        Timestamp ended = rs.getTimestamp("end_time_");
        List<ArchivedTask> tasks = jdbc.sql("""
                        select id_, name_, task_def_key_, assignee_, start_time_, claim_time_, end_time_, due_date_, delete_reason_
                          from platform_approval.act_hi_taskinst where proc_inst_id_ = :id order by start_time_, id_
                        """)
                .param("id", id).query((t, n) -> new ArchivedTask(t.getString("id_"), t.getString("name_"), t.getString("task_def_key_"),
                        t.getString("assignee_"), text(t.getTimestamp("start_time_")), text(t.getTimestamp("claim_time_")),
                        text(t.getTimestamp("end_time_")), text(t.getTimestamp("due_date_")), t.getString("delete_reason_"))).list();
        List<ArchivedActivity> activities = jdbc.sql("""
                        select act_id_, act_type_, act_name_, assignee_, start_time_, end_time_
                          from platform_approval.act_hi_actinst where proc_inst_id_ = :id order by start_time_, id_
                        """)
                .param("id", id).query((a, n) -> new ArchivedActivity(a.getString("act_id_"), a.getString("act_type_"), a.getString("act_name_"),
                        a.getString("assignee_"), text(a.getTimestamp("start_time_")), text(a.getTimestamp("end_time_")))).list();
        ArchivedProcess process = new ArchivedProcess(id, rs.getString("key_"), rs.getString("business_key_"), text(rs.getTimestamp("start_time_")),
                text(ended), rs.getString("start_user_id_"), rs.getString("delete_reason_"), tasks, activities);
        return new Finished(id, ended.toInstant().atZone(zone.zone()).getYear(), process);
    }

    private static String text(Timestamp time) {
        return time == null ? null : time.toInstant().toString();
    }

    private void append(int finishYear, List<ArchivedProcess> processes) {
        Path file = policy.directory().resolve("act_hi-" + finishYear + ".ndjson.gz");
        try {
            Files.createDirectories(policy.directory());
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                OutputStream raw = Channels.newOutputStream(channel);
                GZIPOutputStream gzip = new GZIPOutputStream(raw);
                for (ArchivedProcess process : processes) {
                    gzip.write((json.writeValueAsString(process) + "\n").getBytes(StandardCharsets.UTF_8));
                }
                gzip.finish();
                channel.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the process archive " + file + "; nothing was deleted from the engine", e);
        }
    }
}
