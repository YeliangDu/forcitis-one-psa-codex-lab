package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalField;
import com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.AssignmentRule;
import com.forcitis.one.platform.approval.domain.AssignmentRuleCodec;
import com.forcitis.one.platform.approval.domain.NodeDeadline;
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
        assertThat(policy.beforeHookKey()).contains("K");
    }

    @Test
    @DisplayName("RULE-262 a node without a rule is a hard error, not a silent skip")
    void noRuleFails() {
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(""))))
                .isInstanceOf(DomainException.class).hasMessageContaining("no forcitis:assignmentRule");
    }

    @Test
    @DisplayName("RULE-262 a node with two rules is a hard error")
    void twoRulesFail() {
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse("<extensionElements>"
                + "<forcitis:assignmentRule type=\"ROLE\" role=\"a\"/><forcitis:assignmentRule type=\"ROLE\" role=\"b\"/></extensionElements>"))))
                .isInstanceOf(DomainException.class).hasMessageContaining("2 forcitis:assignmentRule");
    }

    @Test
    @DisplayName("an unknown rule type or a missing attribute names the node in the error")
    void invalidRuleNamesTheNode() {
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"PICK_ONE_AT_RANDOM\"/></extensionElements>"))))
                .isInstanceOf(DomainException.class).hasMessageContaining("review").hasMessageContaining("PICK_ONE_AT_RANDOM");
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"FORM_FIELD_USERS\"/></extensionElements>"))))
                .isInstanceOf(DomainException.class).hasMessageContaining("field");
    }

    @Test
    @DisplayName("every deployed process file of the code base passes the conventions (also checked at startup)")
    void shippedProcessesAreValid() throws Exception {
        for (Path file : shippedProcessFiles()) {
            try (InputStream in = Files.newInputStream(file)) {
                BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
                model.getMainProcess().findFlowElementsOfType(UserTask.class).forEach(NodePolicyReader::read);
            }
        }
    }

    private static List<Path> shippedProcessFiles() throws java.io.IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/processes"))) {
            List<Path> found = files.filter(f -> f.toString().endsWith(".bpmn20.xml")).sorted().toList();
            assertThat(found).as("shipped process files").isNotEmpty();
            return found;
        }
    }

    @Test
    @DisplayName("ADR-0066 decision 4: every process file declares exactly one forcitis:documentType code, a well-formed DocumentTypeCode")
    void everyProcessDeclaresItsDocumentType() throws Exception {
        for (Path file : shippedProcessFiles()) {
            try (InputStream in = Files.newInputStream(file)) {
                BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
                List<org.flowable.bpmn.model.ExtensionElement> declared = model.getMainProcess().getExtensionElements().get("documentType");
                assertThat(declared).as("forcitis:documentType of " + file.getFileName()).hasSize(1);
                assertThat(new com.forcitis.one.kernel.DocumentTypeCode(declared.getFirst().getAttributeValue(null, "code")).value()).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("REG-4: every process file carries the name a person reads and declares forcitis:routingFlagRequired, and its id is a ProcessKey")
    void everyProcessDeclaresItsNameAndRoutingFlagRule() throws Exception {
        for (Path file : shippedProcessFiles()) {
            try (InputStream in = Files.newInputStream(file)) {
                BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
                ProcessKey key = ProcessKey.of(model.getMainProcess().getId());
                assertThat(ProcessDefinitionDeclarations.nameOf(key, model.getMainProcess()).value()).isNotBlank();
                ProcessDefinitionDeclarations.routingFlagRequired(key, model.getMainProcess());
            }
        }
    }

    @Test
    @DisplayName("1a catalogue: a BPMN node that uses a deferred or a not-offered rule type does not load, and names the node and the type")
    void deferredAndNotOfferedTypesDoNotLoad() {
        for (String type : Stream.concat(AssignmentRuleCodec.DEFERRED_TYPES.stream(), AssignmentRuleCodec.NOT_BUILT_TYPES.stream()).toList()) {
            assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                    "<extensionElements><forcitis:assignmentRule type=\"" + type + "\" role=\"r\" field=\"f\" users=\"1\" variable=\"v\"/></extensionElements>"))))
                    .as(type).isInstanceOf(DomainException.class).hasMessageContaining("review").hasMessageContaining(type);
        }
        assertThatThrownBy(() -> NodePolicyReader.read(review(parse(
                "<extensionElements><forcitis:assignmentRule type=\"INITIATOR\"/></extensionElements>"))))
                .isInstanceOf(DomainException.class).hasMessageContaining("INITIATOR");
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
    @DisplayName("H2 D-1a-100 the timesheet BPMN uses exactly the names cost.timesheet publishes: its form fields and its routing flags, and no decision table exists")
    void timesheetProcessUsesThePublishedNames() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml"));
        List<String> fields = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("type=\"FORM_FIELD_USERS\" field=\"([^\"]+)\"").matcher(xml);
        while (m.find()) {
            fields.add(m.group(1));
        }

        assertThat(fields).containsExactlyInAnyOrder(TimesheetApprovalField.PROJECT_MANAGER.fieldName(), TimesheetApprovalField.ORG_UNIT_HEAD.fieldName());
        List<String> flags = new java.util.ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/processes/cost_timesheet_approval.bpmn20.xml")) {
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
            model.getMainProcess().findFlowElementsOfType(UserTask.class)
                    .forEach(node -> NodePolicyReader.read(node).routingFlag().ifPresent(flags::add));
        }
        assertThat(flags).containsExactlyInAnyOrder(java.util.Arrays.stream(TimesheetRoutingFlag.values()).map(TimesheetRoutingFlag::flagName).toArray(String[]::new));
        assertThat(Path.of("src/main/resources/dmn")).as("1a has no decision table").doesNotExist();
    }

    @Test
    @DisplayName("RULE-248/300 D-1a-84 every node of the timesheet process is a countersign node with explicit skip, repeat-approver and paired deadline attributes")
    void timesheetNodesAreCountersignWithExplicitAttributes() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/processes/cost_timesheet_approval.bpmn20.xml")) {
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false);
            List<UserTask> nodes = model.getMainProcess().findFlowElementsOfType(UserTask.class);
            assertThat(nodes).hasSize(3);
            nodes.forEach(node -> {
                NodePolicy policy = NodePolicyReader.read(node);
                assertThat(policy.skipWhenEmpty()).as(node.getId()).isTrue();
                assertThat(policy.autoApproveRepeat()).as(node.getId()).isTrue();
                assertThat(policy.deadline()).as(node.getId()).contains(new NodeDeadline(1, 1));
                assertThat(node.getLoopCharacteristics()).as(node.getId()).isNotNull();
                assertThat(node.getLoopCharacteristics().isSequential()).isFalse();
                assertThat(node.getLoopCharacteristics().getInputDataItem()).isEqualTo("${approverResolution.apply(execution)}");
                assertThat(node.getLoopCharacteristics().getElementVariable()).isEqualTo("approver");
            });
        }
    }

    @Test
    @DisplayName("D-1a-84 M-10 a deadline needs its escalation interval and the reverse; both are whole workdays of at least one")
    void deadlineParametersComeAsAPair() {
        assertThat(NodePolicyReader.read(review(parse("<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\""
                + " deadlineWorkdays=\"2\" escalationWorkdays=\"3\"/></extensionElements>"))).deadline()).contains(new NodeDeadline(2, 3));
        assertThat(NodePolicyReader.read(review(parse("<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\"/></extensionElements>")))
                .deadline()).isEmpty();
        for (String unpaired : List.of("deadlineWorkdays=\"1\"", "escalationWorkdays=\"1\"")) {
            assertThatThrownBy(() -> NodePolicyReader.read(review(parse("<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\" "
                    + unpaired + "/></extensionElements>")))).as(unpaired).isInstanceOf(DomainException.class)
                    .hasMessageContaining("review").hasMessageContaining("pair");
        }
        for (String bad : List.of("deadlineWorkdays=\"0\" escalationWorkdays=\"1\"", "deadlineWorkdays=\"1\" escalationWorkdays=\"-2\"",
                "deadlineWorkdays=\"x\" escalationWorkdays=\"1\"", "deadlineWorkdays=\"1.5\" escalationWorkdays=\"1\"")) {
            assertThatThrownBy(() -> NodePolicyReader.read(review(parse("<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\" "
                    + bad + "/></extensionElements>")))).as(bad).isInstanceOf(DomainException.class).hasMessageContaining("review");
        }
    }

    @Test
    @DisplayName("G-1 an attribute that is neither true nor false does not read as false: the node does not load")
    void aFlagMustBeTrueOrFalse() {
        for (String attribute : List.of("skipWhenEmpty", "autoApproveRepeat", "cutoff", "noFallback")) {
            assertThatThrownBy(() -> NodePolicyReader.read(review(parse("<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\" "
                    + attribute + "=\"yes\"/></extensionElements>")))).as(attribute).isInstanceOf(DomainException.class)
                    .hasMessageContaining(attribute);
        }
        NodePolicy policy = NodePolicyReader.read(review(parse("<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"r\""
                + " routingFlag=\"projectHours\" skipWhenEmpty=\"true\" autoApproveRepeat=\"true\" cutoff=\"true\" noFallback=\"true\"/></extensionElements>")));
        assertThat(policy.noFallback()).isTrue();
        assertThat(policy.routingFlag()).contains("projectHours");
        assertThat(policy.skipWhenEmpty()).isTrue();
        assertThat(policy.autoApproveRepeat()).isTrue();
        assertThat(policy.cutoff()).isTrue();
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
        String first = TestAccounts.employee(1).toString();
        String second = TestAccounts.employee(2).toString();
        String initiator = TestAccounts.employee(7).toString();
        ProcessVariables in = new ProcessVariables(Map.of("projectManagerId", List.of(first, second)),
                Map.of("initiatorId", List.of(initiator), "needs-seal", List.of("true")));
        Map<String, Object> engine = EngineVariables.toEngine(in);
        assertThat(engine).containsEntry("field:projectManagerId", first + "\n" + second).containsEntry("initiatorId", initiator)
                .containsEntry("needs-seal", Boolean.TRUE);
        ProcessVariables back = EngineVariables.fromEngine(engine);
        assertThat(back.formField("projectManagerId")).containsExactly(first, second);
        assertThat(back.variable("initiatorId")).containsExactly(initiator);
        assertThat(back.variable("needs-seal")).containsExactly("true");
        ProcessVariables clash = new ProcessVariables(Map.of("initiatorId", List.of(TestAccounts.employee(999).toString())),
                Map.of("initiatorId", List.of(initiator)));
        assertThat(EngineVariables.fromEngine(EngineVariables.toEngine(clash)).variable("initiatorId")).containsExactly(initiator);
    }
}
