package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.AssignmentRule;
import com.forcitis.one.platform.approval.domain.AssignmentRuleCodec;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.List;
import java.util.Map;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The BPMN conventions that need no engine: one assignment rule per user task (RULE-262), strict rule types, variable mapping. */
class BpmnRuleConventionsTest {

    static BpmnModel parse(String userTaskBody) {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:forcitis="https://forcitis.com/psa/approval/extension"
                             targetNamespace="x">
                  <process id="cost_timesheet_approval" isExecutable="true">
                    <startEvent id="start"/>
                    <sequenceFlow id="f1" sourceRef="start" targetRef="review"/>
                    <userTask id="review" name="Review">%s</userTask>
                    <sequenceFlow id="f2" sourceRef="review" targetRef="end"/>
                    <endEvent id="end"/>
                  </process>
                </definitions>
                """.formatted(userTaskBody);
        InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        return new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
    }

    static UserTask review(BpmnModel model) {
        return (UserTask) model.getFlowElement("review");
    }

    @Test
    @DisplayName("RULE-262 a node with exactly one rule is read with all its options")
    void oneRuleIsRead() {
        NodePolicy policy = NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"reviewer\" selfApproval=\"allowed\" beforeHook=\"K\"/></extensionElements>")));
        assertThat(policy.rule()).isInstanceOf(AssignmentRule.Role.class);
        assertThat(policy.selfApprovalAllowed()).isTrue();
        assertThat(policy.beforeHookKey()).isEqualTo("K");
    }

    @Test
    @DisplayName("RULE-262 a node without a rule is a hard error, not a silent skip")
    void noRuleFails() {
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(""))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no forcitis:assignmentRule");
    }

    @Test
    @DisplayName("RULE-262 a node with two rules is a hard error")
    void twoRulesFail() {
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse("<extensionElements>"
                + "<forcitis:assignmentRule type=\"ROLE\" role=\"a\"/><forcitis:assignmentRule type=\"ROLE\" role=\"b\"/></extensionElements>"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2 forcitis:assignmentRule");
    }

    @Test
    @DisplayName("an unknown rule type or a missing attribute names the node in the error")
    void invalidRuleNamesTheNode() {
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"PICK_ONE_AT_RANDOM\"/></extensionElements>"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("review").hasMessageContaining("PICK_ONE_AT_RANDOM");
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"FORM_FIELD_USERS\"/></extensionElements>"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("field");
    }

    @Test
    @DisplayName("every deployed process file of the code base passes the conventions (also checked at startup)")
    void shippedProcessesAreValid() throws Exception {
        for (ProcessKey key : ProcessKey.values()) {
            try (InputStream in = getClass().getResourceAsStream("/processes/" + key.key() + ".bpmn20.xml")) {
                assertThat(in).as("BPMN file of " + key).isNotNull();
                BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
                model.getMainProcess().findFlowElementsOfType(UserTask.class).forEach(NodePolicyReader::read);
            }
        }
    }

    @Test
    @DisplayName("1a catalogue: a BPMN node that uses a deferred or a not-offered rule type does not load, and names the node and the type")
    void deferredAndNotOfferedTypesDoNotLoad() {
        for (String type : Stream.concat(AssignmentRuleCodec.DEFERRED_TYPES.stream(), AssignmentRuleCodec.NOT_BUILT_TYPES.stream()).toList()) {
            assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                    "<extensionElements><forcitis:assignmentRule type=\"" + type + "\" role=\"r\" field=\"f\" users=\"1\" variable=\"v\"/></extensionElements>"))))
                    .as(type).isInstanceOf(IllegalStateException.class).hasMessageContaining("review").hasMessageContaining(type);
        }
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"INITIATOR\"/></extensionElements>"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INITIATOR");
    }

    @Test
    @DisplayName("1a catalogue: no BPMN file of the code base (shipped or test variant) names a deferred or not-offered rule type or an initiator rework node")
    void noProcessFileUsesARemovedType() throws Exception {
        List<String> removed = Stream.concat(AssignmentRuleCodec.DEFERRED_TYPES.stream(), AssignmentRuleCodec.NOT_BUILT_TYPES.stream())
                .map(t -> "type=\"" + t + "\"").collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        removed.add("type=\"INITIATOR\"");
        removed.add("initiatorNode");
        removed.add("skipExpression");
        List<Path> files = new java.util.ArrayList<>();
        for (Path root : List.of(Path.of("src/main/resources/processes"), Path.of("src/test/resources"))) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(f -> f.getFileName().toString().endsWith(".bpmn20.xml")).forEach(files::add);
            }
        }
        assertThat(files).isNotEmpty();
        for (Path file : files) {
            String xml = Files.readString(file);
            for (String name : removed) {
                assertThat(xml).as("%s uses %s", file, name).doesNotContain(name);
            }
        }
    }

    @Test
    @DisplayName("H2 the timesheet BPMN uses exactly the names cost.timesheet publishes: its form fields and its routing flag")
    void timesheetProcessUsesThePublishedNames() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml"));
        List<String> fields = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("type=\"FORM_FIELD_USERS\" field=\"([^\"]+)\"").matcher(xml);
        while (m.find()) {
            fields.add(m.group(1));
        }

        assertThat(fields).containsExactlyInAnyOrder(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), TimesheetApprovalField.REGIONAL_HEAD.fieldName());
        for (TimesheetRoutingFlag flag : TimesheetRoutingFlag.values()) {
            assertThat(xml).contains("getOrDefault('" + flag.flagName() + "', false)");
        }
    }

    @Test
    @DisplayName("RULE-300 every node of the timesheet process says explicitly that it may be skipped when nobody is found")
    void timesheetNodesAllowSkipping() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/processes/cost_timesheet_approval.bpmn20.xml")) {
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
            List<UserTask> nodes = model.getMainProcess().findFlowElementsOfType(UserTask.class);
            assertThat(nodes).hasSize(3);
            nodes.forEach(node -> assertThat(NodePolicyReader.read(node).skipWhenEmpty()).as(node.getId()).isTrue());
        }
    }

    @Test
    @DisplayName("RULE-300 a node without skipWhenEmpty does not skip")
    void skippingIsOptIn() {
        assertThat(NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\"/></extensionElements>"))).skipWhenEmpty()).isFalse();
    }

    @Test
    @DisplayName("H1 DEPARTMENT_HEAD reads an optional department; nothing about a role or a default variable is implied")
    void departmentHeadNodeHasAnOptionalDepartment() {
        NodePolicy withoutDepartment = NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"DEPARTMENT_HEAD\"/></extensionElements>")));
        NodePolicy withDepartment = NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"DEPARTMENT_HEAD\" department=\"deliveryUnitId\"/></extensionElements>")));

        assertThat(withoutDepartment.rule()).isEqualTo(new AssignmentRule.DepartmentHead(null));
        assertThat(withDepartment.rule()).isEqualTo(new AssignmentRule.DepartmentHead("deliveryUnitId"));
    }

    @Test
    @DisplayName("business fields never shadow engine variables; booleans round-trip; multi-valued fields survive")
    void engineVariableMapping() {
        ProcessVariables in = new ProcessVariables(Map.of("projectManagerId", List.of("1", "2")),
                Map.of("initiatorId", List.of("7"), "needs-seal", List.of("true")));
        Map<String, Object> engine = EngineVariables.toEngine(in);
        assertThat(engine).containsEntry("field:projectManagerId", "1\n2").containsEntry("initiatorId", "7").containsEntry("needs-seal", Boolean.TRUE);
        ProcessVariables back = EngineVariables.fromEngine(engine);
        assertThat(back.formField("projectManagerId")).containsExactly("1", "2");
        assertThat(back.variable("initiatorId")).containsExactly("7");
        assertThat(back.variable("needs-seal")).containsExactly("true");
        // a business field called initiatorId stays a business field
        ProcessVariables clash = new ProcessVariables(Map.of("initiatorId", List.of("999")), Map.of("initiatorId", List.of("7")));
        assertThat(EngineVariables.fromEngine(EngineVariables.toEngine(clash)).variable("initiatorId")).containsExactly("7");
    }
}
