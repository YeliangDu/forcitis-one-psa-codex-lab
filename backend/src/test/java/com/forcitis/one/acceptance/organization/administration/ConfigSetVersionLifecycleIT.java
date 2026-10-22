package com.forcitis.one.acceptance.organization.administration;

import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.ACTIVITY_TYPES;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.AUTHOR;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.CHECK_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.CONFIRM;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.CONFIRMER;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.DECISION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.EXCLUSION_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.FOREIGN_KEY_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.OWNER_ROLE;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.RESTRICT_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.SCHEMA;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.SEAL_APPROVERS;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T0;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T1;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T2;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.UNIQUE_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.WITHDRAW;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.WITHDRAWER;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.sql;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.organization.administration.AdministrationDb.VersionRow;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Card A, configuration set versions: the PENDING to CONFIRMED / WITHDRAWN life cycle of config_set_versions and the guards on config_sets, as the
 * migrated database enforces them (DDL draft DDL_DRAFT_CARD_A, signed by one-data; ADR-0072 decision 1, ADR-0075). Each refused statement breaks one rule.
 * Dates are relative to the database's own transaction day, because the confirmation rule reads that clock.
 */
class ConfigSetVersionLifecycleIT {

    private static final String TABLE = SCHEMA + "config_set_versions";

    private AdministrationDb db;
    private LocalDate today;
    private UUID set;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new AdministrationDb(getClass());
        today = db.today();
        set = db.configSet(ACTIVITY_TYPES);
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private VersionRow row(int number, LocalDate from) {
        return new VersionRow(db.id(), set, ACTIVITY_TYPES, number, from);
    }

    private UUID draftWithItem(int number, LocalDate from) throws SQLException {
        UUID version = db.draft(set, ACTIVITY_TYPES, number, from);
        db.item(version, "TYPE_" + number);
        return version;
    }

    private static Timestamp at(String instant) {
        return Timestamp.from(Instant.parse(instant));
    }

    @Test
    @DisplayName("ADM-A-01 给定配置集，当插入版本号 1、version 1 的待确认版本，则接受，且状态 PENDING、生效区间开放")
    void aPendingVersionAtVersionOneIsAccepted() throws SQLException {
        UUID version = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));

        assertThat(db.versionColumn(String.class, "confirmation_status", version)).isEqualTo("PENDING");
        assertThat(db.versionColumn(Long.class, "version", version)).isEqualTo(1L);
        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", version)).isNull();
        assertThat(db.versionColumn(UUID.class, "confirmed_by", version)).isNull();
    }

    @Test
    @DisplayName("ADM-A-02 给定新版本行，当 version 不是 1（2 或 0），则被插入守护拒绝")
    void aNewVersionRowMustStartAtVersionOne() throws SQLException {
        db.refusedByRule(CHECK_VIOLATION, "starts at version 1", row(1, today).version(2).statement());
        db.refusedByRule(CHECK_VIOLATION, "starts at version 1", row(1, today).version(0).statement());
    }

    @Test
    @DisplayName("ADM-A-03 给定直接插入已确认行（确认列齐全），当插入，则被拒绝：版本只以待确认插入、由另一人事后确认")
    void aConfirmedRowCannotBeInserted() throws SQLException {
        db.refusedByRule(CHECK_VIOLATION, "inserted PENDING", row(1, today).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).statement());
    }

    @Test
    @DisplayName("ADM-A-04 给定直接插入已撤回行（撤回列齐全），当插入，则被拒绝")
    void aWithdrawnRowCannotBeInserted() throws SQLException {
        db.refusedByRule(CHECK_VIOLATION, "inserted PENDING", row(1, today).status("WITHDRAWN").withdrawn(WITHDRAWER, T0, "Mistaken draft").statement());
    }

    @Test
    @DisplayName("ADM-A-05 给定待确认行带着确认人、决定审计或失效日，当插入，则被拒绝")
    void aPendingRowCannotCarryConfirmationOrClosingData() throws SQLException {
        db.refusedByRule(CHECK_VIOLATION, "inserted PENDING", row(1, today).confirmed(CONFIRMER, T0, DECISION).statement());
        db.refusedByRule(CHECK_VIOLATION, "inserted PENDING", row(1, today).confirmed(null, null, DECISION).statement());
        db.refusedByRule(CHECK_VIOLATION, "inserted PENDING", row(1, today).to(today.plusDays(5)).statement());
    }

    @Test
    @DisplayName("ADM-A-06 给定配置集已有一个待确认版本，当再插入第二个待确认版本，则被 uk_config_set_versions_pending 拒绝；另一配置集不受影响")
    void atMostOnePendingVersionPerConfigSet() throws SQLException {
        db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));

        db.refusedByConstraint(UNIQUE_VIOLATION, "uk_config_set_versions_pending", row(2, today.plusDays(20)).statement());

        UUID otherSet = db.configSet(SEAL_APPROVERS);
        db.accepted(new VersionRow(db.id(), otherSet, SEAL_APPROVERS, 1, today.plusDays(10)).statement());
    }

    @Test
    @DisplayName("ADM-A-07 给定版本号 1 已被（已撤回的）版本占用，当再插入版本号 1，则被 uk 拒绝；版本号 0 与负数被 ck_config_set_versions_version_number 拒绝")
    void versionNumbersAreUniquePerConfigSetAndStartAtOne() throws SQLException {
        UUID first = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Drafted twice", T1, first));

        db.refusedByConstraint(UNIQUE_VIOLATION, "uk_config_set_versions_config_set_id_version_number", row(1, today.plusDays(10)).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_version_number", row(0, today.plusDays(10)).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_version_number", row(-1, today.plusDays(10)).statement());
    }

    @Test
    @DisplayName("ADM-A-08 给定版本行的配置集代码与其配置集不一致，当插入，则被 fk_config_set_versions_config_set 拒绝")
    void aVersionCarriesItsOwnConfigSetCode() throws SQLException {
        db.refusedByConstraint(FOREIGN_KEY_VIOLATION, "fk_config_set_versions_config_set",
                new VersionRow(db.id(), set, SEAL_APPROVERS, 1, today.plusDays(10)).statement());
    }

    @Test
    @DisplayName("ADM-A-09 给定有一条条目的待确认版本，当另一人带决定审计确认，则状态 CONFIRMED、确认列写入、version 2、首个已确认版本区间开放")
    void aPendingVersionWithAnItemIsConfirmedByAnotherPerson() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));

        db.exec(db.confirmation(version, T1, T1));

        assertThat(db.versionColumn(String.class, "confirmation_status", version)).isEqualTo("CONFIRMED");
        assertThat(db.versionColumn(UUID.class, "confirmed_by", version)).isEqualTo(CONFIRMER);
        assertThat(db.versionColumn(UUID.class, "decision_audit_entry_id", version)).isEqualTo(DECISION);
        assertThat(db.versionColumn(Timestamp.class, "confirmed_at", version)).isEqualTo(T1);
        assertThat(db.versionColumn(Long.class, "version", version)).isEqualTo(2L);
        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", version)).isNull();
    }

    @Test
    @DisplayName("ADM-A-10 给定待确认版本，当确认人就是作者，则被 ck_config_set_versions_confirmer_is_not_author 拒绝（四眼）")
    void theConfirmerCannotBeTheAuthor() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));

        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_confirmer_is_not_author", sql(CONFIRM, AUTHOR, T1, DECISION, T1, version));
    }

    @Test
    @DisplayName("ADM-A-11 给定待确认版本，当确认时不带决定审计 ID，则被 ck_config_set_versions_decision_source 拒绝")
    void confirmationNeedsTheDecisionAuditEntry() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));

        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_decision_source", sql(CONFIRM, CONFIRMER, T1, null, T1, version));
    }

    @Test
    @DisplayName("ADM-A-12 给定待确认版本，当确认时有确认人而无确认时间，则被 ck_config_set_versions_confirmed 拒绝")
    void confirmerAndConfirmationTimeGoTogether() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));

        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_confirmed", sql(CONFIRM, CONFIRMER, null, DECISION, T1, version));
    }

    @Test
    @DisplayName("ADM-A-13 给定没有任何条目的待确认版本（别的版本有条目），当确认，则被完整性检查拒绝")
    void aVersionWithoutItemsCannotBeConfirmed() throws SQLException {
        UUID first = draftWithItem(1, today.plusDays(10));
        db.exec(db.confirmation(first, T1, T1));
        UUID empty = db.draft(set, ACTIVITY_TYPES, 2, today.plusDays(20));

        db.refusedByRule(CHECK_VIOLATION, "at least one item", db.confirmation(empty, T2, T2));
    }

    @Test
    @DisplayName("ADM-A-14 给定待确认版本，当同一 UPDATE 还改了生效日、作者，或只改内容而不确认，则被确认触发器拒绝（只放行确认列）")
    void onlyTheConfirmationColumnsMayChange() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));
        String confirmWith = "update " + TABLE + " set confirmation_status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?, decision_audit_entry_id = ?,"
                + " updated_at = ?, version = version + 1, ";

        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED",
                sql(confirmWith + "effective_from_date = effective_from_date + 1 where config_set_version_id = ?", CONFIRMER, T1, DECISION, T1, version));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED",
                sql(confirmWith + "author_id = ? where config_set_version_id = ?", CONFIRMER, T1, DECISION, T1, CONFIRMER, version));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED",
                sql("update " + TABLE + " set effective_from_date = effective_from_date + 1, version = version + 1 where config_set_version_id = ?", version));
    }

    @Test
    @DisplayName("ADM-A-15 给定待确认版本，当确认或撤回时 version 没有增大，则被拒绝")
    void everyChangeMustAdvanceTheVersion() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));

        db.refusedByRule(RESTRICT_VIOLATION, "version must increase", sql("update " + TABLE + " set confirmation_status = 'CONFIRMED', confirmed_by = ?,"
                + " confirmed_at = ?, decision_audit_entry_id = ?, updated_at = ? where config_set_version_id = ?", CONFIRMER, T1, DECISION, T1, version));
        db.refusedByRule(RESTRICT_VIOLATION, "version must increase", sql("update " + TABLE + " set confirmation_status = 'WITHDRAWN', withdrawn_by = ?,"
                + " withdrawn_at = ?, withdraw_reason_text = 'Not needed', updated_at = ? where config_set_version_id = ?", WITHDRAWER, T1, T1, version));
    }

    @Test
    @DisplayName("ADM-A-16 给定配置集还没有任何已确认版本，当首个版本以过去的生效日确认，则接受（生效日不受确认日约束）")
    void theFirstConfirmedVersionMayStartInThePast() throws SQLException {
        UUID version = draftWithItem(1, today.minusDays(30));

        db.exec(db.confirmation(version, T1, T1));

        assertThat(db.versionColumn(String.class, "confirmation_status", version)).isEqualTo("CONFIRMED");
        assertThat(db.versionColumn(LocalDate.class, "effective_from_date", version)).isEqualTo(today.minusDays(30));
    }

    @Test
    @DisplayName("ADM-A-17 给定已有已确认版本，当后继版本的生效日早于库的事务日（昨天）且确认时间谎称更早，则被拒绝：确认日取事务时间，不取 confirmed_at")
    void aSuccessorCannotStartBeforeTheDatabaseDay() throws SQLException {
        db.confirmedVersion(set, 1, today.minusDays(30), T0);
        UUID second = draftWithItem(2, today.minusDays(1));

        db.refusedByRule(CHECK_VIOLATION, "earlier than the confirmation day", db.confirmation(second, at("2000-01-01T00:00:00Z"), T1));
    }

    @Test
    @DisplayName("ADM-A-18 给定已有已确认版本，当后继版本的生效日恰为库的事务日且确认时间谎称在 2099 年，则接受，confirmed_at 照记")
    void aSuccessorStartingTodayIsAcceptedWhateverConfirmedAtSays() throws SQLException {
        db.confirmedVersion(set, 1, today.minusDays(30), T0);
        UUID second = draftWithItem(2, today);
        Timestamp claimed = at("2099-01-01T00:00:00Z");

        db.exec(db.confirmation(second, claimed, T1));

        assertThat(db.versionColumn(String.class, "confirmation_status", second)).isEqualTo("CONFIRMED");
        assertThat(db.versionColumn(Timestamp.class, "confirmed_at", second)).isEqualTo(claimed);
    }

    @Test
    @DisplayName("ADM-A-19 给定版本 1 已撤回，当版本 2 以过去的生效日确认，则接受：已撤回版本不算上一版；其后版本 3 撤回、版本 4 确认时，关闭的仍是已确认的版本 2")
    void withdrawnVersionsAreNotThePreviousVersion() throws SQLException {
        UUID first = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Superseded draft", T1, first));
        UUID second = draftWithItem(2, today.minusDays(10));
        db.exec(db.confirmation(second, T1, T1));
        assertThat(db.versionColumn(String.class, "confirmation_status", second)).isEqualTo("CONFIRMED");

        UUID third = draftWithItem(3, today.plusDays(3));
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Superseded draft", T1, third));
        UUID fourth = draftWithItem(4, today.plusDays(3));
        db.exec(db.confirmation(fourth, T2, T2));

        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", second)).isEqualTo(today.plusDays(3));
    }

    @Test
    @DisplayName("ADM-A-20 给定开放的已确认版本 1，当后继版本 2 确认，则版本 1 在同一语句内被关闭：失效日=后继生效日，version 2→3，updated_at 取后继的")
    void confirmingASuccessorClosesThePredecessor() throws SQLException {
        UUID first = db.confirmedVersion(set, 1, today.minusDays(30), T0);
        UUID second = draftWithItem(2, today.plusDays(5));

        db.exec(db.confirmation(second, T1, T1));

        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", first)).isEqualTo(today.plusDays(5));
        assertThat(db.versionColumn(Long.class, "version", first)).isEqualTo(3L);
        assertThat(db.versionColumn(Timestamp.class, "updated_at", first)).isEqualTo(T1);
        assertThat(db.versionColumn(String.class, "confirmation_status", first)).isEqualTo("CONFIRMED");
        assertThat(db.versionColumn(Timestamp.class, "confirmed_at", first)).isEqualTo(T0);
        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", second)).isNull();
        assertThat(db.versionColumn(Long.class, "version", second)).isEqualTo(2L);
    }

    @Test
    @DisplayName("ADM-A-21 给定已确认版本 1 从将来某日起，当同日修订的版本 2 确认，则版本 1 得到零长区间（失效日=生效日）并被接受")
    void aSameDayRevisionLeavesAZeroLengthPredecessor() throws SQLException {
        UUID first = db.confirmedVersion(set, 1, today.plusDays(5), T0);
        UUID second = draftWithItem(2, today.plusDays(5));

        db.exec(db.confirmation(second, T1, T1));

        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", first)).isEqualTo(today.plusDays(5));
        assertThat(db.versionColumn(LocalDate.class, "effective_from_date", first)).isEqualTo(today.plusDays(5));
        assertThat(db.versionColumn(String.class, "confirmation_status", second)).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("ADM-A-22 给定已确认版本 1 从将来第 5 天起，当后继版本 2 的生效日是第 3 天，则确认被拒绝（关闭日不得早于被关闭版本的生效日）")
    void aSuccessorStartingBeforeThePredecessorIsRefused() throws SQLException {
        db.confirmedVersion(set, 1, today.plusDays(5), T0);
        UUID second = draftWithItem(2, today.plusDays(3));

        db.refusedWith(CHECK_VIOLATION, db.confirmation(second, T1, T1));
    }

    @Test
    @DisplayName("ADM-A-23 给定同一配置集两个区间重叠的已确认行（触发器关闭的库内旁路），当插入，则被 ex_config_set_versions_overlap 拒绝；首尾相接与别的配置集不冲突")
    void confirmedRangesOfOneConfigSetCannotOverlap() throws SQLException {
        assertThat(db.value(Boolean.class, "select rolsuper from pg_roles where rolname = current_user")).as("the probe user can switch triggers off").isTrue();
        UUID otherSet = db.configSet(SEAL_APPROVERS);
        db.exec("set local session_replication_role = replica");
        db.exec(row(1, today).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).to(today.plusDays(10)).statement());

        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_config_set_versions_overlap",
                row(2, today.plusDays(5)).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).statement());
        db.refusedByConstraint(EXCLUSION_VIOLATION, "ex_config_set_versions_overlap",
                row(2, today.minusDays(5)).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).to(today.plusDays(1)).statement());
        db.accepted(row(2, today.plusDays(10)).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).statement());
        db.accepted(new VersionRow(db.id(), otherSet, SEAL_APPROVERS, 1, today).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).statement());
    }

    @Test
    @DisplayName("ADM-A-24 给定触发器关闭的库内旁路，当插入失效日早于生效日的已确认行，或带失效日的待确认行，则分别被区间 CHECK 与 pending_is_open CHECK 拒绝")
    void rangeAndOpenRowChecks() throws SQLException {
        db.exec("set local session_replication_role = replica");

        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_effective_range",
                row(1, today.plusDays(5)).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).to(today.plusDays(4)).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_set_versions_pending_is_open", row(1, today).to(today.plusDays(5)).statement());
        db.accepted(row(1, today.plusDays(5)).status("CONFIRMED").confirmed(CONFIRMER, T0, DECISION).to(today.plusDays(5)).statement());
    }

    @Test
    @DisplayName("ADM-A-25 给定已确认版本，当插入与其区间重叠的待确认版本，则接受：排他约束只管已确认行；待确认版本被撤回后可再起草")
    void pendingAndWithdrawnVersionsAreOutsideTheExclusion() throws SQLException {
        db.confirmedVersion(set, 1, today.minusDays(30), T0);
        UUID second = db.draft(set, ACTIVITY_TYPES, 2, today.minusDays(30));
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Overlapping draft", T1, second));

        db.accepted(row(3, today.minusDays(20)).statement());
    }

    @Test
    @DisplayName("ADM-A-26 给定待确认版本，当另一人带原因撤回，则状态 WITHDRAWN、撤回列写入、version 2、区间仍开放且无确认人")
    void aPendingVersionCanBeWithdrawn() throws SQLException {
        UUID version = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));

        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Drafted by mistake", T1, version));

        assertThat(db.versionColumn(String.class, "confirmation_status", version)).isEqualTo("WITHDRAWN");
        assertThat(db.versionColumn(UUID.class, "withdrawn_by", version)).isEqualTo(WITHDRAWER);
        assertThat(db.versionColumn(Timestamp.class, "withdrawn_at", version)).isEqualTo(T1);
        assertThat(db.versionColumn(String.class, "withdraw_reason_text", version)).isEqualTo("Drafted by mistake");
        assertThat(db.versionColumn(Long.class, "version", version)).isEqualTo(2L);
        assertThat(db.versionColumn(LocalDate.class, "effective_to_date", version)).isNull();
        assertThat(db.versionColumn(UUID.class, "confirmed_by", version)).isNull();
    }

    @Test
    @DisplayName("ADM-A-27 给定待确认版本，当撤回缺原因、缺撤回人、原因带首尾空白、为空或超过 500 字，则被 ck_config_set_versions_withdrawal_columns 拒绝；1 至 500 字（含中文）接受")
    void withdrawalColumnsAndReasonLength() throws SQLException {
        UUID version = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));
        String check = "ck_config_set_versions_withdrawal_columns";

        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, WITHDRAWER, T1, null, T1, version));
        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, null, T1, "Not needed", T1, version));
        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, WITHDRAWER, T1, " Not needed", T1, version));
        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, WITHDRAWER, T1, "Not needed ", T1, version));
        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, WITHDRAWER, T1, "", T1, version));
        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, WITHDRAWER, T1, "x".repeat(501), T1, version));
        db.refusedByConstraint(CHECK_VIOLATION, check, sql(WITHDRAW, WITHDRAWER, T1, "撤".repeat(501), T1, version));
        db.accepted(sql(WITHDRAW, WITHDRAWER, T1, "x", T1, version));
        db.accepted(sql(WITHDRAW, WITHDRAWER, T1, "x".repeat(500), T1, version));
        db.accepted(sql(WITHDRAW, WITHDRAWER, T1, "撤".repeat(500), T1, version));
    }

    @Test
    @DisplayName("ADM-A-28 给定已撤回版本（终态），当再确认、改回待确认或改撤回原因，则被拒绝")
    void aWithdrawnVersionIsTerminal() throws SQLException {
        UUID version = draftWithItem(1, today.plusDays(10));
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Not needed", T1, version));

        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", db.confirmation(version, T2, T2));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", sql("update " + TABLE + " set confirmation_status = 'PENDING',"
                + " withdrawn_by = null, withdrawn_at = null, withdraw_reason_text = null, version = version + 1 where config_set_version_id = ?", version));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", sql("update " + TABLE
                + " set withdraw_reason_text = 'Changed', version = version + 1 where config_set_version_id = ?", version));
    }

    @Test
    @DisplayName("ADM-A-29 给定已确认版本，当撤回、改回待确认、改生效日或改确认人，则被拒绝：已确认内容冻结")
    void aConfirmedVersionIsFrozen() throws SQLException {
        UUID version = db.confirmedVersion(set, 1, today.plusDays(10), T0);

        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", sql(WITHDRAW, WITHDRAWER, T1, "Too late", T1, version));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", sql("update " + TABLE + " set confirmation_status = 'PENDING',"
                + " confirmed_by = null, confirmed_at = null, decision_audit_entry_id = null, version = version + 1 where config_set_version_id = ?", version));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED",
                sql("update " + TABLE + " set effective_from_date = effective_from_date + 1, version = version + 1 where config_set_version_id = ?", version));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED",
                sql("update " + TABLE + " set confirmed_by = ?, version = version + 1 where config_set_version_id = ?", WITHDRAWER, version));
    }

    @Test
    @DisplayName("ADM-A-30 给定版本 1 已撤回，当起草版本 2，则接受（待确认名额已释放）；版本 2 的号不是版本 1 的回收")
    void afterAWithdrawalANewVersionCanBeDrafted() throws SQLException {
        UUID first = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));
        db.exec(sql(WITHDRAW, WITHDRAWER, T1, "Redraft", T1, first));

        UUID second = db.draft(set, ACTIVITY_TYPES, 2, today.plusDays(10));

        assertThat(db.versionColumn(Integer.class, "version_number", second)).isEqualTo(2);
        assertThat(db.versionColumn(String.class, "confirmation_status", first)).isEqualTo("WITHDRAWN");
    }

    @Test
    @DisplayName("ADM-A-31 给定开放的已确认版本，当应用直接 UPDATE 失效日（无论有无待确认后继），则被拒绝：关闭只在后继的确认触发器内发生")
    void closingOutsideTheSuccessorsConfirmationIsRefused() throws SQLException {
        UUID first = db.confirmedVersion(set, 1, today.minusDays(30), T0);
        String close = "update " + TABLE + " set effective_to_date = ?, updated_at = ?, version = version + 1 where config_set_version_id = ?";

        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", sql(close, today.plusDays(5), T1, first));

        draftWithItem(2, today.plusDays(5));
        db.refusedByRule(RESTRICT_VIOLATION, "only PENDING -> CONFIRMED", sql(close, today.plusDays(5), T1, first));
    }

    @Test
    @DisplayName("ADM-A-32 给定任一版本行，当 DELETE 或 TRUNCATE，则被拒绝")
    void versionsCannotBeDeletedOrTruncated() throws SQLException {
        UUID version = db.draft(set, ACTIVITY_TYPES, 1, today.plusDays(10));

        db.refusedByRule(RESTRICT_VIOLATION, "DELETE is not allowed", sql("delete from " + TABLE + " where config_set_version_id = ?", version));
        db.refusedByRule(RESTRICT_VIOLATION, "TRUNCATE is not allowed", sql("truncate table " + TABLE + " cascade"));
    }

    @Test
    @DisplayName("ADM-A-33 给定配置集根行，当只推进 version 与 updated_at，则接受；改负责角色或代码、DELETE、TRUNCATE 被拒绝")
    void configSetsChangeOnlyTheOptimisticLockColumns() throws SQLException {
        String root = SCHEMA + "config_sets";

        db.accepted(sql("update " + root + " set version = version + 1, updated_at = ? where config_set_id = ?", T1, set));
        db.refusedByRule(RESTRICT_VIOLATION, "only the optimistic-lock columns",
                sql("update " + root + " set owner_role_code = 'other_owner_role', version = version + 1 where config_set_id = ?", set));
        db.refusedByRule(RESTRICT_VIOLATION, "only the optimistic-lock columns",
                sql("update " + root + " set config_set_code = 'REMINDER_RULES', version = version + 1 where config_set_id = ?", set));
        db.refusedByRule(RESTRICT_VIOLATION, "DELETE is not allowed", sql("delete from " + root + " where config_set_id = ?", set));
        db.refusedByRule(RESTRICT_VIOLATION, "TRUNCATE is not allowed", sql("truncate table " + root + " cascade"));
    }

    @Test
    @DisplayName("ADM-A-34 给定配置集根表，当插入未知代码、重复代码、不合格式的负责角色代码或负 version，则被对应约束拒绝；2 至 60 位小写角色代码接受")
    void configSetRowChecks() throws SQLException {
        String root = SCHEMA + "config_sets";
        String insert = "insert into " + root + " (config_set_id, config_set_code, owner_role_code, created_at, updated_at, version) values (?, ?, ?, ?, ?, ?)";

        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_sets_config_set_code", sql(insert, db.id(), "OTHER_SET", OWNER_ROLE, T0, T0, 0));
        db.refusedByConstraint(UNIQUE_VIOLATION, "uk_config_sets_config_set_code", sql(insert, db.id(), ACTIVITY_TYPES, OWNER_ROLE, T0, T0, 0));
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_sets_owner_role_code", sql(insert, db.id(), SEAL_APPROVERS, "Bad-Role", T0, T0, 0));
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_sets_owner_role_code", sql(insert, db.id(), SEAL_APPROVERS, "a", T0, T0, 0));
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_sets_owner_role_code", sql(insert, db.id(), SEAL_APPROVERS, "1abc", T0, T0, 0));
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_sets_owner_role_code", sql(insert, db.id(), SEAL_APPROVERS, "a" + "b".repeat(60), T0, T0, 0));
        db.refusedByConstraint(CHECK_VIOLATION, "ck_config_sets_version", sql(insert, db.id(), SEAL_APPROVERS, OWNER_ROLE, T0, T0, -1));

        // the code is unique, and the migration may seed it; the positive boundaries are probed with that one constraint dropped inside the rolled-back transaction
        db.exec("alter table " + root + " drop constraint uk_config_sets_config_set_code");
        db.accepted(sql(insert, db.id(), SEAL_APPROVERS, "ab", T0, T0, 0));
        db.accepted(sql(insert, db.id(), SEAL_APPROVERS, "a" + "b".repeat(59), T0, T0, 0));
        db.accepted(sql(insert, db.id(), SEAL_APPROVERS, "a_1", T0, T0, 0));
    }

    @Test
    @DisplayName("ADM-A-67 给定迁移完成的空库，当查询 config_sets，则有且仅有五行（代码与负责角色按 B31 裁定 §2），id 为 UUID 版本 7、version 为 0，且 config_set_versions 没有任何行")
    void migrationSeedsExactlyFiveConfigSetsAndNoVersions() throws SQLException {
        String root = SCHEMA + "config_sets";

        assertThat(db.strings("select config_set_code || ' -> ' || owner_role_code from " + root + " order by config_set_code")).containsExactly(
                "EMPLOYEE_ACTIVITY_TYPES -> employee_relations_head",
                "IT_SERVICE_CATEGORIES -> it_head",
                "POSITION_ASSET_STANDARDS -> administration_head",
                "REMINDER_RULES -> administration_head",
                "SEAL_APPROVERS -> administration_head");
        assertThat(db.count("select count(*) from " + root + " where substring(config_set_id::text from 15 for 1) = '7'"
                + " and substring(config_set_id::text from 20 for 1) in ('8', '9', 'a', 'b')")).as("ids that are UUID version 7 (RFC variant)").isEqualTo(5);
        assertThat(db.count("select count(distinct config_set_id) from " + root)).isEqualTo(5);
        assertThat(db.count("select count(*) from " + root + " where version = 0")).as("rows at the technical initial version").isEqualTo(5);
        assertThat(db.count("select count(*) from " + TABLE)).as("no seeded versions: every set starts without a confirmed version").isZero();
    }
}
