package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.domain.AuditEntryId;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditQuery;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import com.forcitis.one.platform.audit.domain.OwnLoginRecord;
import com.forcitis.one.platform.audit.domain.OwnLoginRecords;
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

    private static final String COLUMNS = "id, created_at, action, actor_id, on_behalf_of_id, subject_kind, subject_id, detail, source_address, outcome_kind, login_method_kind, change_reason, before_text, after_text, correlation_id";

    private final JdbcClient jdbc;

    JdbcAuditEntryQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditPage findPage(AuditQuery query) {
        Map<String, Object> params = new HashMap<>();
        String where = where(query, params);
        if (query.after() != null) {
            where += (where.isEmpty() ? " where " : " and ") + "(created_at, id) < (:afterAt, :afterId)";
            params.put("afterAt", Timestamp.from(query.after().createdAt()));
            params.put("afterId", query.after().entryId().value());
        }
        List<AuditRecord> items = jdbc.sql("select " + COLUMNS + " from platform_audit.audit_entries" + where + """

                         order by created_at desc, id desc
                         limit :limit
                        """)
                .params(params).param("limit", query.limit() + 1).query((rs, n) -> row(rs)).list();
        if (items.size() <= query.limit()) {
            return new AuditPage(items, null);
        }
        List<AuditRecord> page = items.subList(0, query.limit());
        AuditRecord last = page.getLast();
        return new AuditPage(page, new AuditQuery.Position(last.createdAt(), last.entryId()));
    }

    @Override
    public long count(AuditQuery filters) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("select count(*) from platform_audit.audit_entries" + where(filters, params)).params(params).query(Long.class).single();
    }

    @Override
    public List<AuditRecord> findAll(AuditQuery filters, int maxRows) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("select " + COLUMNS + " from platform_audit.audit_entries" + where(filters, params)
                + " order by created_at desc, id desc limit :maxRows")
                .params(params).param("maxRows", maxRows).query((rs, n) -> row(rs)).list();
    }

    @Override
    public List<OwnLoginRecord> findOwnLoginRecords(UserId subject, int limit) {
        return jdbc.sql("""
                        select created_at, action, outcome_kind, source_address, login_method_kind
                          from platform_audit.audit_entries
                         where subject_kind = :subjectKind and subject_id = :subject and action in (:actions)
                         order by created_at desc, id desc
                         limit :limit
                        """)
                .param("subjectKind", AuditSubjectKind.USER_ACCOUNT.name()).param("subject", subject.value()).param("actions", OwnLoginRecords.ACTIONS).param("limit", limit)
                .query((rs, n) -> new OwnLoginRecord(rs.getTimestamp("created_at").toInstant(), rs.getString("action"), outcome(rs),
                        rs.getString("source_address"), rs.getString("login_method_kind")))
                .list();
    }

    private static String where(AuditQuery query, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
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
        if (query.outcome() != null) {
            conditions.add("outcome_kind = :outcome");
            params.put("outcome", query.outcome().name());
        }
        if (query.subjectKind() != null) {
            conditions.add("subject_kind = :subjectKind");
            params.put("subjectKind", query.subjectKind().name());
        }
        if (query.subjectId() != null) {
            conditions.add("subject_id = :subjectId");
            params.put("subjectId", query.subjectId());
        }
        if (query.from() != null) {
            conditions.add("created_at >= :from");
            params.put("from", Timestamp.from(query.from()));
        }
        if (query.to() != null) {
            conditions.add("created_at < :to");
            params.put("to", Timestamp.from(query.to()));
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static AuditRecord row(ResultSet rs) throws SQLException {
        return new AuditRecord(new AuditEntryId(rs.getObject("id", java.util.UUID.class)), rs.getTimestamp("created_at").toInstant(), rs.getString("action"),
                userId(rs, "actor_id"), userId(rs, "on_behalf_of_id"),
                new AuditSubject(AuditSubjectKind.valueOf(rs.getString("subject_kind")), rs.getObject("subject_id", java.util.UUID.class)), rs.getString("detail"),
                rs.getString("source_address"), outcome(rs), rs.getString("login_method_kind"), rs.getString("change_reason"),
                rs.getString("before_text"), rs.getString("after_text"), rs.getObject("correlation_id", java.util.UUID.class));
    }

    private static AuditOutcome outcome(ResultSet rs) throws SQLException {
        String value = rs.getString("outcome_kind");
        return value == null ? null : AuditOutcome.valueOf(value);
    }

    private static UserId userId(ResultSet rs, String column) throws SQLException {
        java.util.UUID value = rs.getObject(column, java.util.UUID.class);
        return value == null ? null : new UserId(value);
    }
}
