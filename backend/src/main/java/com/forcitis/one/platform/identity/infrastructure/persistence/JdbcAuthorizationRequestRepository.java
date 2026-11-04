package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.platform.identity.api.AuthorizationRequestId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.api.RoleVersionId;
import com.forcitis.one.platform.identity.domain.AuthorizationBase;
import com.forcitis.one.platform.identity.domain.AuthorizationRequest;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestKind;
import com.forcitis.one.platform.identity.domain.AuthorizationRequestRepository;
import com.forcitis.one.platform.identity.domain.RoleDraft;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationRequestRepository implements AuthorizationRequestRepository {

    private static final String COLUMNS = """
            request_id, request_kind, requested_by, requested_at, subject_user_id, role_id, role_codes, new_role_code, role_name, is_enabled,
            access_scope_kind, permission_codes, base_account_version_number, base_role_version_ids, request_status, decided_by, decided_at, version, event_count
            """;

    private final JdbcClient jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcAuthorizationRequestRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public Optional<AuthorizationRequest> find(AuthorizationRequestId id) {
        return withUnits(jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.authorization_requests where request_id = :id
                """)
                .param("id", id.value()).query((rs, n) -> row(rs)).list()).stream().findFirst();
    }

    @Override
    public List<AuthorizationRequest> findPending() {
        return withUnits(jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.authorization_requests where request_status = 'PENDING' order by requested_at
                """)
                .query((rs, n) -> row(rs)).list());
    }

    @Override
    public List<AuthorizationRequest> findPendingOf(UserId user) {
        return withUnits(jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.authorization_requests
                where request_status = 'PENDING' and (subject_user_id = :user or requested_by = :user) order by requested_at for update
                """)
                .param("user", user.value())
                .query((rs, n) -> row(rs)).list());
    }

    @Override
    public void insert(AuthorizationRequest opened) {
        RoleDraft draft = opened.roleDraft();
        jdbc.sql("""
                        insert into platform_identity.authorization_requests (request_id, request_kind, requested_by, requested_at, subject_user_id,
                                role_id, role_codes, new_role_code, role_name, is_enabled, access_scope_kind, permission_codes, base_account_version_number,
                                base_role_version_ids, request_status, version, event_count)
                        values (:id, :kind, :by, :at, :subject, :role, :roleCodes, :newCode, :roleName, :enabled, :scope, :permissions, :baseAccount,
                                :baseRoles, :status, :version, :eventSequence)
                        """)
                .param("id", opened.requestId().value()).param("kind", opened.kind().name()).param("by", opened.requestedBy().value())
                .param("at", OffsetDateTime.ofInstant(opened.requestedAt(), ZoneOffset.UTC))
                .param("subject", opened.subjectUser() == null ? null : opened.subjectUser().value(), Types.OTHER)
                .param("role", opened.roleId() == null ? null : opened.roleId().value(), Types.OTHER)
                .param("roleCodes", opened.roleCodes().toArray(String[]::new))
                .param("newCode", draft == null ? null : draft.code(), Types.VARCHAR)
                .param("roleName", draft == null ? null : draft.name(), Types.VARCHAR)
                .param("enabled", draft == null ? null : draft.enabled(), Types.BOOLEAN)
                .param("scope", draft == null || draft.accessScope() == null ? null : draft.accessScope().name(), Types.VARCHAR)
                .param("permissions", draft == null ? new String[0] : draft.permissionCodes().toArray(String[]::new))
                .param("baseAccount", opened.base().accountVersion(), Types.BIGINT)
                .param("baseRoles", opened.base().roleVersions().stream().map(RoleVersionId::value).toArray(UUID[]::new))
                .param("status", opened.status().name()).param("version", opened.versionAfterSave())
                .param("eventSequence", opened.eventSequenceAfterSave()).update();
        if (draft == null) {
            return;
        }
        for (UUID orgUnit : draft.customOrgUnitIds()) {
            jdbc.sql("""
                            insert into platform_identity.authorization_request_custom_org_units (authorization_request_custom_org_unit_id, request_id,
                                   org_unit_id, created_at, updated_at)
                            values (:id, :request, :orgUnit, :at, :at)
                            """)
                    .param("id", ids.next()).param("request", opened.requestId().value()).param("orgUnit", orgUnit)
                    .param("at", OffsetDateTime.ofInstant(opened.requestedAt(), ZoneOffset.UTC))
                    .update();
        }
    }

    /** The units each request names, read once for all of them; the rows are written with the request and never change. */
    private Map<UUID, Set<UUID>> customOrgUnits(List<UUID> requests) {
        Map<UUID, Set<UUID>> units = new HashMap<>();
        if (requests.isEmpty()) {
            return units;
        }
        jdbc.sql("""
                select request_id, org_unit_id from platform_identity.authorization_request_custom_org_units where request_id in (:requests)
                """)
                .param("requests", requests)
                .query((ResultSet rs) -> {
                    units.computeIfAbsent(rs.getObject("request_id", UUID.class), id -> new HashSet<>()).add(rs.getObject("org_unit_id", UUID.class));
                });
        return units;
    }

    private List<AuthorizationRequest> withUnits(List<Row> rows) {
        Map<UUID, Set<UUID>> units = customOrgUnits(rows.stream().filter(row -> row.draft() != null).map(Row::requestId).toList());
        return rows.stream().map(row -> row.restore(units.getOrDefault(row.requestId(), Set.of()))).toList();
    }

    @Override
    public void save(AuthorizationRequest request) {
        int updated = jdbc.sql("""
                        update platform_identity.authorization_requests
                           set request_status = :status, decided_by = :by, decided_at = :at, updated_at = :updatedAt, version = :next, event_count = :eventSequence
                         where request_id = :id and version = :version and request_status = :loaded
                        """)
                .param("status", request.status().name()).param("by", request.decidedBy() == null ? null : request.decidedBy().value(), Types.OTHER)
                .param("at", request.decidedAt() == null ? null : OffsetDateTime.ofInstant(request.decidedAt(), ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("next", request.versionAfterSave()).param("eventSequence", request.eventSequenceAfterSave())
                .param("id", request.requestId().value()).param("version", request.version()).param("loaded", request.loadedStatus().name()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the authorization request was decided by someone else");
        }
    }

    /** The role definition of a request row, before its custom org units are attached. */
    private record DraftColumns(String code, String name, boolean enabled, @Nullable AccessScopeKind scope, Set<String> permissionCodes) {
    }

    /** One authorization request row; the draft's custom org units come from the child table. */
    private record Row(UUID requestId, AuthorizationRequestKind kind, UserId requestedBy, Instant requestedAt, @Nullable UserId subject,
                       @Nullable RoleId role, Set<String> roleCodes, @Nullable DraftColumns draft, AuthorizationBase base, String status, @Nullable UserId decidedBy,
                       @Nullable Instant decidedAt, long version, long eventCount) {

        AuthorizationRequest restore(Set<UUID> customOrgUnitIds) {
            RoleDraft roleDraft = draft == null ? null
                    : new RoleDraft(draft.code(), draft.name(), draft.enabled(), draft.scope(), draft.permissionCodes(), customOrgUnitIds);
            return AuthorizationRequest.restore(new AuthorizationRequestId(requestId), kind, requestedBy, requestedAt, subject, role, roleCodes, roleDraft, base,
                    status, decidedBy, decidedAt, version, eventCount);
        }
    }

    private static Row row(ResultSet rs) throws SQLException {
        String newCode = rs.getString("new_role_code");
        DraftColumns draft = newCode == null && rs.getString("role_name") == null ? null
                : new DraftColumns(newCode, rs.getString("role_name"), rs.getBoolean("is_enabled"),
                        rs.getString("access_scope_kind") == null ? null : AccessScopeKind.valueOf(rs.getString("access_scope_kind")), strings(rs, "permission_codes"));
        UUID subject = rs.getObject("subject_user_id", UUID.class);
        UUID role = rs.getObject("role_id", UUID.class);
        UUID decided = rs.getObject("decided_by", UUID.class);
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        return new Row(rs.getObject("request_id", UUID.class), AuthorizationRequestKind.valueOf(rs.getString("request_kind")),
                new UserId(rs.getObject("requested_by", UUID.class)), rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                subject == null ? null : new UserId(subject), role == null ? null : new RoleId(role), strings(rs, "role_codes"), draft, base(rs),
                rs.getString("request_status"), decided == null ? null : new UserId(decided), decidedAt == null ? null : decidedAt.toInstant(),
                rs.getLong("version"), rs.getLong("event_count"));
    }

    private static AuthorizationBase base(ResultSet rs) throws SQLException {
        Long account = (Long) rs.getObject("base_account_version_number");
        Array roles = rs.getArray("base_role_version_ids");
        List<RoleVersionId> versions = roles == null ? List.of() : Arrays.stream((UUID[]) roles.getArray()).map(RoleVersionId::new).toList();
        return new AuthorizationBase(account, versions);
    }

    private static Set<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? Set.of() : new HashSet<>(List.of((String[]) array.getArray()));
    }
}
