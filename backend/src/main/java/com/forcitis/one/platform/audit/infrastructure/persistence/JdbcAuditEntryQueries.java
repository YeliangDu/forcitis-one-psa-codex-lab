package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.audit.api.AuditOutcome;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import com.forcitis.one.platform.audit.domain.AuditEntryQueries;
import com.forcitis.one.platform.audit.domain.AuditFilter;
import com.forcitis.one.platform.audit.domain.AuditPage;
import com.forcitis.one.platform.audit.domain.AuditPageMark;
import com.forcitis.one.platform.audit.domain.AuditRecord;
import com.forcitis.one.platform.audit.domain.OwnLoginRecord;
import com.forcitis.one.platform.audit.domain.OwnLoginRecordPage;
import com.forcitis.one.platform.audit.domain.OwnLoginRecords;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditEntryQueries implements AuditEntryQueries {

    private static final String COLUMNS = """
            id, created_at, action, actor_id, on_behalf_of_id, subject_kind, subject_id, detail, source_address, outcome_kind,
            login_method_kind, change_reason, before_text, after_text, correlation_id
            """;

    private final JdbcClient jdbc;

    JdbcAuditEntryQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AuditPage findPage(AuditFilter filter, AuditPageMark after, int limit) {
        Map<String, Object> params = new HashMap<>();
        String where = after(where(filter, params), after, params);
        List<AuditRecord> items = jdbc.sql("""
                select
                """ + COLUMNS + """
                         from platform_audit.audit_entries
                        """ + where + """

                         order by created_at desc, id desc
                         limit :limit
                        """)
                .params(params).param("limit", limit + 1).query((rs, n) -> row(rs)).list();
        if (items.size() <= limit) {
            return new AuditPage(items, null);
        }
        List<AuditRecord> page = items.subList(0, limit);
        AuditRecord last = page.getLast();
        return new AuditPage(page, new AuditPageMark(last.createdAt(), last.entryId()));
    }

    @Override
    public List<AuditRecord> findLatest(AuditFilter filter, int limit) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("""
                select
                """ + COLUMNS + """
                         from platform_audit.audit_entries
                        """ + where(filter, params)
                + " order by created_at desc, id desc limit :limit")
                .params(params).param("limit", limit).query((rs, n) -> row(rs)).list();
    }

    @Override
    public OwnLoginRecordPage findOwnLoginRecords(UserId subject, AuditPageMark after, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("subjectKind", AuditSubjectKind.USER_ACCOUNT.name());
        params.put("subject", subject.value());
        params.put("actions", OwnLoginRecords.ACTIONS);
        params.put("limit", limit + 1);
        String where = after(" where subject_kind = :subjectKind and subject_id = :subject and action in (:actions)", after, params);
        List<OwnLoginRecord> items = jdbc.sql("""
                        select id, created_at, action, outcome_kind, source_address, login_method_kind
                          from platform_audit.audit_entries""" + where + """

                         order by created_at desc, id desc
                         limit :limit
                        """)
                .params(params)
                .query((rs, n) -> new OwnLoginRecord(new AuditEntryId(rs.getObject("id", UUID.class)), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getString("action"), outcome(rs), rs.getString("source_address"), rs.getString("login_method_kind")))
                .list();
        if (items.size() <= limit) {
            return new OwnLoginRecordPage(items, null);
        }
        List<OwnLoginRecord> page = items.subList(0, limit);
        OwnLoginRecord last = page.getLast();
        return new OwnLoginRecordPage(page, new AuditPageMark(last.occurredAt(), last.entryId()));
    }

    private static String after(String where, AuditPageMark after, Map<String, Object> params) {
        if (after == null) {
            return where;
        }
        params.put("afterAt", OffsetDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC));
        params.put("afterId", after.entryId().value());
        return where + (where.isEmpty() ? " where " : " and ") + "created_at <= :afterAt and (created_at, id) < (:afterAt, :afterId)";
    }

    private static String where(AuditFilter query, Map<String, Object> params) {
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
        AuditSubjectKind kind = query.subject() != null ? query.subject().kind() : query.subjectKind();
        if (kind != null) {
            conditions.add("subject_kind = :subjectKind");
            params.put("subjectKind", kind.name());
        }
        if (query.subject() != null && query.subject().id() != null) {
            conditions.add("subject_id = :subjectId");
            params.put("subjectId", query.subject().id());
        }
        if (query.from() != null) {
            conditions.add("created_at >= :from");
            params.put("from", OffsetDateTime.ofInstant(query.from(), ZoneOffset.UTC));
        }
        if (query.to() != null) {
            conditions.add("created_at < :to");
            params.put("to", OffsetDateTime.ofInstant(query.to(), ZoneOffset.UTC));
        }
        return conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
    }

    private static AuditRecord row(ResultSet rs) throws SQLException {
        return new AuditRecord(new AuditEntryId(rs.getObject("id", UUID.class)), rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("action"),
                userId(rs, "actor_id"), userId(rs, "on_behalf_of_id"),
                new AuditSubject(AuditSubjectKind.valueOf(rs.getString("subject_kind")), rs.getObject("subject_id", UUID.class)), rs.getString("detail"),
                rs.getString("source_address"), outcome(rs), rs.getString("login_method_kind"), rs.getString("change_reason"),
                rs.getString("before_text"), rs.getString("after_text"), rs.getObject("correlation_id", UUID.class));
    }

    private static AuditOutcome outcome(ResultSet rs) throws SQLException {
        String value = rs.getString("outcome_kind");
        return value == null ? null : AuditOutcome.valueOf(value);
    }

    private static UserId userId(ResultSet rs, String column) throws SQLException {
        UUID value = rs.getObject(column, UUID.class);
        return value == null ? null : new UserId(value);
    }
}
