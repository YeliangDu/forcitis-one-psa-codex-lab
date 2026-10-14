package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AccountStatus;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleId;
import com.forcitis.one.platform.identity.domain.UserAccount;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAccountRepository implements AccountRepository {

    private final JdbcClient jdbc;

    JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> find(UserId userId) {
        return jdbc.sql("select user_id, status from platform_identity.user_accounts where user_id = :id")
                .param("id", userId.value())
                .query((rs, n) -> new UserAccount(new UserId(rs.getLong("user_id")), AccountStatus.valueOf(rs.getString("status"))))
                .optional();
    }

    @Override
    public boolean createIfAbsent(UserId userId) {
        return jdbc.sql("insert into platform_identity.user_accounts (user_id) values (:id) on conflict (user_id) do nothing")
                .param("id", userId.value()).update() == 1;
    }

    @Override
    public void changeStatus(UserId userId, AccountStatus status) {
        jdbc.sql("update platform_identity.user_accounts set status = :status, updated_at = now(), version = version + 1 where user_id = :id")
                .param("status", status.name()).param("id", userId.value()).update();
    }

    @Override
    public void recordLogin(UserId userId, Instant at, String address) {
        jdbc.sql("""
                        update platform_identity.user_accounts
                           set last_login_at = :at, last_login_address = :address, updated_at = now()
                         where user_id = :id
                        """)
                .param("at", OffsetDateTime.ofInstant(at, ZoneOffset.UTC)).param("address", address).param("id", userId.value())
                .update();
    }

    @Override
    public List<Role> findEnabledRoles(UserId userId) {
        return RoleRows.select(jdbc, """
                from platform_identity.roles r
                join platform_identity.account_roles ar on ar.role_id = r.role_id
               where ar.user_id = :id and r.status = 'ENABLED'
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
    public void grantRole(UserId userId, RoleId roleId) {
        jdbc.sql("insert into platform_identity.account_roles (user_id, role_id) values (:user, :role) on conflict do nothing")
                .param("user", userId.value()).param("role", roleId.value()).update();
    }

    @Override
    public void replaceRoles(UserId userId, Set<RoleId> roleIds) {
        if (roleIds.isEmpty()) {
            jdbc.sql("delete from platform_identity.account_roles where user_id = :user").param("user", userId.value()).update();
            return;
        }
        jdbc.sql("delete from platform_identity.account_roles where user_id = :user and role_id not in (:roles)")
                .param("user", userId.value()).param("roles", roleIds.stream().map(RoleId::value).toList()).update();
        roleIds.forEach(roleId -> grantRole(userId, roleId));
    }

    @Override
    public Set<UserId> findEnabledHolders(RoleCode roleCode) {
        return jdbc.sql("""
                        select ar.user_id
                          from platform_identity.account_roles ar
                          join platform_identity.roles r on r.role_id = ar.role_id
                          join platform_identity.user_accounts a on a.user_id = ar.user_id
                         where lower(r.code) = lower(:code) and r.status = 'ENABLED' and a.status = 'ENABLED'
                        """)
                .param("code", roleCode.value()).query(Long.class).list().stream().map(UserId::new).collect(Collectors.toSet());
    }
}
