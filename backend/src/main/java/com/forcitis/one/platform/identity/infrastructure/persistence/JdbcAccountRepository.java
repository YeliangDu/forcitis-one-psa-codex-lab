package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountListRow;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AccountStatus;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.identity.domain.GrantChanges;
import com.forcitis.one.platform.identity.domain.HandledOutcome;
import com.forcitis.one.platform.identity.domain.GrantClosing;
import com.forcitis.one.platform.identity.domain.GrantOpening;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAccountRepository.class);
    private static final int MAX_ADDRESS_LENGTH = 64;
    private static final Pattern ADDRESS_SHAPE = Pattern.compile(
            "(?:[0-9]{1,3}(?:\\.[0-9]{1,3}){3}|(?=[^:]*:[^:]*:)(?!.*:::)[0-9A-Fa-f.:]+)(?:%[0-9A-Za-z._-]+)?");

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcAccountRepository(JdbcClient jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> find(UserId userId) {
        return load(userId, false);
    }

    @Override
    public Optional<UserAccount> findForUpdate(UserId userId) {
        return load(userId, true);
    }

    private Optional<UserAccount> load(UserId userId, boolean lock) {
        JdbcClient.StatementSpec select = lock
                ? jdbc.sql("""
                        select user_id, employee_id, status, version, event_count from platform_identity.user_accounts where user_id = :id for update
                        """)
                : jdbc.sql("""
                        select user_id, employee_id, status, version, event_count from platform_identity.user_accounts where user_id = :id
                        """);
        Optional<UserAccount> found = select
                .param("id", userId.value())
                .query((rs, n) -> UserAccount.restore(new UserId(rs.getObject("user_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                        rs.getString("status"), Set.of(), rs.getLong("version"), rs.getLong("event_count")))
                .optional();
        if (found.isEmpty()) {
            return found;
        }
        UserAccount account = found.get();
        Set<RoleId> roles = openGrants(userId).stream().map(HeldGrant::roleId).collect(Collectors.toSet());
        return Optional.of(UserAccount.restore(account.userId(), account.employeeId(), account.status().name(), roles, account.version(),
                account.eventSequence()));
    }

    @Override
    public boolean isHandled(PersonnelCommandId command) {
        return jdbc.sql("select exists (select 1 from platform_identity.handled_personnel_commands where personnel_command_id = :id)")
                .param("id", command.value()).query(Boolean.class).single();
    }

    @Override
    public void saveHandled(PersonnelCommandId command, UserId user, HandledOutcome outcome, @Nullable AccountRoleGrantId grant) {
        jdbc.sql("""
                insert into platform_identity.handled_personnel_commands (personnel_command_id, user_id, command_kind, outcome_kind, account_role_grant_id, handled_at)
                values (:id, :user, 'REINSTATE', :outcome, :grant, :now)
                """).param("id", command.value()).param("user", user.value()).param("outcome", outcome.name())
                .param("grant", grant == null ? null : grant.value(), java.sql.Types.OTHER).param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).update();
    }

    @Override
    public List<HeldGrant> openGrants(UserId userId) {
        return jdbc.sql("""
                select g.account_role_grant_id, g.role_id, r.code
                  from platform_identity.account_role_grants g join platform_identity.roles r on r.role_id = g.role_id
                 where g.user_id = :id and g.%s
                 order by g.effective_from_at, g.account_role_grant_id
                """.formatted(Validity.CURRENT))
                .param("id", userId.value())
                .query((rs, n) -> new HeldGrant(new AccountRoleGrantId(rs.getObject("account_role_grant_id", UUID.class)),
                        new RoleId(rs.getObject("role_id", UUID.class)), new RoleCode(rs.getString("code"))))
                .list();
    }

    @Override
    public Optional<UserId> findByEmployee(EmployeeId employeeId) {
        return jdbc.sql("""
                select user_id from platform_identity.user_accounts where employee_id = :employee
                """).param("employee", employeeId.value())
                .query(UUID.class).optional().map(UserId::new);
    }

    @Override
    public Optional<EmployeeId> findEmployee(UserId userId) {
        return jdbc.sql("""
                select employee_id from platform_identity.user_accounts where user_id = :id
                """).param("id", userId.value())
                .query(UUID.class).optional().map(EmployeeId::new);
    }

    @Override
    public boolean insertIfAbsent(UserAccount opened) {
        int inserted = jdbc.sql("""
                        insert into platform_identity.user_accounts (user_id, employee_id, status, version, event_count)
                        values (:id, :employee, :status, :version, :eventSequence) on conflict (employee_id) do nothing
                        """)
                .param("id", opened.userId().value()).param("employee", opened.employeeId().value()).param("status", opened.status().name())
                .param("version", opened.versionAfterSave()).param("eventSequence", opened.eventSequenceAfterSave()).update();
        return inserted == 1;
    }

    @Override
    public void save(UserAccount account) {
        int updated = jdbc.sql("""
                        update platform_identity.user_accounts
                           set status = :status, updated_at = :updatedAt, version = :next, event_count = :eventSequence
                         where user_id = :id and version = :version and status = :loadedStatus
                        """)
                .param("status", account.status().name()).param("next", account.versionAfterSave())
                .param("eventSequence", account.eventSequenceAfterSave()).param("id", account.userId().value())
                .param("version", account.version()).param("loadedStatus", account.loadedStatus().name()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "account " + account.userId().value() + " was changed by someone else");
        }
    }

    @Override
    public void saveGrants(UserId userId, GrantChanges changes) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (GrantClosing closing : changes.closed()) {
            int closed = jdbc.sql("""
                    update platform_identity.account_role_grants
                       set effective_to_at = greatest(:now, effective_from_at), updated_at = :now, revoked_by = :revokedBy, revocation_cause_kind = :cause,
                           revocation_authorization_request_id = :request, revocation_separation_command_id = :command, revocation_audit_entry_id = :audit
                     where account_role_grant_id = :id and user_id = :user and %s
                    """.formatted(Validity.CURRENT))
                    .param("now", now).param("revokedBy", closing.revokedBy() == null ? null : closing.revokedBy().value()).param("cause", closing.cause().name())
                    .param("request", closing.request() == null ? null : closing.request().value())
                    .param("command", closing.separationCommand() == null ? null : closing.separationCommand().value())
                    .param("audit", closing.audit().value()).param("id", closing.id().value()).param("user", userId.value()).update();
            if (closed != 1) {
                throw new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                        "grant " + closing.id().value() + " of account " + userId.value() + " is not open any more");
            }
        }
        for (GrantOpening opening : changes.opened()) {
            jdbc.sql("""
                    select role_id from platform_identity.roles where role_id = :role for share
                    """).param("role", opening.roleId().value()).query(UUID.class).optional();
            boolean live = jdbc.sql("""
                    select exists (select 1 from platform_identity.role_versions where role_id = :role and %s)
                    """.formatted(Validity.CURRENT)).param("role", opening.roleId().value()).query(Boolean.class).single();
            if (!live) {
                throw new DomainException(IdentityError.PLATFORM_IDENTITY_ROLE_NOT_FOUND, ErrorKind.NOT_FOUND, "role " + opening.roleId().value() + " was deleted");
            }
            jdbc.sql("""
                    insert into platform_identity.account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, granted_by, grant_channel_kind,
                           grant_authorization_request_id, grant_audit_entry_id, created_at, updated_at)
                    values (:id, :user, :role, greatest(:now, coalesce((select max(g.effective_to_at) from platform_identity.account_role_grants g
                                                                         where g.user_id = :user and g.role_id = :role and not (g.%s)), :now)),
                            :grantedBy, :channel, :request, :audit, :now, :now)
                    """.formatted(Validity.CURRENT))
                    .param("id", opening.id().value()).param("user", userId.value()).param("role", opening.roleId().value()).param("now", now)
                    .param("grantedBy", opening.grantedBy() == null ? null : opening.grantedBy().value()).param("channel", opening.channel().name())
                    .param("request", opening.request() == null ? null : opening.request().value()).param("audit", opening.audit().value()).update();
        }
    }

    @Override
    public void recordLogin(UserId userId, Instant at, String address) {
        String stored = storableAddress(address);
        jdbc.sql("""
                        update platform_identity.user_accounts
                           set last_login_at = :at, last_login_address = :address, updated_at = :updatedAt
                         where user_id = :id
                        """)
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)).param("address", stored).param("id", userId.value())
                .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }

    /** An address the column constraint would refuse is left empty rather than cut or allowed to fail a login that already succeeded. */
    static String storableAddress(String address) {
        if (address != null && address.length() <= MAX_ADDRESS_LENGTH && ADDRESS_SHAPE.matcher(address).matches()) {
            return address;
        }
        LOG.error("ALERT platform.identity last login address not recorded: not an address (length {})", address == null ? 0 : address.length());
        return null;
    }

    @Override
    public List<Role> findEnabledRoles(UserId userId) {
        return RoleRows.select(jdbc, """
                join platform_identity.account_role_grants ar on ar.role_id = r.role_id
               where ar.user_id = :id and ar.%s and v.role_status = 'ENABLED'
                """.formatted(Validity.CURRENT), Map.of("id", userId.value()));
    }

    @Override
    public Set<RoleCode> findAssignedRoleCodes(UserId userId) {
        return jdbc.sql("""
                        select r.code from platform_identity.roles r
                          join platform_identity.account_role_grants ar on ar.role_id = r.role_id
                         where ar.user_id = :id and ar.%s
                        """.formatted(Validity.CURRENT))
                .param("id", userId.value()).query(String.class).list().stream().map(RoleCode::new).collect(Collectors.toSet());
    }

    @Override
    public List<AccountListRow> list(@Nullable AccountStatus status, @Nullable RoleCode roleCode, @Nullable ListPosition after, int limit) {
        return jdbc.sql("""
                select a.user_id, a.created_at, a.employee_id, a.status,
                       coalesce((select array_agg(r.code order by r.code) from platform_identity.account_role_grants g
                                   join platform_identity.roles r on r.role_id = g.role_id where g.user_id = a.user_id and g.%1$s), '{}') as role_codes
                  from platform_identity.user_accounts a
                 where (cast(:status as text) is null or a.status = :status)
                   and (cast(:roleCode as text) is null or exists (select 1 from platform_identity.account_role_grants g
                                   join platform_identity.roles r on r.role_id = g.role_id where g.user_id = a.user_id and g.%1$s and r.code = :roleCode))
                   and (cast(:afterAt as timestamptz) is null or (a.created_at, a.user_id) > (cast(:afterAt as timestamptz), cast(:afterId as uuid)))
                 order by a.created_at, a.user_id
                 limit :limit
                """.formatted(Validity.CURRENT))
                .param("status", status == null ? null : status.name(), java.sql.Types.VARCHAR)
                .param("roleCode", roleCode == null ? null : roleCode.value(), java.sql.Types.VARCHAR)
                .param("afterAt", after == null ? null : OffsetDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("afterId", after == null ? null : after.userId().value(), java.sql.Types.OTHER)
                .param("limit", limit)
                .query((rs, n) -> new AccountListRow(new UserId(rs.getObject("user_id", UUID.class)), rs.getObject("created_at", OffsetDateTime.class).toInstant(), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                        AccountStatus.valueOf(rs.getString("status")), List.of((String[]) rs.getArray("role_codes").getArray())))
                .list();
    }

    @Override
    public Set<UserId> findEnabledHolders(RoleCode roleCode) {
        return jdbc.sql("""
                        select ar.user_id
                          from platform_identity.account_role_grants ar
                          join platform_identity.roles r on r.role_id = ar.role_id
                          join platform_identity.user_accounts a on a.user_id = ar.user_id
                         where lower(r.code) = lower(:code) and a.status = 'ENABLED' and ar.%1$s
                           and exists (select 1 from platform_identity.role_versions v
                                        where v.role_id = r.role_id and v.%1$s and v.role_status = 'ENABLED')
                        """.formatted(Validity.CURRENT))
                .param("code", roleCode.value()).query(UUID.class).list().stream().map(UserId::new).collect(Collectors.toSet());
    }
}
