package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.integration.api.ExternalFailure;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.LegacyInbox;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.SourceReferences;
import com.forcitis.one.platform.integration.domain.LegacyInstanceId;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * No cache: every call queries (about 540 a day). The old system names a person by its own id, found through the (LEGACY_SYSTEM, EMPLOYEE)
 * source reference of the employee; without one, or with an unreachable database, the answer is available = false, never an empty list
 * or an exception that would hide the new tasks (C8: until P6 imports the references nobody has one). Every such answer counts in
 * forcitis.platform.integration.legacy.inbox.unmapped; the ALERT comes once per employee and business day, the repeats are WARN.
 */
class JdbcLegacyInbox implements LegacyInbox {

    private static final Logger log = LoggerFactory.getLogger(JdbcLegacyInbox.class);

    private final JdbcClient jdbc;
    private final String viewName;
    private final String detailUrlTemplate;
    private final SourceReferences references;
    private final BusinessZone zone;
    private final Clock clock;
    private final Counter unmapped;
    private final Map<EmployeeId, LocalDate> alertedOn = new ConcurrentHashMap<>();

    JdbcLegacyInbox(JdbcClient jdbc, String viewName, String detailUrlTemplate, SourceReferences references, BusinessZone zone, Clock clock,
                    MeterRegistry meters) {
        this.jdbc = jdbc;
        this.viewName = viewName;
        this.detailUrlTemplate = detailUrlTemplate;
        this.references = references;
        this.zone = zone;
        this.clock = clock;
        this.unmapped = Counter.builder("forcitis.platform.integration.legacy.inbox.unmapped")
                .description("Merged-inbox reads of an employee without a LEGACY_SYSTEM EMPLOYEE source reference").register(meters);
    }

    @Override
    public LegacyTodos readTodos(EmployeeId person) {
        LocalDate today = zone.today(clock);
        OptionalLong legacyPerson = references.findSourceId(ExternalSource.LEGACY_SYSTEM, SourceObjectKind.EMPLOYEE, person.value(), today);
        if (legacyPerson.isEmpty()) {
            unmapped.increment();
            if (today.equals(alertedOn.put(person, today))) {
                log.warn("Employee {} has no LEGACY_SYSTEM EMPLOYEE source reference: the old to-dos are unavailable (alerted today already)", person);
            } else {
                log.error("ALERT platform.integration employee {} has no LEGACY_SYSTEM EMPLOYEE source reference: the old to-dos are unavailable", person);
            }
            return LegacyTodos.unavailable();
        }
        try {
            List<LegacyTodoRow> rows = jdbc.sql("SELECT task_id, process_instance_id, task_def_key, task_name, process_title, created_at FROM "
                            + viewName + " WHERE assignee_user_id = :user ORDER BY created_at DESC")
                    .param("user", legacyPerson.getAsLong()).query((rs, n) -> read(rs)).list();
            return new LegacyTodos(true, InboxMerger.mergeRowTasks(rows).stream().map(LegacyTodoRow::todo).toList());
        } catch (DataAccessException e) {
            log.error("Legacy to-do view {} could not be read ({}); the merged inbox shows new tasks only", viewName, ExternalFailure.of(e));
            return LegacyTodos.unavailable();
        }
    }

    private LegacyTodoRow read(ResultSet rs) throws SQLException {
        String instance = rs.getString("process_instance_id");
        Timestamp created = rs.getTimestamp("created_at", Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return new LegacyTodoRow(rs.getString("task_id"), new LegacyInstanceId(instance), rs.getString("task_def_key"),
                rs.getString("task_name"), rs.getString("process_title"), created == null ? null : created.toInstant(),
                detailUrl(instance, rs.getString("task_id")));
    }

    private String detailUrl(String instanceId, String taskId) {
        if (detailUrlTemplate == null || detailUrlTemplate.isBlank()) {
            return null;
        }
        return detailUrlTemplate.replace("{processInstanceId}", URLEncoder.encode(instanceId, StandardCharsets.UTF_8))
                .replace("{taskId}", URLEncoder.encode(taskId, StandardCharsets.UTF_8));
    }
}
