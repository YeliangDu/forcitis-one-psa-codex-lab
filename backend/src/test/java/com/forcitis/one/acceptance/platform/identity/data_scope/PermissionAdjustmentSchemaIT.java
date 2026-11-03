package com.forcitis.one.acceptance.platform.identity.data_scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * F-1 card (#729) section 7.1: platform_identity.permission_adjustments is exactly what the card declares (columns, the only two
 * defaults, constraint and index names, RESTRICT foreign keys, partial unique indexes, comments on the table and every column), read from
 * the migrated database. Behaviour of each constraint is in PermissionAdjustmentShapeIT and PermissionAdjustmentLifecycleIT; this class pins the shape the card's
 * "only these differences" rule (section 7, gate 1.4 item 1) fixes.
 */
class PermissionAdjustmentSchemaIT {

    private static final String TABLE = PermissionAdjustmentRows.TABLE;

    private CustomOrgUnitRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new CustomOrgUnitRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    @Test
    @DisplayName("F-1 7.1 permission_adjustments 的列、类型、非空与卡逐列一致；只有 version、event_count 默认 0，其余列无默认值（时刻由注入时钟写入）")
    void columnsAreTheCards() throws SQLException {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("permission_adjustment_id", "uuid NOT NULL");
        expected.put("kind", "text NOT NULL");
        expected.put("origin_kind", "text NOT NULL");
        expected.put("status", "text NOT NULL");
        for (String column : new String[] {"user_id", "role_id", "org_unit_id", "trigger_personnel_command_id", "trigger_org_structure_command_id"}) {
            expected.put(column, "uuid");
        }
        expected.put("trigger_field_kind", "text");
        for (String column : new String[] {"before_org_unit_id", "after_org_unit_id", "before_position_id", "after_position_id", "recipient_employee_id",
                "original_permission_adjustment_id"}) {
            expected.put(column, "uuid");
        }
        expected.put("escalation_due_at", "timestamp with time zone NOT NULL");
        expected.put("escalated_at", "timestamp with time zone");
        expected.put("upheld_at", "timestamp with time zone");
        expected.put("upheld_by", "uuid");
        expected.put("uphold_reason", "text");
        expected.put("business_owner_employee_id", "uuid");
        expected.put("uphold_expires_at", "timestamp with time zone");
        expected.put("expired_at", "timestamp with time zone");
        expected.put("is_held_at_expiry", "boolean");
        expected.put("regeneration_decided_at", "timestamp with time zone");
        expected.put("adjusted_at", "timestamp with time zone");
        expected.put("adjusted_by", "uuid");
        expected.put("adjusted_authorization_request_id", "uuid");
        expected.put("adjusted_audit_entry_id", "uuid");
        expected.put("closed_at", "timestamp with time zone");
        expected.put("closure_personnel_command_id", "uuid");
        expected.put("version", "bigint NOT NULL DEFAULT 0");
        expected.put("event_count", "bigint NOT NULL DEFAULT 0");
        expected.put("created_at", "timestamp with time zone NOT NULL");
        expected.put("updated_at", "timestamp with time zone NOT NULL");

        Map<String, String> found = new LinkedHashMap<>();
        for (Object row : db.column("""
                select a.attname || ' ' || format_type(a.atttypid, a.atttypmod) || case when a.attnotnull then ' NOT NULL' else '' end
                       || coalesce(' DEFAULT ' || pg_get_expr(d.adbin, d.adrelid), '')
                  from pg_attribute a left join pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
                 where a.attrelid = ?::regclass and a.attnum > 0 and not a.attisdropped order by a.attnum""", TABLE)) {
            String line = (String) row;
            found.put(line.substring(0, line.indexOf(' ')), line.substring(line.indexOf(' ') + 1));
        }
        // column order is no part of the card
        assertThat(found).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    @DisplayName("F-1 7.1 / 不变量 9–14、19、23、25、28–30、35 约束名集合与卡一致（pk、6 个 fk、uk 原条目、19 个 ck），主键单列；没有权限码或范围列（不变量 31）")
    void constraintsAreTheCards() throws SQLException {
        assertThat(db.column("select conname || ' ' || contype::text from pg_constraint where conrelid = ?::regclass and contype <> 'n' order by conname collate \"C\"", TABLE))
                .containsExactly(
                        "ck_permission_adjustments_adjusted c",
                        "ck_permission_adjustments_assignment_change c",
                        "ck_permission_adjustments_closed c",
                        "ck_permission_adjustments_escalation_due_at c",
                        "ck_permission_adjustments_expiry c",
                        "ck_permission_adjustments_kind c",
                        "ck_permission_adjustments_kind_origin c",
                        "ck_permission_adjustments_not_own c",
                        "ck_permission_adjustments_origin_kind c",
                        "ck_permission_adjustments_original c",
                        "ck_permission_adjustments_recipient c",
                        "ck_permission_adjustments_status c",
                        "ck_permission_adjustments_subject c",
                        "ck_permission_adjustments_trigger_command c",
                        "ck_permission_adjustments_trigger_field_kind c",
                        "ck_permission_adjustments_uphold c",
                        "ck_permission_adjustments_uphold_expires_at c",
                        "ck_permission_adjustments_uphold_reason c",
                        "ck_permission_adjustments_uphold_terms c",
                        "fk_permission_adjustments_adjusted_authorization_request_id f",
                        "fk_permission_adjustments_adjusted_by f",
                        "fk_permission_adjustments_original_permission_adjustment_id f",
                        "fk_permission_adjustments_role_id f",
                        "fk_permission_adjustments_upheld_by f",
                        "fk_permission_adjustments_user_id f",
                        "pk_permission_adjustments p",
                        "uk_permission_adjustments_original_permission_adjustment_id u");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'pk_permission_adjustments'"))
                .containsExactly("PRIMARY KEY (permission_adjustment_id)");
        assertThat(db.column("select pg_get_constraintdef(oid) from pg_constraint where conname = 'uk_permission_adjustments_original_permission_adjustment_id'"))
                .containsExactly("UNIQUE (original_permission_adjustment_id)");
    }

    @Test
    @DisplayName("F-1 7.1 / B4 / 不变量 36 外键只指向本 schema 的账号、角色、授权申请与自身，全部 ON DELETE RESTRICT；部门、岗位、员工、人事命令、组织结构命令、审计条目跨 schema 不建外键")
    void foreignKeysAreTheCardsAndRestrict() throws SQLException {
        assertThat(db.column("""
                select conname || ' (' || (select string_agg(attname, ',') from pg_attribute where attrelid = conrelid and attnum = any(conkey)) || ') -> '
                       || confrelid::regclass::text || ' ' || confdeltype::text
                  from pg_constraint where conrelid = ?::regclass and contype = 'f' order by conname collate "C"
                """, TABLE)).containsExactly(
                "fk_permission_adjustments_adjusted_authorization_request_id (adjusted_authorization_request_id) -> platform_identity.authorization_requests r",
                "fk_permission_adjustments_adjusted_by (adjusted_by) -> platform_identity.user_accounts r",
                "fk_permission_adjustments_original_permission_adjustment_id (original_permission_adjustment_id) -> platform_identity.permission_adjustments r",
                "fk_permission_adjustments_role_id (role_id) -> platform_identity.roles r",
                "fk_permission_adjustments_upheld_by (upheld_by) -> platform_identity.user_accounts r",
                "fk_permission_adjustments_user_id (user_id) -> platform_identity.user_accounts r");
    }

    @Test
    @DisplayName("F-1 7.1 / O10 索引名集合与卡一致；两个去重部分唯一索引（不变量 15、17）与四个部分索引的列与条件照卡")
    void indexesAreTheCards() throws SQLException {
        assertThat(db.column("select c.relname::text from pg_index i join pg_class c on c.oid = i.indexrelid where i.indrelid = ?::regclass order by c.relname::text collate \"C\"", TABLE))
                .containsExactly(
                        "ix_permission_adjustments_adjusted_authorization_request_id",
                        "ix_permission_adjustments_adjusted_by",
                        "ix_permission_adjustments_created_at",
                        "ix_permission_adjustments_escalation_due_at",
                        "ix_permission_adjustments_recipient_employee_id",
                        "ix_permission_adjustments_regeneration_pending",
                        "ix_permission_adjustments_role_id",
                        "ix_permission_adjustments_upheld_by",
                        "ix_permission_adjustments_uphold_expires_at",
                        "ix_permission_adjustments_user_id",
                        "pk_permission_adjustments",
                        "uk_permission_adjustments_assignment_trigger",
                        "uk_permission_adjustments_inactive_org_unit",
                        "uk_permission_adjustments_original_permission_adjustment_id");
        assertThat(indexDef("uk_permission_adjustments_assignment_trigger")).isEqualTo(
                "CREATE UNIQUE INDEX uk_permission_adjustments_assignment_trigger ON platform_identity.permission_adjustments USING btree"
                        + " (kind, user_id, trigger_personnel_command_id, trigger_field_kind) WHERE (origin_kind = 'ASSIGNMENT_CHANGE'::text)");
        assertThat(indexDef("uk_permission_adjustments_inactive_org_unit")).isEqualTo(
                "CREATE UNIQUE INDEX uk_permission_adjustments_inactive_org_unit ON platform_identity.permission_adjustments USING btree (role_id, org_unit_id)"
                        + " WHERE ((origin_kind = 'ORG_UNIT_INACTIVE'::text) AND (status = ANY (ARRAY['OPEN'::text, 'UPHELD'::text])))");
        assertThat(indexDef("ix_permission_adjustments_created_at")).isEqualTo(
                "CREATE INDEX ix_permission_adjustments_created_at ON platform_identity.permission_adjustments USING btree"
                        + " (created_at DESC, permission_adjustment_id DESC)");
        assertThat(indexDef("ix_permission_adjustments_recipient_employee_id")).isEqualTo(
                "CREATE INDEX ix_permission_adjustments_recipient_employee_id ON platform_identity.permission_adjustments USING btree"
                        + " (recipient_employee_id, created_at DESC, permission_adjustment_id DESC) WHERE (recipient_employee_id IS NOT NULL)");
        assertThat(indexDef("ix_permission_adjustments_uphold_expires_at")).isEqualTo(
                "CREATE INDEX ix_permission_adjustments_uphold_expires_at ON platform_identity.permission_adjustments USING btree (uphold_expires_at)"
                        + " WHERE (status = 'UPHELD'::text)");
        assertThat(indexDef("ix_permission_adjustments_escalation_due_at")).isEqualTo(
                "CREATE INDEX ix_permission_adjustments_escalation_due_at ON platform_identity.permission_adjustments USING btree (escalation_due_at)"
                        + " WHERE ((status = 'OPEN'::text) AND (escalated_at IS NULL))");
        assertThat(indexDef("ix_permission_adjustments_regeneration_pending")).isEqualTo(
                "CREATE INDEX ix_permission_adjustments_regeneration_pending ON platform_identity.permission_adjustments USING btree (expired_at)"
                        + " WHERE ((status = 'EXPIRED'::text) AND (regeneration_decided_at IS NULL))");
    }

    @Test
    @DisplayName("F-1 7.1 表与表上每一列都有非空白的中文注释（DdlNamingTest 同口径）")
    void theTableAndEveryColumnAreCommented() throws SQLException {
        assertThat(db.column("select obj_description(?::regclass, 'pg_class')", TABLE)).singleElement().asString().isNotBlank();
        assertThat(db.column("""
                select a.attname from pg_attribute a where a.attrelid = ?::regclass and a.attnum > 0 and not a.attisdropped
                   and coalesce(btrim(col_description(a.attrelid, a.attnum)), '') = ''""", TABLE)).as("columns without a comment").isEmpty();
    }

    private String indexDef(String index) throws SQLException {
        return (String) db.column("select pg_get_indexdef(?::regclass)", "platform_identity." + index).getFirst();
    }
}
