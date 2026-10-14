package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.domain.AuditEntryId;
import com.forcitis.one.platform.audit.domain.AuditEntryRepository;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditEntryRepository implements AuditEntryRepository {

    private final JdbcClient jdbc;

    JdbcAuditEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(AuditEntry entry, Instant createdAt) {
        jdbc.sql("""
                        insert into platform_audit.audit_entries (created_at, updated_at, action, actor_id, on_behalf_of_id, subject_key, detail)
                        values (:createdAt, :createdAt, :action, :actor, :onBehalfOf, :subject, :detail)
                        """)
                .param("createdAt", Timestamp.from(createdAt))
                .param("action", entry.action())
                .param("actor", entry.actorId() == null ? null : entry.actorId().value(), Types.BIGINT)
                .param("onBehalfOf", entry.onBehalfOfId() == null ? null : entry.onBehalfOfId().value(), Types.BIGINT)
                .param("subject", entry.subjectKey())
                .param("detail", entry.detail(), Types.VARCHAR)
                .update();
    }

    @Override
    public AuditPage findPage(AuditQuery query) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (query.actorId() != null) {
            conditions.add("actor_id = :actor");
            params.put("actor", query.actorId().value());
        }
        if (query.action() != null) {
            conditions.add("action = :action");
            params.put("action", query.action());
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
        long matchCount = jdbc.sql("select count(*) from platform_audit.audit_entries" + where).params(params).query(Long.class).single();
        List<AuditRecord> items = jdbc.sql("""
                        select id, created_at, action, actor_id, on_behalf_of_id, subject_key, detail
                          from platform_audit.audit_entries
                        """ + where + """

                         order by created_at desc, id desc
                         limit :limit offset :offset
                        """)
                .params(params).param("limit", query.limit()).param("offset", (long) query.page() * query.limit())
                .query((rs, n) -> row(rs)).list();
        return new AuditPage(items, query.page(), query.limit(), matchCount);
    }

    private static AuditRecord row(ResultSet rs) throws SQLException {
        return new AuditRecord(new AuditEntryId(rs.getLong("id")), rs.getTimestamp("created_at").toInstant(), rs.getString("action"),
                userId(rs, "actor_id"), userId(rs, "on_behalf_of_id"), rs.getString("subject_key"), rs.getString("detail"));
    }

    private static UserId userId(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : new UserId(value);
    }
}
