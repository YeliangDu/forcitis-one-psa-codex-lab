package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.integration.api.WecomCard;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #896 IMP-1030-FC-E acceptance (recipients of levels 2 and 3: user answers A a and B a of 2026-11-02, recorded on #896) (ADR-0073 item 3.6; note legal-entity-countersign.md sections 4, 6 and 10 item 11; one-design rulings on #896
 * comment 6589848860): the deadline, reminder and escalation job. Open countersign rows (CHANGE and WITHDRAWAL) are seeded through the #893
 * schema with a stored due_on_date; the job (AttributionCountersignDeadlineJob#runOnce, driven at a set clock) raises a row to the highest
 * level due on the business date T, strictly after: level 1 when T is after the due date, level 2 when T is after the 2nd working day after
 * it, level 3 after the 4th; no level 4. One run sends one WeCom notice per row for the level it reached (a missed stretch is caught up in
 * one step), a repeated run sends nothing. Working days come from the row's legal entity calendar; a year that is not maintained counts
 * Monday to Friday and the run still completes. Decided and LAPSED rows are never touched, and the job never decides anything.
 *
 * <p>Fixed dates (TODAY is Monday 2026-11-02): the 2026 calendar has a holiday on Tuesday 2026-11-10; 2027 is not maintained. Rows are
 * requested on 11-02 but carry the stored due date 11-06, not the 11-05 the entry rule would give: on purpose, the job reads the stored
 * due_on_date and never recomputes it (ruling item 6; due_on_date at entry is #895's).
 */
class CountersignDeadlineJobIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(CountersignDeadlineJobIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // Names of the one-design ruling on #896 and the #893 DD (table, columns, status).
    static final String JOB_TYPE = "com.forcitis.one.organization.workforce.application.AttributionCountersignDeadlineJob";
    static final String RUN_METHOD = "runOnce";
    static final String SUBJECT_KIND = "ATTRIBUTION_COUNTERSIGN";
    static final String COUNTERSIGNS = "attribution_countersigns";
    static final String AWAITING = "AWAITING_ATTRIBUTION_COUNTERSIGN";
    /** one-design naming ruling on #893 (comment 6589791372, item 1) and the #896 ruling item 2. */
    static final String LEVEL = "escalation_level_number";

    private static final LocalDate DUE = LocalDate.of(2026, 11, 6);
    private static final LocalDate HOLIDAY = LocalDate.of(2026, 11, 10);

    @Autowired
    private ApplicationContext applicationContext;

    private OffsetDateTime at;
    private UUID e2;
    private Staff orgAdmin;
    private UUID headE1;

    @BeforeEach
    void aCalendarTwoEntitiesAndAHead() throws Exception {
        at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        orgAdmin = staff("org_data_admin");
        maintain2026WithAHoliday(orgAdmin);
        e2 = createLegalEntity(orgAdmin.session(), newLegalEntityCode("DJ"), "Synthetic deadline entity two");
        headE1 = employed(REGULAR);
        bindFinanceHead(headE1, legalEntity, TODAY);
        bindFinanceHead(employed(REGULAR), e2, TODAY);
        Mockito.clearInvocations(wecomGateway);
    }

    @AfterEach
    void putTheClockBack() {
        setToday(TODAY);
    }

    // ---- world ----

    /** The default calendar of 2026 with one holiday, through the attendance endpoint; 2027 stays unmaintained. */
    private void maintain2026WithAHoliday(Staff maintainer) throws Exception {
        String year = "/api/v1/organization/work-calendars/2026";
        if (mvc.perform(get(year).with(session(maintainer.session()))).andReturn().getResponse().getStatus() == 200) {
            return;
        }
        MockHttpServletResponse maintained = mvc.perform(put(year).header("If-Match", "\"0\"").with(csrf()).with(session(maintainer.session()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"days\":[{\"calendarDate\":\"" + HOLIDAY + "\",\"dayKind\":\"HOLIDAY\"}],\"lockedDaysConfirmed\":true}"))
                .andReturn().getResponse();
        assertThat(maintained.getStatus()).as("maintaining 2026: " + maintained.getContentAsString()).isEqualTo(200);
    }

    /** legal_entity_versions is close-and-append (#893 DD amendment 2): the open version closes on the day and a copy naming the head is appended. */
    private void bindFinanceHead(UUID head, UUID entity, LocalDate from) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Map<String, Object> open = query(workforce, "select * from legal_entity_versions where legal_entity_id = ? and effective_to_date is null", entity).get(0);
            update(workforce, "update legal_entity_versions set effective_to_date = ? where legal_entity_version_id = ?", from, open.get("legal_entity_version_id"));
            update(workforce, "insert into legal_entity_versions (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, code, name, status,"
                    + " finance_head_employee_id) values (?, ?, ?, ?, ?, ?, ?, ?)", Uuids.timeOrdered(NOW), entity, from, ((Number) open.get("revision_number")).intValue() + 1,
                    open.get("code"), open.get("name"), open.get("status"), stored(head));
        }
    }

    /** A CHANGE_LEGAL_ENTITY of a new employee from E1 to E2, awaiting countersign; drafted by the 人事专员. */
    private UUID awaitingCommand() throws Exception {
        UUID employee = employed(REGULAR);
        UUID command = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into personnel_commands (personnel_command_id, employee_id, command_kind, effective_date, status, target_legal_entity_id,
                      countersign_previous_legal_entity_id, countersign_new_legal_entity_id, created_at, created_by, updated_at)
                    values (?, ?, 'CHANGE_LEGAL_ENTITY', ?, 'AWAITING_ATTRIBUTION_COUNTERSIGN', ?, ?, ?, ?, ?, ?)""",
                    command, stored(employee), TODAY.plusDays(30), e2, legalEntity, e2, at, hr.userId(), at);
        }
        return command;
    }

    /** A CHANGE_LEGAL_ENTITY from E1 to E2 that was countersigned (both CHANGE rows APPROVED) and is PENDING: a withdrawal request follows such a command. */
    private UUID countersignedCommand() throws Exception {
        UUID command = awaitingCommand();
        for (UUID entity : List.of(legalEntity, e2)) {
            decide(openRow(command, "CHANGE", entity, DUE.minusDays(10)), "APPROVED", null);
        }
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update personnel_commands set status = 'PENDING', updated_at = ? where personnel_command_id = ?", at, command);
        }
        return command;
    }

    /** An open request row of the command for the entity with the stored due date (requested by the drafter, sequence 1). */
    private UUID openRow(UUID command, String kind, UUID entity, LocalDate due) throws SQLException {
        UUID id = Uuids.timeOrdered(NOW);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Object employee = query(workforce, "select employee_id from personnel_commands where personnel_command_id = ?", command).get(0).get("employee_id");
            update(workforce, """
                    insert into attribution_countersigns (attribution_countersign_id, personnel_command_id, employee_id, drafted_by, request_kind,
                      request_sequence_number, legal_entity_id, requested_at, requested_by, due_on_date, created_at, updated_at, request_reason)
                    values (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)""",
                    id, command, employee, hr.userId(), kind, entity, at, hr.userId(), due, at, at, "WITHDRAWAL".equals(kind) ? "synthetic" : null);
        }
        return id;
    }

    private UUID openChangeRow(LocalDate due) throws Exception {
        return openRow(awaitingCommand(), "CHANGE", legalEntity, due);
    }

    private void decide(UUID row, String kind, String reason) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, "update " + COUNTERSIGNS + " set decision_kind = ?, decided_at = ?, decided_by = ?, decision_reason = ?, decision_audit_entry_id = ?, updated_at = ?"
                    + " where attribution_countersign_id = ?", kind, at, "LAPSED".equals(kind) ? null : orgAdmin.userId(), reason, Uuids.timeOrdered(NOW), at, row);
        }
    }

    // ---- the job ----

    private void runOn(LocalDate day) {
        setToday(day);
        Object job;
        try {
            job = applicationContext.getBean(Class.forName(JOB_TYPE));
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("one-design ruling #896 item 1: no class " + JOB_TYPE, missing);
        }
        try {
            job.getClass().getMethod(RUN_METHOD).invoke(job);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("the run must complete (#896 ruling items 1 and 5)", failure.getCause());
        } catch (ReflectiveOperationException broken) {
            throw new AssertionError(JOB_TYPE + " has no public " + RUN_METHOD + "()", broken);
        }
    }

    private int levelOf(UUID row) throws SQLException {
        return ((Number) rows("select " + LEVEL + " from " + COUNTERSIGNS + " where attribution_countersign_id = ?", row).get(0).get(LEVEL)).intValue();
    }

    private Map<String, Object> row(UUID row) throws SQLException {
        return rows("select * from " + COUNTERSIGNS + " where attribution_countersign_id = ?", row).get(0);
    }

    private List<WecomCard> cards() {
        ArgumentCaptor<WecomCard> captured = ArgumentCaptor.forClass(WecomCard.class);
        Mockito.verify(wecomGateway, Mockito.atLeast(0)).send(captured.capture());
        return captured.getAllValues();
    }

    /** The notices about the row: subject kind ATTRIBUTION_COUNTERSIGN with the row as the subject id. */
    private List<WecomCard> noticesOf(UUID row) {
        List<WecomCard> found = new ArrayList<>();
        for (WecomCard card : cards()) {
            if (card.subject().id().equals(row)) {
                assertThat(card.subject().kind().toString()).as("ruling item 3: subject kind").isEqualTo(SUBJECT_KIND);
                found.add(card);
            }
        }
        return found;
    }

    private static String purposeOf(WecomCard card) {
        return card.subject().purpose().toString();
    }

    private static List<String> recipientsOf(WecomCard card) {
        return card.wecomUserIds().stream().map(id -> id.value()).sorted().toList();
    }

    // ---- cases ----

    @Test
    @DisplayName("#896 ADR-0073 第 3 条第 6 款、one-design 裁定第 2、3 项：到期日当天不提醒（「之后」为严格）；到期日之后第一天升到第 1 级并给该法人今天版本的财务负责人发一条提醒（主体为该会签行）；同一天再跑不再发")
    void levelOneTheDayAfterTheDueDate() throws Exception {
        UUID row = openChangeRow(DUE);

        runOn(DUE);
        assertThat(levelOf(row)).isZero();
        assertThat(noticesOf(row)).isEmpty();

        runOn(DUE.plusDays(1));
        assertThat(levelOf(row)).isEqualTo(1);
        assertThat(noticesOf(row)).singleElement().satisfies(card -> assertThat(recipientsOf(card)).containsExactly(wecomUserIdOf(headE1)));

        runOn(DUE.plusDays(1));
        assertThat(noticesOf(row)).as("a repeated run sends nothing").hasSize(1);
        assertThat(levelOf(row)).isEqualTo(1);
    }

    @Test
    @DisplayName("#896 one-design 裁定第 2 项（工作日按法人日历，节假日不算）：到期日 11-06（周五）后的工作日是 11-09、11-11（11-10 为节假日）、11-12、11-13：11-11 仍是第 1 级；11-12 升第 2 级；11-13 不变；11-14 升第 3 级；第 1、3 级各发一条提醒且用途不同（第 2 级的提醒见其收件人用例）；之后（12-31）没有第 4 级、不再提醒")
    void levelsTwoAndThreeCountWorkingDaysOfTheCalendar() throws Exception {
        UUID row = openChangeRow(DUE);

        String levelOne = purposeOf(noticeOnRun(row, DUE.plusDays(1)));
        runOn(LocalDate.of(2026, 11, 11));
        assertThat(levelOf(row)).as("the holiday 11-10 does not count, so 11-11 is only the 2nd working day").isEqualTo(1);

        runOn(LocalDate.of(2026, 11, 12));
        assertThat(levelOf(row)).isEqualTo(2);
        runOn(LocalDate.of(2026, 11, 13));
        assertThat(levelOf(row)).isEqualTo(2);
        String levelThree = purposeOf(noticeOnRun(row, LocalDate.of(2026, 11, 14)));
        assertThat(levelOf(row)).isEqualTo(3);
        assertThat(levelThree).as("each level its own purpose_kind").isNotEqualTo(levelOne);
        Mockito.clearInvocations(wecomGateway);
        runOn(LocalDate.of(2026, 12, 31));
        assertThat(levelOf(row)).as("no level 4").isEqualTo(3);
        assertThat(noticesOf(row)).as("nothing after level 3").isEmpty();
    }

    @Test
    @DisplayName("#896 one-design 裁定第 2 项（补跑一步到位）：一行停在第 0 级、作业多日未跑，到 11-16 跑一次直接升到第 3 级，只发一条第 3 级的提醒（用途与逐级跑到第 3 级时相同）")
    void aMissedStretchIsCaughtUpInOneStep() throws Exception {
        UUID stepped = openChangeRow(DUE);
        for (LocalDate day : List.of(DUE.plusDays(1), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14))) {
            runOn(day);
        }
        assertThat(levelOf(stepped)).isEqualTo(3);
        String levelThreePurpose = purposeOf(noticesOf(stepped).getLast());
        decide(stepped, "LAPSED", null);
        setToday(TODAY);
        UUID late = openChangeRow(DUE);
        Mockito.clearInvocations(wecomGateway);

        runOn(LocalDate.of(2026, 11, 16));

        assertThat(levelOf(late)).isEqualTo(3);
        assertThat(noticesOf(late)).singleElement().satisfies(card -> assertThat(purposeOf(card)).isEqualTo(levelThreePurpose));
        assertThat(noticesOf(stepped)).as("a lapsed row gets nothing").isEmpty();
    }

    @Test
    @DisplayName("#896 one-design 裁定第 6 项：撤回请求（WITHDRAWAL）的未决行同样逐级提醒；已批准、已拒签、已失效的行（到期日早已过去）从不被改动、从不提醒；作业从不做决定：升到第 3 级后请求仍未决、命令仍待会签")
    void withdrawalRowsCountAndDecidedRowsAreNeverTouched() throws Exception {
        UUID withdrawal = openRow(countersignedCommand(), "WITHDRAWAL", e2, DUE);
        UUID approved = openChangeRow(DUE.minusDays(20));
        UUID rejected = openChangeRow(DUE.minusDays(20));
        UUID lapsed = openChangeRow(DUE.minusDays(20));
        decide(approved, "APPROVED", null);
        decide(rejected, "REJECTED", "synthetic: not this entity");
        decide(lapsed, "LAPSED", null);
        UUID open = openChangeRow(DUE);
        UUID openCommand = (UUID) row(open).get("personnel_command_id");

        runOn(LocalDate.of(2026, 11, 16));

        assertThat(levelOf(withdrawal)).isEqualTo(3);
        assertThat(noticesOf(withdrawal)).hasSize(1);
        for (UUID decided : List.of(approved, rejected, lapsed)) {
            assertThat(levelOf(decided)).as("decided row " + decided).isZero();
            assertThat(noticesOf(decided)).isEmpty();
        }
        assertThat(row(open).get("decision_kind")).as("never auto-approve or auto-withdraw").isNull();
        assertThat(command(openCommand).get("status")).isEqualTo(AWAITING);
    }

    @Test
    @DisplayName("#896 one-design 裁定第 3 项（今天版本的财务负责人）：E1 的负责人从今天起换人后，第 1 级提醒只发给当天在任的新负责人，不发给前任，也不发给日后才接任的人；E2 的行发给 E2 的负责人")
    void theReminderGoesToTodaysFinanceHead() throws Exception {
        UUID successor = employed(REGULAR);
        UUID later = employed(REGULAR);
        UUID rowE1 = openChangeRow(DUE);
        UUID rowE2 = openRow(awaitingCommand(), "CHANGE", e2, DUE);
        setToday(DUE.plusDays(1));
        bindFinanceHead(successor, legalEntity, DUE.plusDays(1));
        bindFinanceHead(later, legalEntity, DUE.plusDays(3));
        UUID headE2 = fromStored(rows("select finance_head_employee_id from legal_entity_versions where legal_entity_id = ? and effective_to_date is null", e2)
                .get(0).get("finance_head_employee_id"));

        runOn(DUE.plusDays(1));

        assertThat(noticesOf(rowE1)).as("the head in force on the day, not the newest version").singleElement()
                .satisfies(card -> assertThat(recipientsOf(card)).containsExactly(wecomUserIdOf(successor)));
        assertThat(noticesOf(rowE2)).singleElement().satisfies(card -> assertThat(recipientsOf(card)).containsExactly(wecomUserIdOf(headE2)));
    }

    @Test
    @DisplayName("#896 ADR-0073 第 3 条第 6 款「次年日历未确认按周一至周五计、不让作业失败」、one-design 裁定第 5 项：到期日 2026-12-30，2027 年未维护：12-31 后的工作日按周一至周五算（2027-01-01 周五算工作日），所以 2027-01-02 跑一次即升到第 2 级；作业正常结束、不跳过该行，并有 ALERT 告警行")
    void anUnmaintainedYearCountsMondayToFridayAndTheRunCompletes() throws Exception {
        grantByOperations(employed(REGULAR), "business_admin");
        UUID row = openChangeRow(LocalDate.of(2026, 12, 30));
        int mark = logMark();

        runOn(LocalDate.of(2027, 1, 1));
        assertThat(alertsSince(mark)).as("the unmaintained year raises the ADR-0072 item 6 alert (ADR-0073 line 74 「并告警」)").isNotEmpty();
        assertThat(levelOf(row)).isEqualTo(1);

        runOn(LocalDate.of(2027, 1, 2));
        assertThat(levelOf(row)).as("12-31 and 2027-01-01 (Mon–Fri fallback) are the two working days after the due date").isEqualTo(2);
        assertThat(noticesOf(row)).hasSize(2);
    }

    /** The notice of the row sent on the run of the day, after the cards of earlier runs were forgotten. */
    private WecomCard noticeOnRun(UUID row, LocalDate day) {
        Mockito.clearInvocations(wecomGateway);
        runOn(day);
        List<WecomCard> sent = noticesOf(row);
        assertThat(sent).as("one notice on " + day).hasSize(1);
        return sent.get(0);
    }

    @Test
    @DisplayName("#896 用户答复 A（a，2026-11-02，#896 记录）：第 2 级提醒发给每一位业务管理员角色的持有人（不按日期切换；迁入业务负责人授权尚无，等迁移卡），不发给该法人的财务负责人，也不发给起草人；同级再跑不再发")
    void levelTwoGoesToTheBusinessAdministrators() throws Exception {
        UUID first = employed(REGULAR);
        UUID second = employed(REGULAR);
        grantByOperations(first, "business_admin");
        grantByOperations(second, "business_admin");
        UUID row = openChangeRow(DUE);
        runOn(DUE.plusDays(1));

        Mockito.clearInvocations(wecomGateway);
        runOn(LocalDate.of(2026, 11, 12));

        assertThat(levelOf(row)).isEqualTo(2);
        List<WecomCard> levelTwo = noticesOf(row);
        assertThat(levelTwo.stream().map(CountersignDeadlineJobIT::purposeOf).distinct()).as("all of one purpose, the level-2 one").hasSize(1);
        List<String> reached = levelTwo.stream().flatMap(card -> recipientsOf(card).stream()).toList();
        assertThat(reached).as("every business_admin holder is notified (one-design amendment, #896 comment 6590086619)")
                .contains(wecomUserIdOf(first), wecomUserIdOf(second)).doesNotContain(wecomUserIdOf(headE1), wecomUserIdOf(hr.employeeId()));
        Mockito.clearInvocations(wecomGateway);
        runOn(LocalDate.of(2026, 11, 13));
        assertThat(noticesOf(row)).as("a later run at the same level notifies nobody again").isEmpty();
    }

    @Test
    @DisplayName("#896 用户答复 B（a，2026-11-02，#896 记录）：第 3 级提醒再发给该法人今天版本的财务负责人（没有集团级财务角色），只发给他")
    void levelThreeGoesToTheFinanceHeadAgain() throws Exception {
        UUID row = openChangeRow(DUE);
        runOn(DUE.plusDays(1));
        runOn(LocalDate.of(2026, 11, 12));

        WecomCard levelThree = noticeOnRun(row, LocalDate.of(2026, 11, 14));

        assertThat(levelOf(row)).isEqualTo(3);
        assertThat(recipientsOf(levelThree)).containsExactly(wecomUserIdOf(headE1));
    }
}
