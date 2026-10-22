package com.forcitis.one.acceptance.organization.administration;

import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.ACTIVITY_TYPES;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.CHECK_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.FOREIGN_KEY_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.RESTRICT_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.SCHEMA;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.SEAL_APPROVERS;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T0;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T1;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.UNIQUE_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.WITHDRAW;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.WITHDRAWER;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.sql;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Card A, employee_activity_type_items: an append-only content table whose rows can only be inserted while their version is PENDING and only
 * under a version of their own configuration set (DDL draft DDL_DRAFT_CARD_A, section 3 and 4).
 */
class ConfigSetItemsIT {

    private static final String ITEMS = SCHEMA + "employee_activity_type_items";
    private static final String INSERT = "insert into " + ITEMS + " (employee_activity_type_item_id, config_set_version_id, config_set_code,"
            + " activity_type_code, activity_type_name, is_enabled, created_at, updated_at) values (?, ?, ?, ?, ?, true, ?, ?)";

    private AdministrationDb db;
    private LocalDate today;
    private UUID set;
    private UUID pending;

    @BeforeEach
    void aPendingVersion() throws SQLException {
        db = new AdministrationDb(getClass());
        today = db.today();
        set = db.configSet(ACTIVITY_TYPES);
        pending = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private AdministrationDb.Sql item(UUID version, String code, String name) {
        return sql(INSERT, db.id(), version, ACTIVITY_TYPES, code, name, T0, T0);
    }

    @Test
    @DisplayName("ADM-A-35 给定待确认版本，当插入一条活动类型条目，则接受，并可按版本读回")
    void anItemIsInsertedWhileTheVersionIsPending() throws SQLException {
        db.exec(item(pending, "ANNIVERSARY_CARD", "Anniversary card"));

        assertThat(db.count("select count(*) from " + ITEMS + " where config_set_version_id = ?", pending)).isEqualTo(1L);
        assertThat(db.strings("select activity_type_name from " + ITEMS + " where activity_type_code = 'ANNIVERSARY_CARD'")).containsExactly("Anniversary card");
    }

    @Test
    @DisplayName("ADM-A-36 给定已确认的版本，当再向它插入条目，则被条目插入守护拒绝")
    void noItemIsInsertedAfterConfirmation() throws SQLException {
        db.item(pending, "ANNIVERSARY_CARD");
        db.confirm(pending, T1);

        db.refusedByRule(RESTRICT_VIOLATION, "items can only be inserted while the configuration version is PENDING", item(pending, "HOLIDAY_EVENT", "Holiday event"));
    }

    @Test
    @DisplayName("ADM-A-37 给定已撤回的版本，当再向它插入条目，则被条目插入守护拒绝")
    void noItemIsInsertedAfterWithdrawal() throws SQLException {
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Not needed", T1, pending));

        db.refusedByRule(RESTRICT_VIOLATION, "items can only be inserted while the configuration version is PENDING", item(pending, "HOLIDAY_EVENT", "Holiday event"));
    }

    @Test
    @DisplayName("ADM-A-38 给定不存在的版本 ID，当插入条目，则被条目插入守护拒绝（版本须存在且待确认）")
    void anItemOfAnUnknownVersionIsRefused() throws SQLException {
        db.refusedByRule(RESTRICT_VIOLATION, "items can only be inserted while the configuration version is PENDING", item(db.id(), "HOLIDAY_EVENT", "Holiday event"));
    }

    @Test
    @DisplayName("ADM-A-39 给定条目声明的配置集代码不是 EMPLOYEE_ACTIVITY_TYPES，当插入，则被 ck_employee_activity_type_items_config_set_code 拒绝")
    void anItemIsFixedToTheActivityTypeConfigSet() throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, "ck_employee_activity_type_items_config_set_code",
                sql(INSERT, db.id(), pending, SEAL_APPROVERS, "HOLIDAY_EVENT", "Holiday event", T0, T0));
    }

    @Test
    @DisplayName("ADM-A-40 给定另一配置集（SEAL_APPROVERS）的待确认版本，当把活动类型条目挂上去，则被 fk_employee_activity_type_items_config_set_version 拒绝")
    void anItemCannotHangUnderAnotherConfigSetsVersion() throws SQLException {
        UUID sealSet = db.configSet(SEAL_APPROVERS);
        UUID sealVersion = db.draft(sealSet, SEAL_APPROVERS, 1, today.plusDays(10));

        db.refusedByConstraint(FOREIGN_KEY_VIOLATION, "fk_employee_activity_type_items_config_set_version", item(sealVersion, "HOLIDAY_EVENT", "Holiday event"));
    }

    @Test
    @DisplayName("ADM-A-41 给定版本里已有 ANNIVERSARY_CARD，当同版本再插同代码，则被 uk 拒绝；换一个版本则接受")
    void anActivityTypeCodeIsUniquePerVersion() throws SQLException {
        db.item(pending, "ANNIVERSARY_CARD");
        db.confirm(pending, T1);
        UUID next = db.draft(set, ACTIVITY_TYPES, 2, today.plusDays(20));
        db.item(next, "HOLIDAY_EVENT");

        db.refusedByConstraint(UNIQUE_VIOLATION, "uk_employee_activity_type_items_version_activity_type_code", item(next, "HOLIDAY_EVENT", "Another name"));
        db.accepted(item(next, "ANNIVERSARY_CARD", "Anniversary card"));
    }

    @Test
    @DisplayName("ADM-A-42 给定条目代码（大写字母开头、2 至 40 位大写字母数字下划线）与名称（1 至 100 字），当越界，则被对应 CHECK 拒绝；边界值接受")
    void codeAndNameFormats() throws SQLException {
        String code = "ck_employee_activity_type_items_activity_type_code";
        String name = "ck_employee_activity_type_items_activity_type_name";

        db.refusedByConstraint(CHECK_VIOLATION, code, item(pending, "lower_case", "Name"));
        db.refusedByConstraint(CHECK_VIOLATION, code, item(pending, "A", "Name"));
        db.refusedByConstraint(CHECK_VIOLATION, code, item(pending, "1ABC", "Name"));
        db.refusedByConstraint(CHECK_VIOLATION, code, item(pending, "A" + "B".repeat(40), "Name"));
        db.refusedByConstraint(CHECK_VIOLATION, name, item(pending, "ANNIVERSARY_CARD", ""));
        db.refusedByConstraint(CHECK_VIOLATION, name, item(pending, "ANNIVERSARY_CARD", "n".repeat(101)));
        db.accepted(item(pending, "AB", "Name"));
        db.accepted(item(pending, "A" + "B".repeat(39), "Name"));
        db.accepted(item(pending, "A_1", "n".repeat(100)));
        db.accepted(item(pending, "ANNIVERSARY_CARD", "年".repeat(100)));
    }

    @Test
    @DisplayName("ADM-A-43 给定条目，当创建时间与更新时间不同，则被 ck_employee_activity_type_items_times 拒绝")
    void anItemIsNeverUpdatedSoTheTimesAreEqual() throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, "ck_employee_activity_type_items_times",
                sql(INSERT, db.id(), pending, ACTIVITY_TYPES, "ANNIVERSARY_CARD", "Anniversary card", T0, T1));
    }

    @Test
    @DisplayName("ADM-A-44 给定待确认版本下已有条目，当 UPDATE、DELETE 或 TRUNCATE，则被只追加触发器拒绝")
    void itemsAreAppendOnly() throws SQLException {
        db.item(pending, "ANNIVERSARY_CARD");

        db.refusedByRule(RESTRICT_VIOLATION, "append-only", sql("update " + ITEMS + " set is_enabled = false where config_set_version_id = ?", pending));
        db.refusedByRule(RESTRICT_VIOLATION, "append-only", sql("delete from " + ITEMS + " where config_set_version_id = ?", pending));
        db.refusedByRule(RESTRICT_VIOLATION, "append-only", sql("truncate table " + ITEMS));
    }
}
