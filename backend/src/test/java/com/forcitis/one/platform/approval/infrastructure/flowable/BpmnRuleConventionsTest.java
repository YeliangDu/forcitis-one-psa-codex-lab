package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.AssignmentRule;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import com.forcitis.one.platform.approval.domain.ProcessVariables;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
                "<extensionElements><forcitis:assignmentRule type=\"ROLE\" role=\"BU-002\" selfApproval=\"allowed\" beforeHook=\"K\"/></extensionElements>")));
        assertThat(policy.rule()).isInstanceOf(AssignmentRule.Role.class);
        assertThat(policy.selfApprovalAllowed()).isTrue();
        assertThat(policy.initiatorNode()).isFalse();
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
                + "<forcitis:assignmentRule type=\"INITIATOR\"/><forcitis:assignmentRule type=\"INITIATOR\"/></extensionElements>"))))
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
                assertThat(FlowableApprovalEngine.initiatorNodeKey(model)).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("business fields never shadow engine variables; booleans round-trip; multi-valued fields survive")
    void engineVariableMapping() {
        ProcessVariables in = new ProcessVariables(Map.of("manager_id", List.of("1", "2")),
                Map.of("initiatorId", List.of("7"), "needs-seal", List.of("true")));
        Map<String, Object> engine = EngineVariables.toEngine(in);
        assertThat(engine).containsEntry("field:manager_id", "1\n2").containsEntry("initiatorId", "7").containsEntry("needs-seal", Boolean.TRUE);
        ProcessVariables back = EngineVariables.fromEngine(engine);
        assertThat(back.formField("manager_id")).containsExactly("1", "2");
        assertThat(back.variable("initiatorId")).containsExactly("7");
        assertThat(back.variable("needs-seal")).containsExactly("true");
        // a business field called initiatorId stays a business field
        ProcessVariables clash = new ProcessVariables(Map.of("initiatorId", List.of("999")), Map.of("initiatorId", List.of("7")));
        assertThat(EngineVariables.fromEngine(EngineVariables.toEngine(clash)).variable("initiatorId")).containsExactly("7");
    }
}
