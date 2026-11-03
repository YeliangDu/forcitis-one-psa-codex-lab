package com.forcitis.one.acceptance.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * F-1 card (#729) section 7.2: the audit subject kind PERMISSION_ADJUSTMENT is registered for platform.identity, with an object id, in use.
 * The card says "add a value to ck_audit_entries_subject_kind"; since ADR-0069 item 14 the kinds are rows of audit_subject_kinds, so the
 * row is what is asserted. Lives with the audit module because it reads the audit schema.
 */
class PermissionAdjustmentSubjectKindIT {

    @Test
    @DisplayName("F-1 7.2 审计对象类别 PERMISSION_ADJUSTMENT 已登记：归 platform.identity、有对象 ID、在用、未退休（ADR-0069 第 14 条）")
    void theSubjectKindIsRegistered() throws SQLException {
        TestDatabase database = TestPostgres.database(PermissionAdjustmentSubjectKindIT.class, TestPostgres.MIGRATED);
        try (Connection connection = DriverManager.getConnection(database.url(), database.username(), database.password());
                PreparedStatement select = connection.prepareStatement("""
                        select owner_bc_code || ' ' || has_object::text || ' ' || status || ' ' || (retired_date is null)::text
                          from platform_audit.audit_subject_kinds where code = 'PERMISSION_ADJUSTMENT'""");
                ResultSet rs = select.executeQuery()) {
            assertThat(rs.next()).as("a registered PERMISSION_ADJUSTMENT row").isTrue();
            assertThat(rs.getString(1)).isEqualTo("platform.identity true ACTIVE true");
            assertThat(rs.next()).isFalse();
        }
    }
}
