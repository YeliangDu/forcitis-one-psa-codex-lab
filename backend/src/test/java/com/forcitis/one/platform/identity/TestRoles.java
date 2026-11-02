package com.forcitis.one.platform.identity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Roles written straight into platform_identity for a test (ADR-0034 decision 1): the roles row keeps only the identity, the definition is
 * a version in role_versions. A fixture role is written as a MIGRATION version (no person recorded it, no declaration revision), so it needs
 * no account; the audit entry id is a made-up one, as the version table keeps only the id. Clearing custom roles switches the append-only
 * triggers of role_versions off inside the fixture only (as NumberingFixtures does), never in the application.
 */
public final class TestRoles {

    /** The start of every fixture version: before the fixed clock of the tests, so later changes through the application come after it. */
    public static final String FROM = "2026-10-01T00:00:00Z";

    private TestRoles() {
    }

    /** A CUSTOM, non-administrative role with one current version; returns its id. */
    public static UUID insertCustom(JdbcClient jdbc, String code, String name, String status, String accessScope, String... permissionCodes) {
        UUID roleId = jdbc.sql("""
                        insert into platform_identity.roles (role_id, code, kind, is_administrative, created_at, updated_at)
                        values (uuidv7(), :code, 'CUSTOM', false, :from::timestamptz, :from::timestamptz) returning role_id
                        """)
                .param("code", code).param("from", FROM).query(UUID.class).single();
        jdbc.sql("""
                        insert into platform_identity.role_versions (role_version_id, role_id, version_number, effective_from_at, role_name, role_status,
                               access_scope_kind, permission_codes, change_channel_kind, change_reason, version_audit_entry_id, created_at, updated_at)
                        values (uuidv7(), :role, 1, :from::timestamptz, :name, :status, :scope, :codes, 'MIGRATION', 'test fixture', uuidv7(),
                                :from::timestamptz, :from::timestamptz)
                        """)
                .param("role", roleId).param("from", FROM).param("name", name).param("status", status).param("scope", accessScope)
                .param("codes", Arrays.stream(permissionCodes).sorted().distinct().toArray(String[]::new))
                .update();
        return roleId;
    }

    /** The permission codes of the role's current version, in stored (C) order. */
    public static List<String> currentPermissionCodes(JdbcClient jdbc, UUID roleId) {
        return jdbc.sql("select unnest(permission_codes) from platform_identity.role_versions where role_id = :id and effective_to_at is null")
                .param("id", roleId).query(String.class).list();
    }

    /** Takes every custom role and its versions out, so that the next test starts from the system roles alone. */
    /** Fixture clean-up only: the history is append-only for the application, so the guard trigger is off for the delete. */
    public static void deleteAllGrants(JdbcClient jdbc) {
        jdbc.sql("alter table platform_identity.account_role_grants disable trigger user").update();
        try {
            jdbc.sql("delete from platform_identity.account_role_grants").update();
        } finally {
            jdbc.sql("alter table platform_identity.account_role_grants enable trigger user").update();
        }
    }

    public static void deleteCustomRoles(JdbcClient jdbc) {
        jdbc.sql("alter table platform_identity.account_role_grants disable trigger user").update();
        try {
            jdbc.sql("delete from platform_identity.account_role_grants where role_id in (select role_id from platform_identity.roles where kind = 'CUSTOM')").update();
        } finally {
            jdbc.sql("alter table platform_identity.account_role_grants enable trigger user").update();
        }
        jdbc.sql("alter table platform_identity.role_versions disable trigger user").update();
        try {
            jdbc.sql("""
                    delete from platform_identity.role_versions
                     where role_id in (select role_id from platform_identity.roles where kind = 'CUSTOM')
                    """).update();
        } finally {
            jdbc.sql("alter table platform_identity.role_versions enable trigger user").update();
        }
        jdbc.sql("delete from platform_identity.roles where kind = 'CUSTOM'").update();
    }
}
