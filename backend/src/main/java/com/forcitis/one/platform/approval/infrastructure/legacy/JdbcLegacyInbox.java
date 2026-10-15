package com.forcitis.one.platform.approval.infrastructure.legacy;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.domain.LegacyInbox;
import com.forcitis.one.platform.approval.domain.LegacyInstanceId;
import com.forcitis.one.platform.approval.domain.LegacyTodo;
import com.forcitis.one.platform.integration.api.ExternalFailure;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** No cache: every call queries (about 540 a day); an unreachable database yields available = false, never an exception that would hide the new tasks. */
class JdbcLegacyInbox implements LegacyInbox {

    private static final Logger log = LoggerFactory.getLogger(JdbcLegacyInbox.class);

    private final JdbcClient jdbc;
    private final String viewName;
    private final String detailUrlTemplate;

    JdbcLegacyInbox(JdbcClient jdbc, String viewName, String detailUrlTemplate) {
        this.jdbc = jdbc;
        this.viewName = viewName;
        this.detailUrlTemplate = detailUrlTemplate;
    }

    @Override
    public LegacyTodos readTodos(EmployeeId person) {
        try {
            List<LegacyTodo> todos = jdbc.sql("SELECT task_id, process_instance_id, task_def_key, task_name, process_title, created_at FROM "
                            + viewName + " WHERE assignee_user_id = :user ORDER BY created_at DESC")
                    .param("user", person.value()).query((rs, n) -> read(rs)).list();
            return new LegacyTodos(true, todos);
        } catch (DataAccessException e) {
            log.error("Legacy to-do view {} could not be read ({}); the merged inbox shows new tasks only", viewName, ExternalFailure.of(e));
            return LegacyTodos.unavailable();
        }
    }

    private LegacyTodo read(ResultSet rs) throws SQLException {
        String instance = rs.getString("process_instance_id");
        Timestamp created = rs.getTimestamp("created_at", Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return new LegacyTodo(rs.getString("task_id"), new LegacyInstanceId(instance), rs.getString("task_def_key"),
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
