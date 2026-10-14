package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleId;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.RoleRuleException;
import com.forcitis.one.platform.identity.domain.RoleViolation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcRoleRepository implements RoleRepository {

    private final JdbcClient jdbc;

    JdbcRoleRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Role> findById(RoleId roleId) {
        return RoleRows.select(jdbc, "from platform_identity.roles r where r.role_id = :id", Map.of("id", roleId.value()))
                .stream().findFirst();
    }

    @Override
    public Optional<Role> findByCode(RoleCode code) {
        return RoleRows.select(jdbc, "from platform_identity.roles r where lower(r.code) = lower(:code)", Map.of("code", code.value()))
                .stream().findFirst();
    }

    @Override
    public List<Role> findAll() {
        return RoleRows.select(jdbc, "from platform_identity.roles r order by r.code", Map.of());
    }

    @Override
    public Role insert(Role role) {
        Optional<Long> id = jdbc.sql("""
                        insert into platform_identity.roles (code, name, kind, status, access_scope, is_administrative)
                        values (:code, :name, :kind, :status, :scope, :administrative)
                        on conflict do nothing
                        returning role_id
                        """)
                .param("code", role.code().value()).param("name", role.name()).param("kind", role.kind().name())
                .param("status", role.status().name()).param("scope", role.accessScope().name())
                .param("administrative", role.administrative())
                .query(Long.class).optional();
        if (id.isEmpty()) {
            boolean codeTaken = jdbc.sql("select exists (select 1 from platform_identity.roles where lower(code) = lower(:code))")
                    .param("code", role.code().value()).query(Boolean.class).single();
            throw new RoleRuleException(codeTaken ? RoleViolation.DUPLICATE_CODE : RoleViolation.DUPLICATE_NAME);
        }
        replacePermissions(id.get(), role.permissionCodes());
        return new Role(new RoleId(id.get()), role.code(), role.name(), role.kind(), role.status(), role.accessScope(),
                role.administrative(), role.permissionCodes());
    }

    @Override
    public void update(Role role) {
        boolean nameTaken = jdbc.sql("""
                        select exists (select 1 from platform_identity.roles where lower(name) = lower(:name) and role_id <> :id)
                        """)
                .param("name", role.name()).param("id", role.roleId().value()).query(Boolean.class).single();
        if (nameTaken) {
            throw new RoleRuleException(RoleViolation.DUPLICATE_NAME);
        }
        jdbc.sql("""
                        update platform_identity.roles
                           set name = :name, status = :status, access_scope = :scope,
                               updated_at = now(), version = version + 1
                         where role_id = :id
                        """)
                .param("name", role.name()).param("status", role.status().name()).param("scope", role.accessScope().name())
                .param("id", role.roleId().value()).update();
        replacePermissions(role.roleId().value(), role.permissionCodes());
    }

    @Override
    public void delete(RoleId roleId) {
        jdbc.sql("delete from platform_identity.roles where role_id = :id").param("id", roleId.value()).update();
    }

    /** The whole set is replaced: missing links are added, the rest removed (RULE-346). */
    private void replacePermissions(long roleId, Set<String> permissionCodes) {
        if (permissionCodes.isEmpty()) {
            jdbc.sql("delete from platform_identity.role_permissions where role_id = :id").param("id", roleId).update();
            return;
        }
        jdbc.sql("delete from platform_identity.role_permissions where role_id = :id and permission_code not in (:codes)")
                .param("id", roleId).param("codes", List.copyOf(permissionCodes)).update();
        for (String code : permissionCodes) {
            jdbc.sql("""
                            insert into platform_identity.role_permissions (role_id, permission_code) values (:id, :code)
                            on conflict do nothing
                            """)
                    .param("id", roleId).param("code", code).update();
        }
    }
}
