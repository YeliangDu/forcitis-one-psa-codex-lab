package com.forcitis.one.acceptance.platform.identity.authorization_history;

import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.CHECK_VIOLATION;
import static com.forcitis.one.acceptance.platform.identity.authorization_history.AuthorizationHistoryRows.T0;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 item 1.2 and guard rule 2 (last bullet), ADR-0034-IMPL card row 0034-G2-02 and 7.1 ck_role_versions_permission_codes /
 * is_normalized_code_set: a role version stores its permission set normalised (one dimension, no NULL element, no duplicate, ascending in
 * C order). Each refused array breaks exactly that one rule.
 */
class PermissionCodeSetIT {

    private static final String READ = "platform.identity:role:read";
    private static final String UPDATE = "platform.identity:role:update";

    private AuthorizationHistoryRows db;
    private UUID role;

    @BeforeEach
    void aMigratedDatabaseWithARole() throws SQLException {
        db = new AuthorizationHistoryRows(getClass());
        role = db.role();
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private Object[] version(String codes) {
        return new Object[] {UUID.randomUUID(), role, 1, T0, null, AuthorizationHistoryRows.uniqueName(), codes, UUID.randomUUID(), T0, T0};
    }

    private void refused(String codes) throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, "ck_role_versions_permission_codes", AuthorizationHistoryRows.VERSION_INSERT, version(codes));
    }

    @Test
    @DisplayName("0034-G2-02 permission_codes 含 NULL 元素被 ck_role_versions_permission_codes（23514）拒")
    void aNullElementIsRefused() throws SQLException {
        refused("{NULL}");
        refused("{" + READ + ",NULL}");
        refused("{NULL," + READ + "}");
    }

    @Test
    @DisplayName("0034-G2-02 permission_codes 含重复元素被 ck_role_versions_permission_codes（23514）拒")
    void aDuplicateIsRefused() throws SQLException {
        refused("{" + READ + "," + READ + "}");
        refused("{" + READ + "," + READ + "," + UPDATE + "}");
    }

    @Test
    @DisplayName("0034-G2-02 permission_codes 未按 C 排序升序被 ck_role_versions_permission_codes（23514）拒：降序、以及语言排序会接受而 C 排序不接受的 {a,B}")
    void anArrayNotInAscendingCOrderIsRefused() throws SQLException {
        refused("{" + UPDATE + "," + READ + "}");
        refused("{a.x:r:read,B.x:r:read}");
    }

    @Test
    @DisplayName("0034-G2-02 permission_codes 为二维数组被 ck_role_versions_permission_codes（23514）拒；为 NULL 被非空约束（23502）拒")
    void aTwoDimensionalOrMissingArrayIsRefused() throws SQLException {
        refused("{{" + READ + "},{" + UPDATE + "}}");
        db.refusedAsNull("permission_codes", AuthorizationHistoryRows.VERSION_INSERT, version(null));
    }

    @Test
    @DisplayName("0034-G2-02 空数组、单元素与按 C 排序升序的规范数组（含 {B,a}）通过")
    void normalisedArraysGoThrough() throws SQLException {
        db.accepted(AuthorizationHistoryRows.VERSION_INSERT, version("{}"));
        db.accepted(AuthorizationHistoryRows.VERSION_INSERT, version("{" + READ + "}"));
        db.accepted(AuthorizationHistoryRows.VERSION_INSERT, version("{" + READ + "," + UPDATE + "}"));
        db.accepted(AuthorizationHistoryRows.VERSION_INSERT, version("{B.x:r:read,a.x:r:read}"));
    }

    @Test
    @DisplayName("0034-G2-02 is_normalized_code_set 是 IMMUTABLE STRICT 的 SQL 函数，参数 text[]（卡 7.1）")
    void theNormalisationFunctionIsImmutableAndStrict() throws SQLException {
        assertThat(db.column("""
                select p.provolatile::text || ':' || p.proisstrict || ':' || l.lanname || ':' || pg_get_function_identity_arguments(p.oid)
                  from pg_proc p join pg_namespace n on n.oid = p.pronamespace join pg_language l on l.oid = p.prolang
                 where n.nspname = 'platform_identity' and p.proname = 'is_normalized_code_set'"""))
                .containsExactly("i:true:sql:codes text[]");
    }
}
