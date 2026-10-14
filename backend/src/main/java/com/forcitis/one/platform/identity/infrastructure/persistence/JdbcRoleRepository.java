package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.api.RoleId;
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
                        insert into platform_identity.roles (code, name, kind, status, access_scope, is_administrative, version, event_count)
                        values (:code, :name, :kind, :status, :scope, :administrative, :version, :eventSequence)
                        on conflict do nothing
                        returning role_id
                        """)
                .param("code", role.code().value()).param("name", role.name()).param("kind", role.kind().name())
                .param("status", role.status().name()).param("scope", role.accessScope().name())
                .param("administrative", role.administrative()).param("version", role.versionAfterSave())
                .param("eventSequence", role.eventSequenceAfterSave())
                .query(Long.class).optional();
        if (id.isEmpty()) {
            boolean codeTaken = jdbc.sql("select exists (select 1 from platform_identity.roles where lower(code) = lower(:code))")
                    .param("code", role.code().value()).query(Boolean.class).single();
            throw new RoleRuleException(codeTaken ? RoleViolation.DUPLICATE_CODE : RoleViolation.DUPLICATE_NAME);
        }
        savePermissions(id.get(), role.permissionCodes());
        return role.withId(new RoleId(id.get()));
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
        int updated = jdbc.sql("""
                        update platform_identity.roles
                           set name = :name, status = :status, access_scope = :scope,
                               updated_at = now(), version = :next, event_count = :eventSequence
                         where role_id = :id and version = :version and status = :loadedStatus
                        """)
                .param("name", role.name()).param("status", role.status().name()).param("scope", role.accessScope().name())
                .param("next", role.versionAfterSave()).param("eventSequence", role.eventSequenceAfterSave()).param("id", role.roleId().value()).param("version", role.version())
                .param("loadedStatus", role.loadedStatus().name()).update();
        if (updated != 1) {
            throw staleRole(role);
        }
        savePermissions(role.roleId().value(), role.permissionCodes());
    }

    @Override
    public void delete(Role role) {
        int removed;
        try {
            removed = jdbc.sql("delete from platform_identity.roles where role_id = :id and version = :version")
                    .param("id", role.roleId().value()).param("version", role.version()).update();
        } catch (org.springframework.dao.DataIntegrityViolationException held) {
            throw IdentityError.PLATFORM_IDENTITY_ROLE_IN_USE.refusal("role " + role.roleId().value() + " is still held by accounts");
        }
        if (removed != 1) {
            throw staleRole(role);
        }
    }

    private static DomainException staleRole(Role role) {
        return new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                "role " + role.roleId().value() + " was changed by someone else");
    }

    private void savePermissions(long roleId, Set<String> permissionCodes) {
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
