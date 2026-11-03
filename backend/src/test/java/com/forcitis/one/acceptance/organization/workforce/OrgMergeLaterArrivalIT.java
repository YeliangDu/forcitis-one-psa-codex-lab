package com.forcitis.one.acceptance.organization.workforce;

import com.forcitis.one.acceptance.AssignmentRoots;
import static com.forcitis.one.acceptance.organization.workforce.OrgStructureCommandNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.ASSIGNMENT_VERSIONS;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.BUSINESS_ADMIN;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.COMMAND_KIND;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.DISPLAY_NAME;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EMPLOYEE_DOCUMENT_TYPE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.HIRE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.IS_TARGET_FEE_EARNER;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TARGET_EMPLOYMENT_PHASE_KIND;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.TARGET_POSITION_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WECOM_USER_ID;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.EFFECTIVE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PENDING;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.PROBATION;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULAR;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.REGULARIZE;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.WITHDRAWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.Uuids;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashMap;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #907, one-design ruling https://github.com/FORCITIS/forcitis-one-psa/issues/907#issuecomment-6596302513 and the user's answer 「a」 (recorded
 * by lead on #907): a merge M of unit X into Y effective D and the people who arrive in X on or after D.
 * Part 1 (technical): while M is open, a personnel command that places someone in X on or after D is refused at entry; the day before is
 * allowed and M moves that person; after M is withdrawn the same command is accepted.
 * Part 2 (option a): an arrival on or after D already on record blocks M at entry, at start and at finish; HR clears it (withdraws it or
 * re-enters it into Y) and M then runs; the system never rewrites the blocking command.
 * The refusals and the merge preview name the blocking employees and their command ids. The error codes, the wording that carries the blockers
 * (the exception message, ProblemDetail detail) and the preview's shape are the implementer's names, given by one-dev-org on 2026-11-03 at the
 * request of one-design ("the response field names for the blockers come from the implementer"); see the constants below.
 */
class OrgMergeLaterArrivalIT extends OrgStructureCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(OrgMergeLaterArrivalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    private static final LocalDate D = TODAY.plusDays(6);

    // Names agreed with the implementer (one-dev-org, 2026-11-03).
    static final String MERGE_OPEN = "ORGANIZATION_WORKFORCE_ORG_UNIT_MERGE_OPEN";
    static final String NOT_ALLOWED = "ORGANIZATION_WORKFORCE_ORG_STRUCTURE_COMMAND_NOT_ALLOWED";
    static final String MERGE_INCOMPLETE = "ORGANIZATION_WORKFORCE_ORG_STRUCTURE_MERGE_INCOMPLETE";
    static final String PREVIEW_TYPE = "com.forcitis.one.organization.workforce.api.OrgMergePreview";
    static final String PREVIEW_BLOCKING = "blocking";

    @Autowired
    ApplicationContext context;

    /** A business refusal with this error code, whose message names every given id (employee or command). */
    private static void refused(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable entry, UUID... named) {
        assertThatThrownBy(entry).isInstanceOfSatisfying(DomainException.class, refusal -> {
            assertThat(refusal.errorCode().code()).as(refusal.getMessage()).isEqualTo(code);
            for (UUID id : named) {
                assertThat(refusal.getMessage()).as("the refusal names the blocker").contains(id.toString());
            }
        });
    }

    /** One blocking arrival of the preview: employee, arrival day, and the personnel command (null for a row without one). */
    record Blocker(UUID employee, LocalDate startDate, UUID command) {
    }

    /** The merge preview's blocking arrivals, read from the api's preview type by its component names. */
    private List<Blocker> blockersInThePreview(LocalDate day) throws Exception {
        Class<?> api = Class.forName(API_PACKAGE + "." + API_TYPE);
        Method preview = null;
        for (Method method : api.getMethods()) {
            if (method.getName().equals(PREVIEW_MERGE_METHOD)) {
                preview = method;
            }
        }
        assertThat(preview).as(api.getName() + "." + PREVIEW_MERGE_METHOD).isNotNull();
        assertThat(preview.getReturnType().getName()).isEqualTo(PREVIEW_TYPE);
        Class<?> parameter = preview.getParameterTypes()[0];
        RecordComponent[] declared = parameter.getRecordComponents();
        Object[] arguments = new Object[declared.length];
        Class<?>[] types = new Class<?>[declared.length];
        for (int i = 0; i < declared.length; i++) {
            types[i] = declared[i].getType();
            arguments[i] = switch (declared[i].getName()) {
                case TARGET_ORG_UNIT_ID -> types[i].getDeclaredConstructor(UUID.class).newInstance(merged);
                case MERGE_INTO_ORG_UNIT_ID -> types[i].getDeclaredConstructor(UUID.class).newInstance(receiving);
                case EFFECTIVE_DATE -> day;
                default -> throw new AssertionError("unexpected component " + declared[i].getName() + " of " + parameter.getName());
            };
        }
        restoreActing();
        Object result;
        try {
            result = preview.invoke(context.getBean(api), parameter.getDeclaredConstructor(types).newInstance(arguments));
        } catch (InvocationTargetException failure) {
            throw failure.getCause() instanceof Exception cause ? cause : new IllegalStateException(failure.getCause());
        }
        List<Blocker> blockers = new ArrayList<>();
        for (Object arrival : (List<?>) component(result, PREVIEW_BLOCKING)) {
            Object command = component(arrival, "personnelCommandId");
            blockers.add(new Blocker((UUID) component(component(arrival, "employeeId"), "value"), (LocalDate) component(arrival, "startDate"),
                    command == null ? null : (UUID) component(command, "value")));
        }
        return blockers;
    }

    private static Object component(Object record, String name) throws Exception {
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (component.getName().equals(name)) {
                return component.getAccessor().invoke(record);
            }
        }
        throw new AssertionError(record.getClass().getName() + " has no component " + name);
    }

    private UUID merged;
    private UUID receiving;
    private UUID elsewhere;

    private UUID world() throws Exception {
        merged = seedUnit(null);
        receiving = seedUnit(null);
        elsewhere = seedUnit(null);
        return employedIn(merged, REGULAR, true);
    }

    private UUID enterMerge(LocalDate day) {
        return unitCommand(MERGE, merged, day, MERGE_INTO_ORG_UNIT_ID, receiving);
    }

    private List<Map<String, Object>> transfersOf(UUID merge) throws Exception {
        return rows("select * from personnel_commands where org_structure_command_id = ? order by employee_id", merge);
    }

    /** Moves the clock to the merge day; the sessions signed in before are past their 24 h lifetime then (RULING-LEAD-20261103-07). */
    private void onTheMergeDay() throws Exception {
        setToday(D);
        if (hr != null) {
            hr = new Staff(hr.employeeId(), signIn(hr.employeeId()));
        }
        orgAdmin = new Staff(orgAdmin.employeeId(), signIn(orgAdmin.employeeId()));
        actIn(orgAdmin);
    }

    /**
     * Someone is in X from the given day without any personnel command, as a write outside the command path would leave it (the precedent is
     * OrgUnitMergeIT.executionChecksTheChildrenAgain): the open assignment row is closed on that day and a MIGRATION row in X follows. It is the
     * only way to have an arrival after an entered merge, since part 1 refuses one entered through the commands.
     */
    private void arrivesOutsideTheCommands(UUID person, LocalDate from) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            Object position = query(workforce, "select position_id from assignment_versions where employee_id = ? and effective_to_date is null and superseded_at is null",
                    stored(person)).get(0).get("position_id");
            update(workforce, "update assignment_versions set effective_to_date = ?, closed_at = ?, updated_at = ? where employee_id = ? and effective_to_date is null and superseded_at is null",
                    from, at, at, stored(person));
            UUID root = AssignmentRoots.primaryOf(workforce, stored(person));
            update(workforce, AssignmentRoots.withRoot("""
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""", root),
                    AssignmentRoots.arguments(root, Uuids.timeOrdered(NOW), stored(person), from, stored(merged), position, at, at));
        }
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条（技术裁定）：合并待生效时，把人调入被并入单元、生效日为合并日或其后一天的调岗在录入时被拒，不留命令行")
    void anArrivalOnOrAfterTheMergeDayIsRefusedWhileTheMergeIsOpen() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        enterMerge(D);
        actInSpecialist();

        refused(MERGE_OPEN, () -> transfer(mover, D, merged));
        refused(MERGE_OPEN, () -> transfer(mover, D.plusDays(1), merged));

        assertThat(commandCount(mover)).isZero();
        assertThat(unitOfEmployee(mover, D)).isEqualTo(elsewhere);
    }

    /** A HIRE of a new person into the merged unit; the WeCom user id is unique to the call (#1038). */
    private UUID hireInto(UUID orgUnit, LocalDate effective) throws Exception {
        String wecomUserId = "late-hire-" + UUID.randomUUID().toString().substring(0, 13);
        Map<String, Object> fields = new HashMap<>();
        fields.put(COMMAND_KIND, HIRE);
        fields.put(EFFECTIVE_DATE, effective);
        fields.put(TARGET_ORG_UNIT_ID, orgUnit);
        fields.put(TARGET_POSITION_ID, newPosition());
        fields.put(IS_TARGET_FEE_EARNER, true);
        fields.put(TARGET_EMPLOYMENT_PHASE_KIND, PROBATION);
        fields.put(DISPLAY_NAME, "Synthetic hire " + wecomUserId);
        fields.put(WECOM_USER_ID, wecomUserId);
        return commands().enter(fields);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条（入职与调入同样处理）：合并待生效时，入职到被并入单元、生效日为合并日或其后一天，录入被拒（同一具名冲突错误），不留员工与命令行")
    void aHireOnOrAfterTheMergeDayIsRefusedWhileTheMergeIsOpen() throws Exception {
        world();
        enterMerge(D);
        // A hire issues an employee code: the template is confirmed as in HireOpensAccountIT, so the refusal can only be the merge's.
        confirmSeededNumberingRule(EMPLOYEE_DOCUMENT_TYPE, staff(BUSINESS_ADMIN).session());
        actInSpecialist();
        long employees = (long) rows("select count(*) as n from employees").get(0).get("n");
        long commands = (long) rows("select count(*) as n from personnel_commands").get(0).get("n");

        refused(MERGE_OPEN, () -> hireInto(merged, D));
        refused(MERGE_OPEN, () -> hireInto(merged, D.plusDays(1)));

        assertThat((long) rows("select count(*) as n from employees").get(0).get("n")).isEqualTo(employees);
        assertThat((long) rows("select count(*) as n from personnel_commands").get(0).get("n")).isEqualTo(commands);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条：合并待生效时，调入被并入单元、生效日为合并日前一天的调岗被接受，合并的预演列出此人，执行时把此人转到承接单元")
    void anArrivalTheDayBeforeIsAcceptedAndMoved() throws Exception {
        UUID stayer = world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        UUID merge = enterMerge(D);
        actInSpecialist();

        UUID arrival = transfer(mover, D.minusDays(1), merged);

        assertThat(command(arrival).get("status")).isEqualTo(PENDING);
        actIn(orgAdmin);
        assertThat(orgCommands().previewMerge(merged, receiving, D)).containsExactlyInAnyOrder(stayer, mover);
        onTheMergeDay();
        orgCommands().executeMerge(merge);
        assertThat(orgCommand(merge).get("status")).isEqualTo(EFFECTIVE);
        assertThat(transfersOf(merge).stream().map(transfer -> fromStored(transfer.get("employee_id")))).containsExactlyInAnyOrder(stayer, mover);
        assertThat(unitOfEmployee(mover, D)).isEqualTo(receiving);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 1 条：合并撤回后，原先被拒的调入（合并日当天）被接受")
    void afterTheMergeIsWithdrawnTheArrivalIsAccepted() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        UUID merge = enterMerge(D);
        actInSpecialist();
        refused(MERGE_OPEN, () -> transfer(mover, D, merged));
        actIn(orgAdmin);

        orgCommands().withdraw(merge, "synthetic: the merge is off");
        actInSpecialist();

        assertThat(orgCommand(merge).get("status")).isEqualTo(WITHDRAWN);
        assertThat(transfer(mover, D, merged)).isNotNull();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：先录了调入被并入单元、生效日在合并日后 10 天的调岗，再录合并被拒，不留合并命令行；该调岗命令行与其任职版本在拒绝前后不变")
    void aLaterArrivalOnRecordBlocksTheEntry() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(mover, D.plusDays(10), merged);
        Map<String, Object> blockingBefore = command(blocking);
        List<Map<String, Object>> versionsBefore = allVersions(ASSIGNMENT_VERSIONS, mover);
        actIn(orgAdmin);
        long commands = orgCommandCount();

        refused(NOT_ALLOWED, () -> enterMerge(D), mover, blocking);

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(command(blocking)).as("the system does not rewrite or retarget the blocking command").isEqualTo(blockingBefore);
        assertThat(allVersions(ASSIGNMENT_VERSIONS, mover)).isEqualTo(versionsBefore);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：倒签的合并（合并日在过去，有人自合并日后第 3 天起已在被并入单元）录入被拒；没有这样的人的另一单元同日倒签合并可以录入")
    void aBackDatedMergeWithSomeoneAlreadyArrivedIsRefusedAtEntry() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        LocalDate past = TODAY.minusDays(10);
        actInSpecialist();
        UUID arrival = transfer(mover, past.plusDays(3), merged);
        Map<String, Object> arrivalBefore = command(arrival);
        actIn(orgAdmin);
        long commands = orgCommandCount();

        refused(NOT_ALLOWED, () -> enterMerge(past), mover, arrival);

        assertThat(orgCommandCount()).isEqualTo(commands);
        assertThat(command(arrival)).isEqualTo(arrivalBefore);
        UUID quiet = seedUnit(null);
        employedIn(quiet, REGULAR, true);
        assertThat(unitCommand(MERGE, quiet, past, MERGE_INTO_ORG_UNIT_ID, receiving)).as("the refusal is the arrival's, not the back date's").isNotNull();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：开始时再校验：合并录入后有人在合并日后第 3 天起在被并入单元（不经人事命令写入），到日执行被拒：仍 PENDING、没有调岗、原有人员未动")
    void theStartChecksTheArrivalsAgain() throws Exception {
        UUID stayer = world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        UUID merge = enterMerge(D);
        arrivesOutsideTheCommands(mover, D.plusDays(3));
        onTheMergeDay();

        refused(NOT_ALLOWED, () -> orgCommands().executeMerge(merge), mover);

        assertThat(orgCommand(merge).get("status")).isEqualTo(PENDING);
        assertThat(transfersOf(merge)).isEmpty();
        assertThat(unitOfEmployee(stayer, D)).isEqualTo(merged);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：收尾时再校验：合并执行中（一人被同日命令卡住）出现合并日后到来的人，障碍解除后收尾被拒：仍 IN_PROGRESS、被并入单元不撤销、不发「组织单元已合并」")
    void theFinishChecksTheArrivalsAgain() throws Exception {
        world();
        UUID stuck = employedIn(merged, PROBATION, true);
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID obstacle = enter(stuck, REGULARIZE, D);
        actIn(orgAdmin);
        UUID merge = enterMerge(D);
        onTheMergeDay();
        catchThrowable(() -> orgCommands().executeMerge(merge));
        assertThat(orgCommand(merge).get("status")).as("halfway, as in OrgUnitMergeIT").isEqualTo(IN_PROGRESS);
        arrivesOutsideTheCommands(mover, D.plusDays(3));
        actInSpecialist();
        commands().withdraw(obstacle, "synthetic: clears the way");
        actIn(orgAdmin);

        refused(MERGE_INCOMPLETE, () -> orgCommands().executeMerge(merge), mover);

        assertThat(orgCommand(merge).get("status")).isEqualTo(IN_PROGRESS);
        assertThat(orgCommand(merge).get("applied_at")).isNull();
        assertThat(unitOn(merged, D).get("status")).isEqualTo(ACTIVE);
        assertThat(eventsOf(EVENT_MERGED, merge)).isZero();
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：合并预演把挡路的人（员工、到达日、人事命令 ID；没有命令的行为空）按到达日列出，与要转走的人分开；预演不写任何东西")
    void thePreviewListsTheBlockersApartFromThePeopleToMove() throws Exception {
        UUID stayer = world();
        UUID byCommand = employedIn(elsewhere, REGULAR, true);
        UUID byRow = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(byCommand, D.plusDays(10), merged);
        arrivesOutsideTheCommands(byRow, D.plusDays(3));
        actIn(orgAdmin);
        long commands = orgCommandCount();

        List<Blocker> blockers = blockersInThePreview(D);

        assertThat(blockers).containsExactly(new Blocker(byRow, D.plusDays(3), null), new Blocker(byCommand, D.plusDays(10), blocking));
        assertThat(orgCommands().previewMerge(merged, receiving, D)).as("the people to move are listed apart").containsExactly(stayer);
        assertThat(orgCommandCount()).isEqualTo(commands);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：人事撤回挡路的调岗后，合并可以录入并执行到 EFFECTIVE")
    void withdrawingTheBlockerLetsTheMergeRun() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(mover, D.plusDays(10), merged);
        actIn(orgAdmin);
        assertRefused(() -> enterMerge(D));
        actInSpecialist();

        commands().withdraw(blocking, "synthetic: cleared for the merge");
        actIn(orgAdmin);
        UUID merge = enterMerge(D);
        onTheMergeDay();
        orgCommands().executeMerge(merge);

        assertThat(orgCommand(merge).get("status")).isEqualTo(EFFECTIVE);
        assertThat(unitOn(merged, D).get("status")).isEqualTo(REVOKED);
        assertThat(command(blocking).get("status")).isEqualTo(WITHDRAWN);
    }

    @Test
    @DisplayName("#907 one-design 裁定 6596302513 第 2 条、用户答复「a」：人事把挡路的调岗撤回、改录为调入承接单元后，合并可以录入并执行到 EFFECTIVE；改录的调岗不被合并改动，仍待生效、目标仍是承接单元")
    void reEnteringTheBlockerIntoTheReceivingUnitLetsTheMergeRun() throws Exception {
        world();
        UUID mover = employedIn(elsewhere, REGULAR, true);
        actInSpecialist();
        UUID blocking = transfer(mover, D.plusDays(10), merged);
        actIn(orgAdmin);
        assertRefused(() -> enterMerge(D));
        actInSpecialist();

        commands().withdraw(blocking, "synthetic: re-entered into the receiving unit");
        UUID reEntered = transfer(mover, D.plusDays(10), receiving);
        Map<String, Object> reEnteredBefore = command(reEntered);
        actIn(orgAdmin);
        UUID merge = enterMerge(D);
        onTheMergeDay();
        orgCommands().executeMerge(merge);

        assertThat(orgCommand(merge).get("status")).isEqualTo(EFFECTIVE);
        assertThat(transfersOf(merge).stream().map(transfer -> fromStored(transfer.get("employee_id")))).doesNotContain(mover);
        assertThat(command(reEntered)).isEqualTo(reEnteredBefore);
        assertThat(unitOfEmployee(mover, D.plusDays(10))).isEqualTo(receiving);
    }
}
