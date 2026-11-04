package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.Uuids;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1172 completion lines 2, 4, 6-8; ADR-0068 section 4 and org-structure-commands.md sections 4-5.
 * Contract fixed here: page {@code items, nextCursor} (null at the end); list/detail rows
 * {@code orgStructureCommandId, commandKind, targetKind, targetOrgUnitId, targetPositionId, effectiveDate, status,
 * countersigns, mergeProgress}; each countersign has {@code attributionCountersignId, requestKind, legalEntityId, decisionKind (null while the request is open)};
 * merge progress has {@code affectedEmployeeCount, transferredEmployeeCount}. Non-merges may omit/null mergeProgress;
 * no countersign means an empty array. Target ids unused for a target kind may be absent/null.
 * Company-wide for every holder of org-structure-command:create whatever the data scope ("whoever can enter can see", user 2026-11-03 A1 and A4,
 * lead correction of the card on 2026-11-04); an unknown id is 404. No additional list filters are introduced.
 */
class OrgStructureCommandReadHttpIT extends CommandReadHttpSupport {
    static final TestDatabase postgres = TestPostgres.database(OrgStructureCommandReadHttpIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired ApplicationContext context;
    private String awaiting;
    private String merged;
    private String positionCommand;
    private UUID targetPosition;
    private UUID otherEntity;

    @Override String resource() { return "/api/v1/organization/org-structure-commands"; }
    @Override String idField() { return "orgStructureCommandId"; }
    @Override String permission() { return "organization.workforce:org-structure-command:create"; }
    @Override String denied() { return "organization.workforce.org-structure-command.denied"; }
    @Override String mode() { return "NOT_SCOPE_BOUND"; }

    @Override
    void seedCommands() throws Exception {
        for (String key : List.of("a", "child", "b")) {
            String unit = key.equals("a") ? "READ-A" : key.equals("b") ? "READ-B" : "READ-CHILD";
            commands.put(key, unitCommand("SET_ATTRIBUTES", unit, key.equals("b") ? TODAY.minusDays(1) : DUE, "targetName", "Synthetic changed " + key));
        }
        grantByOperations("finance", "legal_entity_finance_head");
        otherEntity = createLegalEntity(signedIn("writer"), "RDB", "Synthetic second read entity");
        bindFinanceHead(fixtureEntity);
        bindFinanceHead(otherEntity);
        awaiting = unitCommand("ASSIGN_LEGAL_ENTITY", "READ-A", DUE.plusDays(2), "targetLegalEntityId", otherEntity);

        targetPosition = Uuids.timeOrdered(NOW);
        try (Connection db = schemaConnection("organization_workforce")) {
            run(db, "insert into positions (id, code, name, status) values (?, 'READ-DISABLE', 'Synthetic unused position', 'ACTIVE')", targetPosition);
            run(db, """
                    insert into position_versions (position_version_id, position_id, effective_from_date, code, name, status, change_channel_kind)
                    values (?, ?, ?, 'READ-DISABLE', 'Synthetic unused position', 'ACTIVE', 'MIGRATION')""", Uuids.timeOrdered(NOW), targetPosition, TODAY.minusDays(30));
        }
        positionCommand = enter(Map.of("targetKind", "POSITION", "commandKind", "DISABLE", "targetPositionId", targetPosition, "effectiveDate", DUE), null);
        merged = unitCommand("MERGE", "READ-B", TODAY, "mergeIntoOrgUnitId", UUID.fromString(unitsByCode.get("READ-X")));
        orgApi().executeMerge(UUID.fromString(merged));
    }

    private void bindFinanceHead(UUID entity) throws Exception {
        try (Connection db = schemaConnection("organization_workforce")) {
            Map<String, Object> open = PersonnelCommandSupport.query(db,
                    "select * from legal_entity_versions where legal_entity_id = ? and effective_to_date is null", entity).getFirst();
            run(db, "update legal_entity_versions set effective_to_date = ? where legal_entity_version_id = ?", TODAY, open.get("legal_entity_version_id"));
            run(db, """
                    insert into legal_entity_versions (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, code, name, status, finance_head_employee_id)
                    values (?, ?, ?, ?, ?, ?, ?, ?)""", Uuids.timeOrdered(NOW), entity, TODAY, ((Number) open.get("revision_number")).intValue() + 1,
                    open.get("code"), open.get("name"), open.get("status"), UUID.fromString(employeeIdOf("finance")));
        }
    }

    private String unitCommand(String kind, String unit, LocalDate day, String field, Object value) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>(Map.of("targetKind", "ORG_UNIT", "commandKind", kind, "targetOrgUnitId", UUID.fromString(unitsByCode.get(unit)),
                "effectiveDate", day));
        fields.put(field, value);
        return enter(fields, unit);
    }

    private String enter(Map<String, Object> fields, String unit) throws Exception {
        String id = orgApi().enter(fields).toString();
        commandUnits.put(id, unit);
        return id;
    }

    /** Same production-session authentication and permission refresh as PersonnelCommandSupport.actIn, for the pre-HTTP application API. */
    private OrgStructureCommandsProbe orgApi() throws Exception {
        var profile = mvc.perform(get(CURRENT_SESSION).with(as(session("writer")))).andReturn().getResponse();
        require(profile, 200, "resolving the writer's permissions");
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String permission : JsonPath.<List<String>>read(profile.getContentAsString(), "$.permissionCodes")) authorities.add(new SimpleGrantedAuthority(permission));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + com.forcitis.one.platform.identity.api.SessionRoles.ORDINARY));
        actInSession(signedIn("writer"));
        Authentication login = SecurityContextHolder.getContext().getAuthentication();
        var refresh = login.getClass().getDeclaredMethod("withAuthorities", java.util.Collection.class, boolean.class);
        refresh.setAccessible(true);
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication((Authentication) refresh.invoke(login, authorities, true));
        SecurityContextHolder.setContext(security);
        return OrgStructureCommandsProbe.of(context, AccountsAcceptanceSupport::employeeIdOf);
    }

    @Test
    @DisplayName("#1172 完成标准第 2 条：列表和详情显示组织与岗位命令的种类、目标、生效日、状态与会签状态")
    void orgAndPositionCommandContent() throws Exception {
        for (Map<String, Object> row : List.of(row("all", commands.get("a")), listed("all", commands.get("a")))) {
            assertThat(row).containsEntry("commandKind", "SET_ATTRIBUTES").containsEntry("targetKind", "ORG_UNIT")
                    .containsEntry("targetOrgUnitId", unitsByCode.get("READ-A")).containsEntry("effectiveDate", DUE.toString()).containsEntry("status", "PENDING");
            List<Object> countersigns = JsonPath.parse(row).read("$.countersigns");
            assertThat(countersigns).isEmpty();
        }
        for (Map<String, Object> row : List.of(row("all", positionCommand), listed("all", positionCommand))) {
            assertThat(row).containsEntry("commandKind", "DISABLE").containsEntry("targetKind", "POSITION")
                    .containsEntry("targetPositionId", targetPosition.toString()).containsEntry("effectiveDate", DUE.toString()).containsEntry("status", "PENDING");
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 2 条：已有会签的组织命令在列表和详情显示 AWAITING_ATTRIBUTION_COUNTERSIGN 与每个待签决定")
    void anExistingCountersignIsVisible() throws Exception {
        for (Map<String, Object> row : List.of(row("all", awaiting), listed("all", awaiting))) {
            assertThat(row).containsEntry("status", "AWAITING_ATTRIBUTION_COUNTERSIGN");
            List<Map<String, Object>> requests = JsonPath.parse(row).read("$.countersigns");
            assertThat(requests).hasSize(2).allSatisfy(request -> {
                assertThat(request).containsEntry("requestKind", "CHANGE");
                assertThat(request.get("decisionKind")).as("an undecided request has no decision kind").isNull();
                assertThat(request.get("attributionCountersignId")).isNotNull();
            });
            assertThat(requests).extracting(request -> request.get("legalEntityId")).containsExactlyInAnyOrder(fixtureEntity.toString(), otherEntity.toString());
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 2 条：合并命令显示真实已转与受影响人数")
    void aMergeShowsProgress() throws Exception {
        for (Map<String, Object> row : List.of(row("all", merged), listed("all", merged))) {
            assertThat(row).containsEntry("commandKind", "MERGE").containsEntry("targetOrgUnitId", unitsByCode.get("READ-B")).containsEntry("status", "EFFECTIVE");
            Map<String, Object> progress = JsonPath.parse(row).read("$.mergeProgress");
            assertThat(progress).containsEntry("affectedEmployeeCount", 1).containsEntry("transferredEmployeeCount", 1);
        }
    }

    @Test
    @DisplayName("#1172 完成标准第 2、4 条（A1/A4 全公司）：持录入权限的人不论数据范围（本部门、子树、自选部门、空范围、范围算不出来）都看到全部组织与岗位命令，列表与详情一致；未知 id 是 404 且错误码以 _NOT_FOUND 结尾")
    void everyHolderOfTheCreatePermissionSeesEveryCommand() throws Exception {
        require(read("broken", CURRENT_SESSION), 200, "priming the cached principal");
        String employee = employeeIdOf("broken");
        org.mockito.Mockito.doThrow(new IllegalStateException("Synthetic directory failure")).when(organisation)
                .findEmployee(org.mockito.ArgumentMatchers.argThat(id -> id != null && employee.equals(id.value().toString())));
        for (String reader : List.of("own", "subtree", "custom", "empty", "receiver", "recorder", "broken")) {
            assertThat(ids(all(reader, ""))).as(reader + " lists every org-structure command").containsAll(commandUnits.keySet());
            for (String id : commandUnits.keySet()) {
                assertThat(row(reader, id).get(idField())).as(reader + " reads " + id).isEqualTo(id);
            }
        }
        MockHttpServletResponse unknown = read("custom", resource() + "/" + UUID.randomUUID());
        require(unknown, 404, "an unknown command");
        assertThat(errorCode(unknown)).endsWith("_NOT_FOUND");
    }
}
