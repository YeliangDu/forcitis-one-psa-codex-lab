package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.domain.AuditEntryId;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditEntryQueries implements AuditEntryQueries {

    private final JdbcClient jdbc;

    JdbcAuditEntryQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditPage findPage(AuditQuery query) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (query.actorId() != null) {
            conditions.add("actor_id = :actor");
            params.put("actor", query.actorId().value());
        }
        if (query.onBehalfOfId() != null) {
            conditions.add("on_behalf_of_id = :onBehalfOf");
            params.put("onBehalfOf", query.onBehalfOfId().value());
        }
        if (query.action() != null) {
            conditions.add("action = :action");
            params.put("action", query.action());
        }
        if (query.actionPrefix() != null) {
            conditions.add("starts_with(action, :actionPrefix)");
            params.put("actionPrefix", query.actionPrefix());
        }
        if (query.sourceAddress() != null) {
            conditions.add("source_address = :sourceAddress");
            params.put("sourceAddress", query.sourceAddress());
        }
        if (query.result() != null) {
            conditions.add("result_kind = :result");
            params.put("result", query.result());
        }
        if (query.subjectKey() != null) {
            conditions.add("subject_key = :subject");
            params.put("subject", query.subjectKey());
        }
        if (query.from() != null) {
            conditions.add("created_at >= :from");
            params.put("from", Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            conditions.add("created_at < :to");
            params.put("to", Timestamp.from(query.to()));
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        if (query.after() != null) {
            where += (where.isEmpty() ? " where " : " and ") + "(created_at, id) < (:afterAt, :afterId)";
            params.put("afterAt", Timestamp.from(query.after().createdAt()));
            params.put("afterId", query.after().entryId().value());
        }
        List<AuditRecord> items = jdbc.sql("""
                        select id, created_at, action, actor_id, on_behalf_of_id, subject_key, detail, source_address, result_kind, change_reason,
                               before_text, after_text, correlation_id
                          from platform_audit.audit_entries
                        """ + where + """

                         order by created_at desc, id desc
                         limit :limit
                        """)
                .params(params).param("limit", query.limit() + 1)
                .query((rs, n) -> row(rs)).list();
        if (items.size() <= query.limit()) {
            return new AuditPage(items, null);
        }
        List<AuditRecord> page = items.subList(0, query.limit());
        AuditRecord last = page.getLast();
        return new AuditPage(page, new AuditQuery.Position(last.createdAt(), last.entryId()));
    }

    private static AuditRecord row(ResultSet rs) throws SQLException {
        return new AuditRecord(new AuditEntryId(rs.getLong("id")), rs.getTimestamp("created_at").toInstant(), rs.getString("action"),
                userId(rs, "actor_id"), userId(rs, "on_behalf_of_id"), rs.getString("subject_key"), rs.getString("detail"),
                rs.getString("source_address"), rs.getString("result_kind"), rs.getString("change_reason"), rs.getString("before_text"),
                rs.getString("after_text"), rs.getObject("correlation_id", java.util.UUID.class));
    }

    private static UserId userId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new UserId(value);
    }
}
