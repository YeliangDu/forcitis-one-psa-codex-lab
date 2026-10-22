package com.forcitis.one.acceptance.organization.administration;

import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.ACTIVITY_TYPES;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.CHECK_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.FOREIGN_KEY_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.RESTRICT_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.SCHEMA;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T0;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T1;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.T2;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.UNIQUE_VIOLATION;
import static com.forcitis.one.acceptance.organization.administration.AdministrationDb.sql;
import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.acceptance.organization.administration.AdministrationDb.TransitionRow;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Card A, state_transitions (ADR-0058, ADR-0075 decisions 1 to 5): which moves each state holder may make (ConfigSetVersion including
 * PENDING to WITHDRAWN, EmployeeActivityRequest), the sequence number rules (the version of the holder's row, gaps allowed), the cause and time
 * columns, and the append-only guard. Each refused statement breaks one rule, except where the test says that several break at once.
 */
class AdministrationStateTransitionsIT {

    private static final String REQUEST = "EmployeeActivityRequest";
    private static final String TRANSITIONS = SCHEMA + "state_transitions";
    private static final String STATUS_OF_AGGREGATE = "ck_state_transitions_status_of_aggregate";

    private AdministrationDb db;
    private UUID holder;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new AdministrationDb(getClass());
        holder = db.id();
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    private TransitionRow version(long sequence, String from, String to) {
        return db.transition(holder).sequence(sequence).move(from, to);
    }

    private TransitionRow request(long sequence, String from, String to) {
        return db.transition(holder).type(REQUEST).sequence(sequence).move(from, to);
    }

    @Test
    @DisplayName("ADM-A-45 给定配置集版本，当写入 ∅→PENDING（序号 1）、PENDING→CONFIRMED、PENDING→WITHDRAWN，则都接受")
    void configSetVersionLegalMoves() throws SQLException {
        db.accepted(version(1, null, "PENDING").statement());
        db.accepted(version(2, "PENDING", "CONFIRMED").statement());
        db.accepted(version(2, "PENDING", "WITHDRAWN").statement());
    }

    @Test
    @DisplayName("ADM-A-46 给定配置集版本，当写入 ∅→CONFIRMED、∅→WITHDRAWN、PENDING→APPROVED、PENDING→CANCELLED、DRAFT→APPROVED，则被 ck_state_transitions_status_of_aggregate 拒绝")
    void configSetVersionIllegalMovesBreakTheStatusPairs() throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, version(1, null, "CONFIRMED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, version(1, null, "WITHDRAWN").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, version(2, "PENDING", "APPROVED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, version(2, "PENDING", "CANCELLED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, version(2, "DRAFT", "APPROVED").statement());
    }

    @Test
    @DisplayName("ADM-A-47 给定配置集版本的终态与回头路，当写入 CONFIRMED→PENDING、CONFIRMED→WITHDRAWN、WITHDRAWN→PENDING、PENDING→PENDING，则被拒绝（同时破多条 CHECK，只断言 SQLSTATE）")
    void configSetVersionTerminalStatesAndSelfMovesAreRefused() throws SQLException {
        db.refusedWith(CHECK_VIOLATION, version(2, "CONFIRMED", "PENDING").statement());
        db.refusedWith(CHECK_VIOLATION, version(2, "CONFIRMED", "WITHDRAWN").statement());
        db.refusedWith(CHECK_VIOLATION, version(2, "WITHDRAWN", "PENDING").statement());
        db.refusedWith(CHECK_VIOLATION, version(2, "PENDING", "PENDING").statement());
    }

    @Test
    @DisplayName("ADM-A-48 给定员工活动申请，当写入 ∅→DRAFT、∅→APPROVED、DRAFT→APPROVED、APPROVED→CONCLUDED、APPROVED→CANCELLED、CONCLUDED→CANCELLED，则都接受")
    void requestLegalMoves() throws SQLException {
        db.accepted(request(1, null, "DRAFT").statement());
        db.accepted(request(1, null, "APPROVED").statement());
        db.accepted(request(2, "DRAFT", "APPROVED").statement());
        db.accepted(request(2, "APPROVED", "CONCLUDED").statement());
        db.accepted(request(2, "APPROVED", "CANCELLED").statement());
        db.accepted(request(3, "CONCLUDED", "CANCELLED").statement());
    }

    @Test
    @DisplayName("ADM-A-49 给定员工活动申请，当写入 CONCLUDED→APPROVED、DRAFT→CONCLUDED、DRAFT→CANCELLED、APPROVED→DRAFT、∅→CONCLUDED、∅→CANCELLED、∅→PENDING，则被 status_of_aggregate 拒绝；CANCELLED 之后任何流转被拒绝")
    void requestIllegalMoves() throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(3, "CONCLUDED", "APPROVED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(2, "DRAFT", "CONCLUDED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(2, "DRAFT", "CANCELLED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(2, "APPROVED", "DRAFT").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(1, null, "CONCLUDED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(1, null, "CANCELLED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(1, null, "PENDING").statement());
        db.refusedWith(CHECK_VIOLATION, request(4, "CANCELLED", "APPROVED").statement());
    }

    @Test
    @DisplayName("ADM-A-50 给定迁入起因（MIGRATION，无命令无事件），当员工活动申请以任一终态或中间态初始化（序号不限），则接受；初始为 PENDING 或原状态非空则被拒绝")
    void migrationMayStartARequestInAnyOfItsFourStates() throws SQLException {
        for (String state : new String[] {"DRAFT", "APPROVED", "CONCLUDED", "CANCELLED"}) {
            db.accepted(request(7, null, state).cause("MIGRATION", null, null, null).statement());
        }
        db.refusedByConstraint(CHECK_VIOLATION, STATUS_OF_AGGREGATE, request(7, null, "PENDING").cause("MIGRATION", null, null, null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_migration", request(7, "DRAFT", "APPROVED").cause("MIGRATION", null, null, null).statement());
    }

    @Test
    @DisplayName("ADM-A-51 给定聚合类型不在 EmployeeActivityRequest / ConfigSetVersion 之内，当写入，则被拒绝")
    void anUnknownHolderTypeIsRefused() throws SQLException {
        db.refusedWith(CHECK_VIOLATION, version(1, null, "PENDING").type("LegalEntity").statement());
    }

    @Test
    @DisplayName("ADM-A-52 给定序号规则，当同持有者序号重复，则被 uk 拒绝；跳号（1→4）、不同持有者同序号、序号 0 接受；负序号、非迁入的初始行序号大于 1 被拒绝")
    void sequenceNumberRules() throws SQLException {
        db.exec(version(1, null, "PENDING").statement());

        db.refusedByConstraint(UNIQUE_VIOLATION, "uk_state_transitions_aggregate_id_transition_sequence_number", version(1, "PENDING", "CONFIRMED").statement());
        db.accepted(version(4, "PENDING", "CONFIRMED").statement());
        db.accepted(db.transition(db.id()).sequence(1).move(null, "PENDING").statement());
        db.accepted(version(0, "PENDING", "CONFIRMED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_transition_sequence_number", version(-1, "PENDING", "CONFIRMED").statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_first", db.transition(db.id()).sequence(2).move(null, "PENDING").statement());
        db.accepted(db.transition(db.id()).sequence(0).move(null, "PENDING").statement());
    }

    @Test
    @DisplayName("ADM-A-53 给定起因与触发列，当命令起因无命令 ID、命令与事件同时给、系统作业带命令 ID、迁入带事件、冲销无被冲销 ID、命令带被冲销 ID，则被拒绝；系统作业带事件或不带触发接受")
    void causeAndTriggerColumns() throws SQLException {
        UUID other = db.id();

        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_trigger", version(1, null, "PENDING").cause("COMMAND", null, null, null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_trigger", version(1, null, "PENDING").cause("COMMAND", null, other, null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_trigger", version(1, null, "PENDING").cause("COMMAND", other, db.id(), null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_trigger", version(1, null, "PENDING").cause("SYSTEM_JOB", other, null, null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_trigger", version(1, null, "PENDING").cause("MIGRATION", null, other, null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_reversal", version(1, null, "PENDING").cause("REVERSAL", other, null, null).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_reversal", version(1, null, "PENDING").cause("COMMAND", other, null, db.id()).statement());
        db.accepted(version(1, null, "PENDING").actorType("SYSTEM").cause("SYSTEM_JOB", null, other, null).statement());
        db.accepted(version(1, null, "PENDING").actorType("SYSTEM").cause("SYSTEM_JOB", null, null, null).statement());
    }

    @Test
    @DisplayName("ADM-A-54 给定操作者类型与起因取值，当取 ROBOT 或 MANUAL，则被各自的 CHECK 拒绝；SYSTEM 接受")
    void actorTypeAndCauseKindValues() throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_actor_type", version(1, null, "PENDING").actorType("ROBOT").statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_cause_kind", version(1, null, "PENDING").cause("MANUAL", null, null, null).statement());
        db.accepted(version(1, null, "PENDING").actorType("SYSTEM").statement());
    }

    @Test
    @DisplayName("ADM-A-55 给定创建时间或更新时间与记录时间不同，当写入，则被 ck_state_transitions_times 拒绝")
    void createdAndUpdatedTimesEqualTheRecordedTime() throws SQLException {
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_times", version(1, null, "PENDING").times(T0, T1, T0).statement());
        db.refusedByConstraint(CHECK_VIOLATION, "ck_state_transitions_times", version(1, null, "PENDING").times(T0, T0, T2).statement());
        db.accepted(version(1, null, "PENDING").times(T1, T1, T1).statement());
    }

    @Test
    @DisplayName("ADM-A-56 给定已有转换，当更正行指向同持有者的那一行，则接受；指向别的持有者或不存在的转换，则被 fk_state_transitions_reverses_transition_id 拒绝")
    void aCorrectionPointsAtATransitionOfTheSameHolder() throws SQLException {
        TransitionRow first = request(1, null, "APPROVED");
        db.exec(first.statement());

        db.accepted(request(2, "APPROVED", "CANCELLED").cause("CORRECTION", null, null, first.id).statement());
        db.refusedByConstraint(FOREIGN_KEY_VIOLATION, "fk_state_transitions_reverses_transition_id",
                db.transition(db.id()).type(REQUEST).sequence(2).move("APPROVED", "CANCELLED").cause("CORRECTION", null, null, first.id).statement());
        db.refusedByConstraint(FOREIGN_KEY_VIOLATION, "fk_state_transitions_reverses_transition_id",
                request(2, "APPROVED", "CANCELLED").cause("CORRECTION", null, null, db.id()).statement());
    }

    @Test
    @DisplayName("ADM-A-57 给定已写入的转换，当任何角色（含属主）UPDATE，则被只追加触发器拒绝")
    void transitionsCannotBeUpdated() throws SQLException {
        TransitionRow row = version(1, null, "PENDING");
        db.exec(row.statement());

        db.refusedByRule(RESTRICT_VIOLATION, "append-only", sql("update " + TRANSITIONS + " set actor_type = 'SYSTEM' where id = ?", row.id));
    }

    @Test
    @DisplayName("ADM-A-58 给定已写入的转换，当非属主角色（已授 DELETE）删除，则被只追加触发器拒绝")
    void aNonOwnerCannotDeleteTransitions() throws SQLException {
        TransitionRow row = version(1, null, "PENDING");
        db.exec(row.statement());
        String role = "probe_nonowner_" + System.nanoTime();
        db.exec("create role " + role);
        db.exec("grant usage on schema organization_administration to " + role);
        db.exec("grant select, delete on " + TRANSITIONS + " to " + role);
        db.exec("set local role " + role);

        db.refusedByRule(RESTRICT_VIOLATION, "append-only", sql("delete from " + TRANSITIONS + " where id = ?", row.id));
    }

    @Test
    @DisplayName("ADM-A-59 给定已写入的转换，当表属主角色删除（保留期清理的唯一通道），则接受")
    void theTableOwnerMayDeleteForRetention() throws SQLException {
        assertThat(db.value(Boolean.class, "select pg_get_userbyid(relowner) = current_user from pg_class where oid = '" + TRANSITIONS + "'::regclass"))
                .as("the probe runs as the table owner").isTrue();
        TransitionRow row = version(1, null, "PENDING");
        db.exec(row.statement());

        db.exec(sql("delete from " + TRANSITIONS + " where id = ?", row.id));

        assertThat(db.count("select count(*) from " + TRANSITIONS + " where id = ?", row.id)).isZero();
    }

    @Test
    @DisplayName("ADM-A-60 给定转换表，当 TRUNCATE（含属主），则被只追加触发器拒绝")
    void transitionsCannotBeTruncated() throws SQLException {
        db.refusedByRule(RESTRICT_VIOLATION, "append-only", sql("truncate table " + TRANSITIONS));
    }

    @Test
    @DisplayName("ADM-A-61 给定版本行 1 经起草、确认、被后继确认关闭，当按序号=版本行 version 写转换，则全部接受，版本 1 的行 version 为 3 而转换止于 2（关闭不写转换，允许跳号）")
    void aClosingBumpsTheVersionRowWithoutATransition() throws SQLException {
        LocalDate today = db.today();
        UUID set = db.configSet(ACTIVITY_TYPES);
        UUID first = db.draft(set, ACTIVITY_TYPES, 1, today.minusDays(30));
        db.exec(db.transition(first).sequence(1).move(null, "PENDING").statement());
        db.item(first, "TYPE_1");
        db.confirm(first, T0);
        db.exec(db.transition(first).sequence(db.versionColumn(Long.class, "version", first)).move("PENDING", "CONFIRMED").statement());

        UUID second = db.draft(set, ACTIVITY_TYPES, 2, today.plusDays(5));
        db.exec(db.transition(second).sequence(1).move(null, "PENDING").statement());
        db.item(second, "TYPE_2");
        db.confirm(second, T1);
        db.exec(db.transition(second).sequence(db.versionColumn(Long.class, "version", second)).move("PENDING", "CONFIRMED").statement());

        assertThat(db.versionColumn(Long.class, "version", first)).isEqualTo(3L);
        assertThat(db.value(Long.class, "select max(transition_sequence_number) from " + TRANSITIONS + " where aggregate_id = ?", first)).isEqualTo(2L);
        assertThat(db.versionColumn(Long.class, "version", second)).isEqualTo(2L);
        assertThat(db.value(Long.class, "select max(transition_sequence_number) from " + TRANSITIONS + " where aggregate_id = ?", second)).isEqualTo(2L);
        assertThat(db.strings("select to_status from " + TRANSITIONS + " where aggregate_id = ? order by transition_sequence_number", first))
                .containsExactly("PENDING", "CONFIRMED");
    }
}
