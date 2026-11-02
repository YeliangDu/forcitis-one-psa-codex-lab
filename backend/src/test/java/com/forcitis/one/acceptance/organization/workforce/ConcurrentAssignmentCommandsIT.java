package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.EmployeeLegalEntities;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #711 acceptance part through the personnel command api (DD revision 2 on #711 sections 4.3 to 4.5; amendment of 2026-11-02, user answers
 * 1a 2a 3a; one-design rulings on #711 comment 6588973194): the 人事专员 starts and ends concurrent posts (新增兼职 START_CONCURRENT, 结束兼职
 * END_CONCURRENT) under the same permission as TRANSFER, audited the same way; a TRANSFER without targetAssignmentId acts on the primary
 * root and stores it; commands are counted per day per person-or-primary and per concurrent post; SEPARATE ends every post; a command on a
 * root with no current version on its date is refused; corrections stay on their post; readers of the person's department, supervisor and
 * cost legal entity read the primary only, and a concurrent post publishes no workforce event.
 */
class ConcurrentAssignmentCommandsIT extends PersonnelCommandSupport {

    static final TestDatabase postgres = TestPostgres.database(ConcurrentAssignmentCommandsIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    // Names of #711 (DD 4.3, glossary list of section 7; api component accepted by one-design, #711 comment 6588973194).
    static final String START_CONCURRENT = "START_CONCURRENT";
    static final String END_CONCURRENT = "END_CONCURRENT";
    static final String TARGET_ASSIGNMENT_ID = "targetAssignmentId";

    @Autowired
    private OrgDirectory directory;

    @Autowired
    private EmployeeLegalEntities legalEntities;

    private UUID startConcurrent(UUID employee, LocalDate day, UUID orgUnit, UUID position) {
        return commands().enter(fields(EMPLOYEE_ID, employee, COMMAND_KIND, START_CONCURRENT, EFFECTIVE_DATE, day, TARGET_ORG_UNIT_ID, orgUnit, TARGET_POSITION_ID, position));
    }

    private UUID endConcurrent(UUID employee, LocalDate day, UUID root) {
        return commands().enter(fields(EMPLOYEE_ID, employee, COMMAND_KIND, END_CONCURRENT, EFFECTIVE_DATE, day, TARGET_ASSIGNMENT_ID, root));
    }

    private UUID transferPost(UUID employee, LocalDate day, UUID root, UUID toUnit) {
        return commands().enter(fields(EMPLOYEE_ID, employee, COMMAND_KIND, TRANSFER, EFFECTIVE_DATE, day, TARGET_ASSIGNMENT_ID, root, TARGET_ORG_UNIT_ID, toUnit));
    }

    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> fields = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            fields.put((String) keyValues[i], keyValues[i + 1]);
        }
        return fields;
    }

    /** The CONCURRENT root a START_CONCURRENT created: the root of the version the command wrote (one-design ruling, no new api method). */
    private UUID rootCreatedBy(UUID command) throws Exception {
        List<Map<String, Object>> found = rows("select r.assignment_id from " + AssignmentRoots.TABLE + " r join assignment_versions v on v.assignment_id = r.assignment_id"
                + " where v.change_command_id = ? and r.assignment_kind = 'CONCURRENT'", command);
        assertThat(found).as("START_CONCURRENT " + command + " created a CONCURRENT root with its first version").hasSize(1);
        return (UUID) found.get(0).get("assignment_id");
    }

    private UUID primaryRootOf(UUID employee) throws Exception {
        return (UUID) rows("select assignment_id from " + AssignmentRoots.TABLE + " where employee_id = ? and assignment_kind = 'PRIMARY'", stored(employee)).get(0).get("assignment_id");
    }

    /** The current (not superseded) version of the root in force on the day, or null. */
    private Map<String, Object> postOn(UUID root, LocalDate day) throws Exception {
        List<Map<String, Object>> current = rows("select * from assignment_versions where assignment_id = ? and superseded_at is null order by effective_from_date", root);
        return inForceOn(current, day);
    }

    private static void assertRefused(Throwable failure) {
        assertThat(failure).as("refused for its content").isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("#711 第 4.3 节、修正案第 1 条（用户 1a）：人事专员新增兼职：命令生效，建一个兼职根与它的第一条版本（单元、岗位、来源命令为本命令、无直属领导），主任职不变；与其他人事命令一样审计；不发任何 workforce 事件（第 4.5 节）")
    void aSpecialistStartsAConcurrentPost() throws Exception {
        UUID person = employed(REGULAR);
        UUID primaryUnit = unitOfEmployee(person);
        UUID other = newUnit();
        UUID position = newPosition();
        int audited = auditCount(AUDIT_ENTERED);

        UUID command = startConcurrent(person, TODAY, other, position);

        UUID root = rootCreatedBy(command);
        Map<String, Object> post = postOn(root, TODAY);
        assertThat(fromStored(post.get("org_unit_id"))).isEqualTo(other);
        assertThat(post.get("position_id")).isEqualTo(position);
        assertThat(post.get("reports_to_employee_id")).isNull();
        assertThat(post.get("assignment_kind")).isEqualTo(AssignmentRoots.CONCURRENT);
        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(fromStored(postOn(primaryRootOf(person), TODAY).get("org_unit_id"))).isEqualTo(primaryUnit);
        assertThat(auditCount(AUDIT_ENTERED)).isEqualTo(audited + 1);
        assertThat(allCommandEventsOf(command)).isZero();
    }

    private UUID unitOfEmployee(UUID person) throws Exception {
        return fromStored(inForceOn(currentVersions(ASSIGNMENT_VERSIONS, person), TODAY).get("org_unit_id"));
    }

    @Test
    @DisplayName("#711 修正案第 1 条（与调岗同一权限 organization.workforce:personnel-command:create）：没有该权限的人新增、结束兼职都被拒，不留命令")
    void startingAndEndingNeedThePersonnelCommandPermission() throws Exception {
        UUID person = employed(REGULAR);
        actIn(hr);
        UUID root = rootCreatedBy(startConcurrent(person, TODAY, newUnit(), newPosition()));
        long commands = commandCount(person);
        actIn(staff());

        assertThat(isPermissionRefusal(catchThrowable(() -> startConcurrent(person, TODAY.plusDays(1), newUnit(), newPosition())))).isTrue();
        assertThat(isPermissionRefusal(catchThrowable(() -> endConcurrent(person, TODAY.plusDays(1), root)))).isTrue();

        assertThat(commandCount(person)).isEqualTo(commands);
    }

    @Test
    @DisplayName("#711 one-design 裁定（#711 comment 6588973194，新增完成标准）：不带 targetAssignmentId 的调岗作用于主任职并存下主任职根；兼职不动")
    void aTransferWithoutATargetActsOnThePrimary() throws Exception {
        UUID person = employed(REGULAR);
        UUID concurrent = rootCreatedBy(startConcurrent(person, TODAY, newUnit(), newPosition()));
        UUID concurrentUnit = fromStored(postOn(concurrent, TODAY).get("org_unit_id"));
        UUID to = newUnit();

        UUID command = transfer(person, TODAY.plusDays(2), to);

        assertThat(command(command).get("target_assignment_id")).isEqualTo(primaryRootOf(person));
        assertThat(fromStored(postOn(primaryRootOf(person), TODAY.plusDays(2)).get("org_unit_id"))).isEqualTo(to);
        assertThat(fromStored(postOn(concurrent, TODAY.plusDays(2)).get("org_unit_id"))).isEqualTo(concurrentUnit);
    }

    @Test
    @DisplayName("#711 完成标准 7（经接口）：同一天主任职调岗与新增兼职都被接受，同一天两条新增兼职也被接受（新增兼职不按日限）；同一天结束兼职与主任职调岗都被接受；主任职同一天第二条、同一兼职同一天第二条被拒")
    void dayRulesThroughTheApi() throws Exception {
        UUID person = employed(REGULAR);
        UUID concurrent = rootCreatedBy(startConcurrent(person, TODAY, newUnit(), newPosition()));
        LocalDate first = TODAY.plusDays(3);
        LocalDate second = TODAY.plusDays(4);

        transfer(person, first, newUnit());
        startConcurrent(person, first, newUnit(), newPosition());
        startConcurrent(person, first, newUnit(), newPosition());
        endConcurrent(person, second, concurrent);
        transfer(person, second, newUnit());

        assertRefused(catchThrowable(() -> transfer(person, first, newUnit())));
        assertRefused(catchThrowable(() -> transferPost(person, second, concurrent, newUnit())));
    }

    @Test
    @DisplayName("#711 完成标准 4、5（经接口）：新增兼职带直属领导、兼职调岗带直属领导或计费标记、结束兼职指向主任职根，都被拒且不留命令")
    void contentRefusalsThroughTheApi() throws Exception {
        UUID person = employed(REGULAR);
        UUID leader = employed(REGULAR);
        UUID concurrent = rootCreatedBy(startConcurrent(person, TODAY, newUnit(), newPosition()));
        long commands = commandCount(person);

        assertRefused(catchThrowable(() -> commands().enter(fields(EMPLOYEE_ID, person, COMMAND_KIND, START_CONCURRENT, EFFECTIVE_DATE, TODAY.plusDays(1),
                TARGET_ORG_UNIT_ID, newUnit(), TARGET_POSITION_ID, newPosition(), TARGET_REPORTS_TO_EMPLOYEE_ID, leader))));
        assertRefused(catchThrowable(() -> commands().enter(fields(EMPLOYEE_ID, person, COMMAND_KIND, TRANSFER, EFFECTIVE_DATE, TODAY.plusDays(1),
                TARGET_ASSIGNMENT_ID, concurrent, TARGET_REPORTS_TO_EMPLOYEE_ID, leader))));
        assertRefused(catchThrowable(() -> commands().enter(fields(EMPLOYEE_ID, person, COMMAND_KIND, TRANSFER, EFFECTIVE_DATE, TODAY.plusDays(1),
                TARGET_ASSIGNMENT_ID, concurrent, IS_TARGET_FEE_EARNER, true))));
        assertRefused(catchThrowable(() -> endConcurrent(person, TODAY.plusDays(1), primaryRootOf(person))));

        assertThat(commandCount(person)).isEqualTo(commands);
    }

    @Test
    @DisplayName("#711 完成标准 10：离职关闭此人所有开着的任职版本（主任职与兼职）；结束兼职（与其他人事命令一样审计）后，在结束日之后对该兼职的命令（再结束、调岗）被拒（S7）")
    void separationEndsEveryPostAndAnEndedPostTakesNoCommand() throws Exception {
        UUID leaver = employed(REGULAR);
        UUID leaverConcurrent = rootCreatedBy(startConcurrent(leaver, TODAY, newUnit(), newPosition()));
        separate(leaver, TODAY.plusDays(5));

        assertThat(postOn(primaryRootOf(leaver), TODAY.plusDays(6))).isNull();
        assertThat(postOn(leaverConcurrent, TODAY.plusDays(6))).isNull();
        assertThat(postOn(leaverConcurrent, TODAY.plusDays(5))).as("open through the last working day").isNotNull();

        UUID person = employed(REGULAR);
        UUID concurrent = rootCreatedBy(startConcurrent(person, TODAY, newUnit(), newPosition()));
        int audited = auditCount(AUDIT_ENTERED);
        endConcurrent(person, TODAY.plusDays(2), concurrent);
        assertThat(auditCount(AUDIT_ENTERED)).as("audited as the other personnel commands (amendment item 1)").isEqualTo(audited + 1);
        assertThat(postOn(concurrent, TODAY.plusDays(2))).isNull();
        assertRefused(catchThrowable(() -> endConcurrent(person, TODAY.plusDays(3), concurrent)));
        assertRefused(catchThrowable(() -> transferPost(person, TODAY.plusDays(3), concurrent, newUnit())));
    }

    @Test
    @DisplayName("#711 完成标准 6（经接口）：兼职的更正只作废该兼职的版本（主任职的行不动）；有兼职时主任职的更正（不带 targetAssignmentId 即主任职，并存下主任职根）照常被接受，只作废主任职的行")
    void correctionsStayOnTheirPost() throws Exception {
        UUID person = employed(REGULAR);
        UUID leader = employed(REGULAR);
        UUID concurrent = rootCreatedBy(startConcurrent(person, TODAY.minusDays(20), newUnit(), newPosition()));
        UUID primary = primaryRootOf(person);
        UUID corrected = newUnit();

        commands().enter(fields(EMPLOYEE_ID, person, COMMAND_KIND, CORRECTION, EFFECTIVE_DATE, TODAY.minusDays(10), TARGET_ASSIGNMENT_ID, concurrent,
                TARGET_ORG_UNIT_ID, corrected));

        assertThat(rows("select 1 from assignment_versions where assignment_id = ? and superseded_at is not null", primary)).as("the primary is untouched").isEmpty();
        assertThat(fromStored(postOn(concurrent, TODAY).get("org_unit_id"))).isEqualTo(corrected);
        assertThat(postOn(concurrent, TODAY).get("assignment_id")).isEqualTo(concurrent);

        UUID primaryCorrection = commands().enter(fields(EMPLOYEE_ID, person, COMMAND_KIND, CORRECTION, EFFECTIVE_DATE, TODAY.minusDays(5), TARGET_REPORTS_TO_EMPLOYEE_ID, leader));

        assertThat(command(primaryCorrection).get("target_assignment_id")).as("omitted = the primary root, stored at entry (#711 comment 6589514976)").isEqualTo(primary);
        assertThat(fromStored(postOn(primary, TODAY).get("reports_to_employee_id"))).isEqualTo(leader);
        assertThat(fromStored(postOn(concurrent, TODAY).get("org_unit_id"))).as("the concurrent post is untouched by the primary correction").isEqualTo(corrected);
    }

    @Test
    @DisplayName("#711 完成标准 12、13（c，one-design 裁定：权限复核标记在 #729 前归结为不发调岗事件）：调动兼职不发「员工已调岗」也不发「员工成本法人已变更」；今天生效的主任职调岗照常发一次「员工已调岗」")
    void movingAConcurrentPostPublishesNothing() throws Exception {
        UUID person = employed(REGULAR);
        UUID concurrent = rootCreatedBy(startConcurrent(person, TODAY, newUnit(), newPosition()));

        UUID moved = transferPost(person, TODAY, concurrent, newUnit());

        assertThat(eventsOf(EVENT_TRANSFERRED, moved)).isZero();
        assertThat(eventsOf("EmployeeLegalEntityChanged", moved)).isZero();
        assertThat(allCommandEventsOf(moved)).isZero();
        assertThat(eventsOf(EVENT_TRANSFERRED, transfer(person, TODAY, newUnit()))).as("the twin: moving the primary publishes").isEqualTo(1);
    }

    @Test
    @DisplayName("#711 完成标准 11、13（a、b，one-design 裁定 #711 comment 6588973194）：有兼职（在另一法人的单元）时，组织目录按主任职给出所属单元（按员工 ID 与按企业微信登录查都一样）与直属领导；兼职单元的负责人仍是该单元的负责人、不是兼职者；没有自有归属的人成本法人按主任职单元链取（不按兼职、不抛异常）")
    void readersReadThePrimaryOnly() throws Exception {
        Staff admin = staff("org_data_admin");
        UUID otherEntity = createLegalEntity(admin.session(), newLegalEntityCode("CJ"), "Synthetic concurrent post entity");
        UUID primaryUnit = attributedUnit(admin, legalEntity);
        UUID concurrentUnit = attributedUnit(admin, otherEntity);
        UUID unitHead = employed(REGULAR);
        headedBy(concurrentUnit, unitHead);
        UUID leader = employed(REGULAR);
        UUID person = inheriting(primaryUnit, leader);
        startConcurrent(person, TODAY, concurrentUnit, newPosition());
        EmployeeId id = employeeIdOf(person);

        assertThat(directory.findEmployee(id)).hasValueSatisfying(profile -> assertThat(profile.orgUnitId().value()).isEqualTo(primaryUnit));
        assertThat(directory.findEmployeeByLogin(new com.forcitis.one.organization.workforce.api.LoginLookup.ByWecomUser(
                new com.forcitis.one.kernel.WecomUserId(wecomUserIdOf(person))))).hasValueSatisfying(profile -> assertThat(profile.orgUnitId().value()).isEqualTo(primaryUnit));
        assertThat(directory.findSupervisor(id).map(EmployeeId::value)).contains(leader);
        assertThat(directory.findOrgUnitHead(new OrgUnitId(concurrentUnit)).map(EmployeeId::value)).as("the unit's head, not its concurrent holder").contains(unitHead);
        assertThat(legalEntities.on(id, TODAY).value()).isEqualTo(legalEntity);
    }

    private UUID attributedUnit(Staff admin, UUID entity) throws Exception {
        UUID id = newUnit();
        MockHttpServletResponse attributed = mvc.perform(put("/api/v1/organization/org-unit-legal-entities/" + text(id)).header("If-Match", "\"0\"")
                .with(csrf()).with(session(admin.session())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalEntityId\":\"" + entity + "\",\"effectiveFrom\":\"" + TODAY.minusDays(30) + "\"}")).andReturn().getResponse();
        assertThat(attributed.getStatus()).as("attributing the unit: " + attributed.getContentAsString()).isEqualTo(200);
        return id;
    }

    /** A migrated version of the unit from SINCE naming its head (the head is a unit attribute, ADR-0068 item 4). */
    private void headedBy(UUID orgUnit, UUID head) throws Exception {
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into org_unit_versions (org_unit_version_id, org_unit_id, effective_from_date, name, status, region_kind, is_delivery_unit,
                      head_employee_id, created_at, updated_at) values (?, ?, ?, ?, 'ACTIVE', 'HEADQUARTERS', false, ?, ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(orgUnit), SINCE, "Synthetic headed unit", stored(head), at, at);
        }
    }

    /** An employed person without an own attribution, in the unit, reporting to the leader; migrated versions on the PRIMARY root. */
    private UUID inheriting(UUID orgUnit, UUID leader) throws Exception {
        UUID id = newEmployeeId();
        employee(id, orgUnit, null);
        OffsetDateTime at = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        try (Connection workforce = schemaConnection("organization_workforce")) {
            update(workforce, """
                    insert into employee_versions (employee_version_id, employee_id, effective_from_date, revision_number, employment_status,
                      employment_phase_kind, is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind,
                      change_channel_kind, created_at, updated_at)
                    values (?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'LEGACY_HR_RECORD', 'MIGRATION', ?, ?)""",
                    Uuids.timeOrdered(NOW), stored(id), SINCE, SINCE, at, at);
            UUID root = AssignmentRoots.primaryOf(workforce, stored(id));
            update(workforce, AssignmentRoots.withRoot("""
                    insert into assignment_versions (assignment_version_id, employee_id, effective_from_date, org_unit_id, position_id, reports_to_employee_id,
                      change_channel_kind, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 'MIGRATION', ?, ?)""", root),
                    AssignmentRoots.arguments(root, Uuids.timeOrdered(NOW), stored(id), SINCE, stored(orgUnit), newPosition(), stored(leader), at, at));
        }
        return id;
    }
}
