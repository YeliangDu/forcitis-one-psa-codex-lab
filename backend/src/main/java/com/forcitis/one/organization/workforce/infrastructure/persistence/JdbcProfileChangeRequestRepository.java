package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import com.forcitis.one.organization.workforce.api.ProfileChangeStatus;
import com.forcitis.one.organization.workforce.api.ProfileVoidReason;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.PersonalProfile;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequest;
import com.forcitis.one.organization.workforce.domain.ProfileChangeRequestRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Profile change requests in the workforce schema. A row is inserted pending and decided once; the table's checks and trigger hold the rest. */
@Repository
class JdbcProfileChangeRequestRepository implements ProfileChangeRequestRepository {
    private static final String COLUMNS = """
            select employee_profile_change_request_id, employee_id, requested_by, requested_at, base_display_name, base_gender_code, base_email_address, base_wecom_user_id,
                   display_name, gender_code, email_address, wecom_user_id, request_status, version, decided_by, decided_at, void_reason_kind, decision_reason
              from organization_workforce.employee_profile_change_requests
            """;

    private final JdbcClient jdbc;

    JdbcProfileChangeRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(ProfileChangeRequest request, Instant at) {
        jdbc.sql("""
                insert into organization_workforce.employee_profile_change_requests (employee_profile_change_request_id, employee_id, requested_by, requested_at,
                       base_display_name, base_gender_code, base_email_address, base_wecom_user_id, display_name, gender_code, email_address, wecom_user_id,
                       request_status, version, created_at, updated_at)
                values (:id, :employee, :by, :requestedAt, :baseName, :baseGender, :baseEmail, :baseWecom, :name, :gender, :email, :wecom, :status, :version, :at, :at)
                """)
                .param("id", request.id().value()).param("employee", request.employeeId().value()).param("by", request.requestedBy().value()).param("requestedAt", at(request.requestedAt()))
                .param("baseName", request.base().displayName()).param("baseGender", gender(request.base()), Types.VARCHAR).param("baseEmail", request.base().email(), Types.VARCHAR)
                .param("baseWecom", wecom(request.base()), Types.VARCHAR).param("name", request.after().displayName()).param("gender", gender(request.after()), Types.VARCHAR)
                .param("email", request.after().email(), Types.VARCHAR).param("wecom", wecom(request.after()), Types.VARCHAR)
                .param("status", request.status().name()).param("version", request.version()).param("at", at(at)).update();
    }

    @Override
    public Optional<ProfileChangeRequest> find(ProfileChangeRequestId id) {
        return jdbc.sql(COLUMNS + " where employee_profile_change_request_id = :id").param("id", id.value()).query((rs, n) -> request(rs)).optional();
    }

    @Override
    public void update(ProfileChangeRequest request, long loadedVersion, Instant at) {
        int updated = jdbc.sql("""
                update organization_workforce.employee_profile_change_requests set request_status = :status, decided_by = :by, decided_at = :decidedAt,
                       void_reason_kind = :voidReason, decision_reason = :reason, updated_at = :at, version = :version
                 where employee_profile_change_request_id = :id and version = :loaded
                """)
                .param("status", request.status().name()).param("by", request.decidedBy() == null ? null : request.decidedBy().value(), Types.OTHER)
                .param("decidedAt", request.decidedAt() == null ? null : at(request.decidedAt()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("voidReason", request.voidReason() == null ? null : request.voidReason().name(), Types.VARCHAR).param("reason", request.decisionReason(), Types.VARCHAR)
                .param("at", at(at)).param("version", request.version()).param("id", request.id().value()).param("loaded", loadedVersion).update();
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT, "the request was changed by someone else");
        }
    }

    @Override
    public boolean pendingFor(EmployeeId employee) {
        return jdbc.sql("select exists (select 1 from organization_workforce.employee_profile_change_requests where employee_id = :employee and request_status = 'PENDING')")
                .param("employee", employee.value()).query(Boolean.class).single();
    }

    @Override
    public List<ProfileChangeRequest> pending(@Nullable UUID after, int limit) {
        return jdbc.sql(COLUMNS + """
                 where request_status = 'PENDING' and (cast(:after as uuid) is null or (requested_at, employee_profile_change_request_id) > (
                       select requested_at, employee_profile_change_request_id from organization_workforce.employee_profile_change_requests
                        where employee_profile_change_request_id = :after))
                 order by requested_at, employee_profile_change_request_id limit :limit
                """)
                .param("after", after, Types.OTHER).param("limit", limit).query((rs, n) -> request(rs)).list();
    }

    @Override
    public Optional<Instant> lastConfirmedClearAt(EmployeeId employee) {
        return jdbc.sql("""
                select decided_at from organization_workforce.employee_profile_change_requests
                 where employee_id = :employee and request_status = 'CONFIRMED' and wecom_user_id is null
                 order by decided_at desc limit 1
                """)
                .param("employee", employee.value()).query((rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant()).optional();
    }

    private static ProfileChangeRequest request(ResultSet rs) throws SQLException {
        UUID decidedBy = rs.getObject("decided_by", UUID.class);
        OffsetDateTime decidedAt = rs.getObject("decided_at", OffsetDateTime.class);
        String voidReason = rs.getString("void_reason_kind");
        return ProfileChangeRequest.restore(new ProfileChangeRequestId(rs.getObject("employee_profile_change_request_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                new UserId(rs.getObject("requested_by", UUID.class)), rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                profile(rs.getString("base_display_name"), rs.getString("base_gender_code"), rs.getString("base_email_address"), rs.getString("base_wecom_user_id")),
                profile(rs.getString("display_name"), rs.getString("gender_code"), rs.getString("email_address"), rs.getString("wecom_user_id")),
                ProfileChangeStatus.valueOf(rs.getString("request_status")), rs.getLong("version"), decidedBy == null ? null : new UserId(decidedBy),
                decidedAt == null ? null : decidedAt.toInstant(), voidReason == null ? null : ProfileVoidReason.valueOf(voidReason), rs.getString("decision_reason"));
    }

    private static PersonalProfile profile(String name, @Nullable String gender, @Nullable String email, @Nullable String wecom) {
        return new PersonalProfile(name, gender == null ? null : Gender.valueOf(gender), email, wecom == null ? null : new WecomUserId(wecom));
    }

    private static @Nullable String gender(PersonalProfile profile) {
        return profile.gender() == null ? null : profile.gender().name();
    }

    private static @Nullable String wecom(PersonalProfile profile) {
        return profile.wecomUserId() == null ? null : profile.wecomUserId().value();
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
