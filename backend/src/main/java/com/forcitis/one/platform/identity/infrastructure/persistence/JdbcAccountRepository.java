package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
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
    private final IdGenerator ids;

    JdbcAccountRepository(JdbcClient jdbc, Clock clock, IdGenerator ids) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> find(UserId userId) {
        Optional<UserAccount> found = jdbc.sql("""
                        select user_id, employee_id, status, version, event_count from platform_identity.user_accounts where user_id = :id
                        """)
                .param("id", userId.value())
                .query((rs, n) -> UserAccount.restore(new UserId(rs.getObject("user_id", UUID.class)), new EmployeeId(rs.getObject("employee_id", UUID.class)),
                        rs.getString("status"), Set.of(), rs.getLong("version"), rs.getLong("event_count")))
                .optional();
        if (found.isEmpty()) {
            return found;
        }
        UserAccount account = found.get();
        Set<RoleId> roles = new HashSet<>(jdbc.sql("select role_id from platform_identity.account_roles where user_id = :id")
                .param("id", userId.value()).query(UUID.class).list().stream().map(RoleId::new).toList());
        return Optional.of(UserAccount.restore(account.userId(), account.employeeId(), account.status().name(), roles, account.version(),
                account.eventSequence()));
    }

    @Override
    public Optional<UserId> findByEmployee(EmployeeId employeeId) {
        return jdbc.sql("select user_id from platform_identity.user_accounts where employee_id = :employee").param("employee", employeeId.value())
                .query(UUID.class).optional().map(UserId::new);
    }

    @Override
    public Optional<EmployeeId> findEmployee(UserId userId) {
        return jdbc.sql("select employee_id from platform_identity.user_accounts where user_id = :id").param("id", userId.value())
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
        if (inserted == 1) {
            saveRoles(opened.userId(), opened.roles());
        }
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
                .param("version", account.version()).param("loadedStatus", account.loadedStatus().name()).param("updatedAt", Timestamp.from(clock.instant()))
                .update();
        if (updated != 1) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "account " + account.userId().value() + " was changed by someone else");
        }
        if (account.rolesChanged()) {
            saveRoles(account.userId(), account.roles());
        }
    }

    private void saveRoles(UserId userId, Set<RoleId> roleIds) {
        if (roleIds.isEmpty()) {
            jdbc.sql("delete from platform_identity.account_roles where user_id = :user").param("user", userId.value()).update();
            return;
        }
        jdbc.sql("delete from platform_identity.account_roles where user_id = :user and role_id not in (:roles)")
                .param("user", userId.value()).param("roles", roleIds.stream().map(RoleId::value).toList()).update();
        for (RoleId roleId : roleIds) {
            jdbc.sql("insert into platform_identity.account_roles (account_role_id, user_id, role_id) values (:id, :user, :role) on conflict do nothing")
                    .param("id", ids.next()).param("user", userId.value()).param("role", roleId.value()).update();
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
                .param("updatedAt", Timestamp.from(clock.instant()))
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
                join platform_identity.account_roles ar on ar.role_id = r.role_id
               where ar.user_id = :id and v.role_status = 'ENABLED'
                """, Map.of("id", userId.value()));
    }

    @Override
    public Set<RoleCode> findAssignedRoleCodes(UserId userId) {
        return jdbc.sql("""
                        select r.code from platform_identity.roles r
                          join platform_identity.account_roles ar on ar.role_id = r.role_id
                         where ar.user_id = :id
                        """)
                .param("id", userId.value()).query(String.class).list().stream().map(RoleCode::new).collect(Collectors.toSet());
    }

    @Override
    public Set<UserId> findEnabledHolders(RoleCode roleCode) {
        return jdbc.sql("""
                        select ar.user_id
                          from platform_identity.account_roles ar
                          join platform_identity.roles r on r.role_id = ar.role_id
                          join platform_identity.user_accounts a on a.user_id = ar.user_id
                         where lower(r.code) = lower(:code) and a.status = 'ENABLED'
                           and exists (select 1 from platform_identity.role_versions v
                                        where v.role_id = r.role_id and v.effective_to_at is null and v.role_status = 'ENABLED')
                        """)
                .param("code", roleCode.value()).query(UUID.class).list().stream().map(UserId::new).collect(Collectors.toSet());
    }
}
