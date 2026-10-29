package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.acceptance.P4Names;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.approval.api.ApprovalField;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * R-1b P4 ARCH-D5 D5-06 / RULE-389 (acceptance items 11 and 12): the pre-sales node of the timesheet approval is the org unit head's
 * approval. A start that carries the pre-sales flag and the org unit head field gets a task at that node for that person; the old node
 * id and field name are gone from the process definition. A start that names a form field the process does not know (above all the
 * old regionalHeadId) fails with an alert instead of letting the node be skipped as empty (RULING-P4-D10, CLAUDE.md rule 6).
 * Only the names changed: the people are still named by the submitter (FORM_FIELD_USERS, D-9).
 */
class OrgUnitHeadApprovalIT extends ApprovalP4Support {

    static final TestDatabase postgres = TestPostgres.database(OrgUnitHeadApprovalIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static final String PROCESS_RESOURCE = "/processes/cost_timesheet_approval.bpmn20.xml";

    private static ApprovalField orgUnitHead(UUID person) {
        return new ApprovalField(P4Names.ORG_UNIT_HEAD_FIELD, List.of(text(person)));
    }

    private int rowsOf(String table, DocumentReference document) throws Exception {
        String key = table.equals("approval_requests") ? "t." + P4Names.DOCUMENT_ID : "r." + P4Names.DOCUMENT_ID;
        String sql = table.equals("approval_requests")
                ? "select count(*) as n from approval_requests t where " + key + " = ?"
                : "select count(*) as n from " + table + " t join approval_requests r on r.id = t." + P4Names.APPROVAL_REQUEST_ID + " where " + key + " = ?";
        return ((Number) rows(sql, uuidOf(document)).getFirst().get("n")).intValue();
    }

    @Test
    @DisplayName("R-1b P4 D5-06 / RULE-389：售前工时节点名为「部门负责人审批」，按 orgUnitHeadId 取审批人——带 preSalesHours 旗标与该字段人员时，该节点为该人建任务，节点名称为「部门负责人审批」")
    void thePreSalesNodeIsTheOrgUnitHeadsApproval() throws Exception {
        DocumentReference document = newDocument();

        start(document, Set.of(P4Names.PRE_SALES_FLAG), orgUnitHead(orgUnitHead));

        List<Map<String, Object>> tasks = rows("select task_id, node_key, node_name, assignee_id from approval_tasks t join approval_requests r on r.id = t."
                + P4Names.APPROVAL_REQUEST_ID + " where r." + P4Names.DOCUMENT_ID + " = ?", uuidOf(document));
        assertThat(tasks).hasSize(1);
        assertThat(tasks.getFirst().get("node_key")).isEqualTo(P4Names.ORG_UNIT_HEAD_NODE);
        assertThat(tasks.getFirst().get("node_name")).isEqualTo(P4Names.ORG_UNIT_HEAD_NODE_NAME);
        MockHttpServletResponse seen = detail(tasks.getFirst().get("task_id"), orgUnitHeadAccount);
        assertThat(seen.getStatus()).as(seen.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(seen.getContentAsString(), "$.nodeName")).isEqualTo(P4Names.ORG_UNIT_HEAD_NODE_NAME);
        assertThat((String) JsonPath.read(seen.getContentAsString(), "$.nodeKey")).isEqualTo(P4Names.ORG_UNIT_HEAD_NODE);
        assertThat((List<String>) JsonPath.read(seen.getContentAsString(), "$.candidates")).containsExactly(orgUnitHeadAccount.userId().toString());
        assertThat(detail(tasks.getFirst().get("task_id"), projectManagerAccount).getStatus()).as("someone else").isEqualTo(404);
    }

    @Test
    @DisplayName("R-1b P4 D5-06：工时同时带项目工时与售前工时旗标时，项目经理节点与部门负责人节点各建任务；节点只有这两个")
    void bothNodesRunSideBySide() throws Exception {
        DocumentReference document = newDocument();

        start(document, Set.of(P4Names.PROJECT_HOURS_FLAG, P4Names.PRE_SALES_FLAG), projectManagers(projectManager), orgUnitHead(orgUnitHead));

        assertThat(rows("select node_key from approval_tasks t join approval_requests r on r.id = t." + P4Names.APPROVAL_REQUEST_ID + " where r."
                + P4Names.DOCUMENT_ID + " = ?", uuidOf(document))).extracting(row -> row.get("node_key"))
                .containsExactlyInAnyOrder("projectManagerReview", P4Names.ORG_UNIT_HEAD_NODE);
    }

    @Test
    @DisplayName("R-1b P4 D5-06：流程定义中不再出现 regionalHeadReview、regionalHeadId、routed_regionalHeadReview 与「区域负责人审批」，已部署的定义同样没有")
    void theOldNamesAreGoneFromTheProcessDefinition() throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream(PROCESS_RESOURCE)) {
            assertThat(in).as("the process definition on the classpath").isNotNull();
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new AssertionError(unreadable);
        }

        assertThat(xml).doesNotContain(P4Names.REGIONAL_HEAD_NODE, P4Names.REGIONAL_HEAD_FIELD, "RegionalHead", "区域负责人");
        assertThat(xml).contains("id=\"" + P4Names.ORG_UNIT_HEAD_NODE + "\"", "name=\"" + P4Names.ORG_UNIT_HEAD_NODE_NAME + "\"",
                "field=\"" + P4Names.ORG_UNIT_HEAD_FIELD + "\"", "routed_" + P4Names.ORG_UNIT_HEAD_NODE);

        List<Map<String, Object>> deployed = rows("select convert_from(bytes_, 'UTF8') as xml from act_ge_bytearray where name_ like '%cost_timesheet_approval%'");
        assertThat(deployed).as("the deployed definition").isNotEmpty().allSatisfy(row -> assertThat((String) row.get("xml"))
                .doesNotContain(P4Names.REGIONAL_HEAD_NODE, P4Names.REGIONAL_HEAD_FIELD));
    }

    @Test
    @DisplayName("R-1b P4 D5-06 / RULING-P4-D10：表单只带旧字段名 regionalHeadId（或任何流程未登记的字段名）时发起失败并告警，不静默跳过部门负责人节点；不写审批与任务")
    void aFormFieldTheProcessDoesNotKnowFailsTheStart() throws Exception {
        for (String unknown : List.of(P4Names.REGIONAL_HEAD_FIELD, "someOtherField")) {
            DocumentReference document = newDocument();
            int mark = logMark();

            Throwable failure = catchThrowable(() -> start(document, Set.of(P4Names.PRE_SALES_FLAG),
                    new ApprovalField(unknown, List.of(text(orgUnitHead)))));

            assertThat(failure).as("starting with the form field %s", unknown).isNotNull();
            assertThat(alertsSince(mark)).as("an alert for %s", unknown).isNotEmpty();
            assertThat(rowsOf("approval_requests", document)).as("no approval is written for %s", unknown).isZero();
            assertThat(rowsOf("approval_tasks", document)).as("no task is written for %s", unknown).isZero();
        }
    }

    @Test
    @DisplayName("R-1b P4 D5-06 / RULING-P4-D10（对照）：旧字段名与新字段名一起带时同样失败；只带已登记字段的发起不受影响")
    void anOldFieldBesideTheNewOneStillFailsAndKnownFieldsStillWork() throws Exception {
        DocumentReference mixed = newDocument();
        int mark = logMark();

        Throwable failure = catchThrowable(() -> start(mixed, Set.of(P4Names.PRE_SALES_FLAG), orgUnitHead(orgUnitHead),
                new ApprovalField(P4Names.REGIONAL_HEAD_FIELD, List.of(text(orgUnitHead)))));

        assertThat(failure).isNotNull();
        assertThat(alertsSince(mark)).isNotEmpty();
        assertThat(rowsOf("approval_requests", mixed)).isZero();

        DocumentReference known = startProjectHours(projectManager);
        assertThat(rowsOf("approval_tasks", known)).isEqualTo(1);
    }
}
