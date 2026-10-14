package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestKind;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationRequestRepository implements AuthorizationRequestRepository {

    private static final String COLUMNS = """
            request_id, request_kind, requested_by, requested_at, subject_user_id, role_id, role_codes, new_role_code, role_name, is_enabled,
            access_scope_kind, permission_codes, request_status, decided_by, decided_at, version, event_count
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcAuthorizationRequestRepository(JdbcClient jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AuthorizationRequest> find(AuthorizationRequestId id) {
        return jdbc.sql("select " + COLUMNS + " from platform_identity.authorization_requests where request_id = :id")
                .param("id", id.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<AuthorizationRequest> findPending() {
        return jdbc.sql("select " + COLUMNS + " from platform_identity.authorization_requests where request_status = 'PENDING' order by requested_at")
                .query((rs, n) -> row(rs)).list();
    }

    @Override
    public void insert(AuthorizationRequest opened) {
        RoleDraft draft = opened.roleDraft();
        jdbc.sql("""
                        insert into platform_identity.authorization_requests (request_id, request_kind, requested_by, requested_at, subject_user_id,
                                role_id, role_codes, new_role_code, role_name, is_enabled, access_scope_kind, permission_codes, request_status,
                                version, event_count)
                        values (:id, :kind, :by, :at, :subject, :role, :roleCodes, :newCode, :roleName, :enabled, :scope, :permissions, :status,
                                :version, :eventSequence)
                        """)
                .param("id", opened.requestId().value()).param("kind", opened.kind().name()).param("by", opened.requestedBy().value())
                .param("at", OffsetDateTime.ofInstant(opened.requestedAt(), ZoneOffset.UTC))
                .param("subject", opened.subjectUser() == null ? null : opened.subjectUser().value(), Types.BIGINT)
                .param("role", opened.roleId() == null ? null : opened.roleId().value(), Types.BIGINT)
                .param("roleCodes", opened.roleCodes().toArray(String[]::new))
                .param("newCode", draft == null ? null : draft.code(), Types.VARCHAR)
                .param("roleName", draft == null ? null : draft.name(), Types.VARCHAR)
                .param("enabled", draft == null ? null : draft.enabled(), Types.BOOLEAN)
                .param("scope", draft == null || draft.accessScope() == null ? null : draft.accessScope().name(), Types.VARCHAR)
                .param("permissions", draft == null ? new String[0] : draft.permissionCodes().toArray(String[]::new))
                .param("status", opened.status().name()).param("version", opened.versionAfterSave())
                .param("eventSequence", opened.eventSequenceAfterSave()).update();
    }

    @Override
    public void save(AuthorizationRequest request) {
        int updated = jdbc.sql("""
                        update platform_identity.authorization_requests
                           set request_status = :status, decided_by = :by, decided_at = :at, updated_at = :updatedAt, version = :next, event_count = :eventSequence
                         where request_id = :id and version = :version and request_status = :loaded
                        """)
                .param("status", request.status().name()).param("by", request.decidedBy() == null ? null : request.decidedBy().value(), Types.BIGINT)
                .param("at", request.decidedAt() == null ? null : OffsetDateTime.ofInstant(request.decidedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("next", request.versionAfterSave()).param("eventSequence", request.eventSequenceAfterSave())
                .param("id", request.requestId().value()).param("version", request.version()).param("loaded", request.loadedStatus().name()).param("updatedAt", Timestamp.from(clock.instant()))
                .update();
        if (updated != 1) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the authorization request was decided by someone else");
        }
    }

    private static AuthorizationRequest row(ResultSet rs) throws SQLException {
        String newCode = rs.getString("new_role_code");
        RoleDraft draft = newCode == null && rs.getString("role_name") == null ? null
                : new RoleDraft(newCode, rs.getString("role_name"), rs.getBoolean("is_enabled"),
                        rs.getString("access_scope_kind") == null ? null : AccessScopeKind.valueOf(rs.getString("access_scope_kind")), strings(rs, "permission_codes"));
        long subject = rs.getLong("subject_user_id");
        boolean noSubject = rs.wasNull();
        long role = rs.getLong("role_id");
        boolean noRole = rs.wasNull();
        long decided = rs.getLong("decided_by");
        boolean notDecided = rs.wasNull();
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return AuthorizationRequest.restore(new AuthorizationRequestId(rs.getObject("request_id", UUID.class)),
                AuthorizationRequestKind.valueOf(rs.getString("request_kind")), new UserId(rs.getLong("requested_by")),
                rs.getObject("requested_at", OffsetDateTime.class).toInstant(), noSubject ? null : new UserId(subject), noRole ? null : new RoleId(role),
                strings(rs, "role_codes"), draft, rs.getString("request_status"), notDecided ? null : new UserId(decided),
                decidedAt == null ? null : decidedAt.toInstant(), rs.getLong("version"), rs.getLong("event_count"));
    }

    private static Set<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? Set.of() : new HashSet<>(List.of((String[]) array.getArray()));
    }
}
