package com.forcitis.one.organization.workforce.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AssignmentRoots;
import com.forcitis.one.acceptance.DataScopeSupport;
import com.forcitis.one.acceptance.EmploymentRelationshipSeeds;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1495: {@code GET /api/v1/organization/primary-assignments/{employeeId}} lists the person's primary posts, newest start first, with the
 * one open on the business day marked {@code current}. It is readable exactly when the employee detail is: the permission organization.workforce:employee:read,
 * the caller's data scope worked out from the person's CURRENT posts (an old post in a unit does not put the person in that unit's scope), out of scope
 * is 404 with the denied audit. Concurrent posts, voided versions and empty same-day ranges are not part of the history.
 */
class PrimaryAssignmentHistoryHttpIT extends DataScopeSupport {

    static final TestDatabase postgres = TestPostgres.database(PrimaryAssignmentHistoryHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String DENIED = "organization.workforce.employee.denied";
    static final LocalDate FIRST = LocalDate.of(2023, 1, 1);
    static final LocalDate SECOND = LocalDate.of(2024, 6, 1);
    static final LocalDate THIRD = LocalDate.of(2025, 9, 1);

    private final Map<String, String> unitIds = new LinkedHashMap<>();

    @BeforeAll
    void theWorld() throws Exception {
        unit("PH-A", null);
        unit("PH-B", null);
        unit("PH-C", null);
        unit("PH-X", null);
        position("PH-P1");
        position("PH-P2");
        person("mover", "PH-A", "PH-P2");
        person("stayer", "PH-A", "PH-P1");
        for (String key : List.of("admin1", "admin2", "oda", "allReader", "aReader", "bReader", "emptyReader", "nobody")) {
            person(key, "PH-X", "PH-P1");
        }
        seedOrganisation();
        administrator("admin1");
        administrator("admin2");
        grantByOperations("oda", ORG_DATA_ADMIN);
        for (String code : List.of("PH-A", "PH-B", "PH-C", "PH-X")) {
            unitIds.put(code, orgUnitId(session("oda"), code));
        }
        String[] perms = {EMPLOYEE_READ};
        roleWith("ph-all", "ALL", null, perms);
        roleWith("ph-only-a", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("PH-A"))), perms);
        roleWith("ph-only-b", "CUSTOM_ORG_UNITS", List.of(onlyUnit(unitIds.get("PH-B"))), perms);
        roleWith("ph-nothing", "CUSTOM_ORG_UNITS", List.of(onlyUnit(UUID.randomUUID().toString())), perms);
        grantOrdinaryRoles(administrator("admin1"), "allReader", "ph-all");
        grantOrdinaryRoles(administrator("admin1"), "aReader", "ph-only-a");
        grantOrdinaryRoles(administrator("admin1"), "bReader", "ph-only-b");
        grantOrdinaryRoles(administrator("admin1"), "emptyReader", "ph-nothing");
        try (Connection workforce = schemaConnection("organization_workforce")) {
            relationship(workforce, "mover");
            relationship(workforce, "stayer");
            UUID mover = UUID.fromString(employeeIdOf("mover"));
            UUID root = AssignmentRoots.primaryOf(workforce, stored(mover));
            // PH-B -> PH-C -> PH-A (open); the three ranges tile the time line.
            version(workforce, mover, root, "PRIMARY", "PH-B", "PH-P1", FIRST, SECOND, 0);
            version(workforce, mover, root, "PRIMARY", "PH-C", "PH-P1", SECOND, THIRD, 0);
            version(workforce, mover, root, "PRIMARY", "PH-A", "PH-P2", THIRD, null, 0);
            // An empty range written by a same-day change, a voided version and a concurrent post must not show.
            version(workforce, mover, root, "PRIMARY", "PH-X", "PH-P1", SECOND, SECOND, 1);
            UUID voided = version(workforce, mover, root, "PRIMARY", "PH-X", "PH-P2", LocalDate.of(2022, 1, 1), FIRST, 0);
            voidVersion(workforce, voided);
            UUID concurrent = AssignmentRoots.create(workforce, stored(mover), AssignmentRoots.CONCURRENT);
            version(workforce, mover, concurrent, "CONCURRENT", "PH-X", "PH-P2", THIRD, null, 1);
            UUID stayer = UUID.fromString(employeeIdOf("stayer"));
            version(workforce, stayer, AssignmentRoots.primaryOf(workforce, stored(stayer)), "PRIMARY", "PH-A", "PH-P1", FIRST, null, 0);
        }
    }

    private void relationship(Connection workforce, String key) throws Exception {
        UUID employee = UUID.fromString(employeeIdOf(key));
        execute(workforce, """
                insert into employment_relationship_versions (employment_relationship_id, employment_relationship_version_id, employee_id, effective_from_date, revision_number, employment_status, employment_phase_kind,
                    is_on_unpaid_leave, is_fee_earner, employment_start_date, employment_start_basis_kind, change_channel_kind, created_at, updated_at)
                values (?, ?, ?, ?, 0, 'EMPLOYED', 'REGULAR', false, false, ?, 'DECLARED', 'MIGRATION', now(), now())""",
                EmploymentRelationshipSeeds.relationshipOf(workforce, stored(employee)), Uuids.timeOrdered(NOW), stored(employee), FIRST, FIRST);
    }

    private UUID version(Connection workforce, UUID employee, UUID root, String kind, String unitCode, String positionCode, LocalDate from, LocalDate to, int revision) throws Exception {
        UUID position;
        try (PreparedStatement found = workforce.prepareStatement("select id from positions where code = ?")) {
            found.setString(1, positionCode);
            try (ResultSet rows = found.executeQuery()) {
                rows.next();
                position = rows.getObject(1, UUID.class);
            }
        }
        UUID id = Uuids.timeOrdered(NOW);
        execute(workforce, """
                insert into assignment_versions (employment_relationship_id, assignment_version_id, employee_id, assignment_id, assignment_kind, effective_from_date, effective_to_date, closed_at, revision_number,
                    org_unit_id, position_id, change_channel_kind, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, case when ?::date is null then null else now() end, ?, ?, ?, 'MIGRATION', now(), now())""",
                EmploymentRelationshipSeeds.relationshipOf(workforce, stored(employee)), id, stored(employee), root, kind, from, to, to, revision, UUID.fromString(unitIds.get(unitCode)), position);
        return id;
    }

    /** The voiding needs a personnel command row; the test only needs the version voided, so the foreign key is skipped for this one statement. */
    private void voidVersion(Connection workforce, UUID version) throws Exception {
        execute(workforce, "set session_replication_role = replica");
        try {
            execute(workforce, "update assignment_versions set superseded_at = now(), superseding_command_id = ?, updated_at = now() where assignment_version_id = ?", UUID.randomUUID(), version);
        } finally {
            execute(workforce, "set session_replication_role = origin");
        }
    }

    private static void execute(Connection connection, String sql, Object... args) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            statement.execute();
        }
    }

    private void roleWith(String code, String scope, List<CustomOrgUnit> units, String... permissions) throws Exception {
        MockHttpServletResponse created = createRole(administrator("admin1"), roleBody(code, scope, units, permissions));
        if (created.getStatus() == 202) {
            String request = JsonPath.read(created.getContentAsString(), "$.requestId");
            assertThat(confirm(administrator("admin2"), request).getStatus()).as("confirming role " + code).isEqualTo(204);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(roleIdOf(administrator("admin1"), code)).isPresent());
        } else {
            assertThat(created.getStatus()).as("creating role " + code + ": " + created.getContentAsString()).isEqualTo(201);
        }
    }

    private MockHttpServletResponse history(String key, String employeeKey) throws Exception {
        return mvc.perform(get("/api/v1/organization/primary-assignments/" + employeeIdOf(employeeKey)).with(as(session(key)))).andReturn().getResponse();
    }

    @Test
    @DisplayName("#1495 完成标准 2：主职历史按生效日从新到旧，只含主职；当前这条标记 current，已结束的带失效日（不含），空区间、已作废版本、兼职都不出现")
    void theHistoryListsPrimaryPostsNewestFirst() throws Exception {
        MockHttpServletResponse answer = history("allReader", "mover");
        assertThat(answer.getStatus()).as(answer.getContentAsString()).isEqualTo(200);
        List<Map<String, Object>> items = JsonPath.read(answer.getContentAsString(), "$.items");
        assertThat(items).extracting(i -> i.get("orgUnitName")).containsExactly(unitName("PH-A"), unitName("PH-C"), unitName("PH-B"));
        assertThat(items).extracting(i -> i.get("positionName")).containsExactly(positionName("PH-P2"), positionName("PH-P1"), positionName("PH-P1"));
        assertThat(items).extracting(i -> i.get("effectiveFrom")).containsExactly(THIRD.toString(), SECOND.toString(), FIRST.toString());
        assertThat(items).extracting(i -> i.get("effectiveTo")).containsExactly(null, THIRD.toString(), SECOND.toString());
        assertThat(items).extracting(i -> i.get("current")).containsExactly(true, false, false);
        assertThat(items.get(0).keySet()).containsExactlyInAnyOrder("orgUnitId", "orgUnitName", "positionId", "positionName", "effectiveFrom", "effectiveTo", "current");
    }

    @Test
    @DisplayName("#1495 完成标准 3（数据范围）：现任职在范围内的人，其完整历史（含范围外部门的旧主职）可读；范围按现任职算，旧主职所在部门的范围读不到")
    void scopeFollowsTheCurrentPostNotTheOldOnes() throws Exception {
        MockHttpServletResponse inScope = history("aReader", "mover");
        assertThat(inScope.getStatus()).isEqualTo(200);
        assertThat((List<?>) JsonPath.read(inScope.getContentAsString(), "$.items")).hasSize(3);

        int before = auditedActions(DENIED).size();
        MockHttpServletResponse oldUnitOnly = history("bReader", "mover");
        assertThat(oldUnitOnly.getStatus()).as("a reader of the unit the person LEFT is out of scope").isEqualTo(404);
        MockHttpServletResponse nothing = history("emptyReader", "mover");
        assertThat(nothing.getStatus()).isEqualTo(404);
        assertThat(JsonPath.<String>read(nothing.getContentAsString(), "$.errorCode")).endsWith("_NOT_FOUND")
                .isEqualTo(JsonPath.<String>read(unknown("emptyReader").getContentAsString(), "$.errorCode"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(auditedActions(DENIED)).hasSize(before + 2));
    }

    @Test
    @DisplayName("#1495 不得做：没有 employee:read 权限的账号是 403；不存在的员工是 404，不写审计")
    void withoutThePermissionOrWithoutThePerson() throws Exception {
        assertThat(history("nobody", "mover").getStatus()).isEqualTo(403);
        int before = auditedActions(DENIED).size();
        assertThat(unknown("allReader").getStatus()).isEqualTo(404);
        assertThat(auditedActions(DENIED)).hasSize(before);
    }

    @Test
    @DisplayName("#1495：只有一条主职的人历史只有一行，且为当前")
    void aPersonWhoNeverMovedHasOneRow() throws Exception {
        List<Map<String, Object>> items = JsonPath.read(history("allReader", "stayer").getContentAsString(), "$.items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("current", true).containsEntry("effectiveTo", null);
    }

    private final String unknownId = UUID.randomUUID().toString();

    private MockHttpServletResponse unknown(String key) throws Exception {
        return mvc.perform(get("/api/v1/organization/primary-assignments/" + unknownId).with(as(session(key)))).andReturn().getResponse();
    }

}
