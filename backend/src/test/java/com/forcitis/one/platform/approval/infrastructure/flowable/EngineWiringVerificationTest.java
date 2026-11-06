package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

/** The start-up check collects every fault of every process definition and fails the start with all of them (ADR-0066 decision 4). */
class EngineWiringVerificationTest {

    private static final String HEAD = """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:forcitis="https://forcitis.com/psa/approval/extension" targetNamespace="x">
              <process id="cost_timesheet_approval" name="工时审批" isExecutable="true">""";

    private static EngineWiring wiringFor(String processBody) {
        RepositoryService repository = mock(RepositoryService.class);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(
                new ByteArrayInputStream((HEAD + processBody + "</process></definitions>").getBytes(StandardCharsets.UTF_8))), false, false);
        when(repository.createProcessDefinitionQuery()).thenReturn(query);
        when(query.list()).thenReturn(java.util.List.of(definition));
        when(definition.getKey()).thenReturn("cost_timesheet_approval");
        when(definition.getId()).thenReturn("d1");
        when(repository.getBpmnModel("d1")).thenReturn(model);
        return new EngineWiring(null, repository, null, null);
    }

    private static final String TASK_WITHOUT_RULE = "<startEvent id=\"s\"/><userTask id=\"review\" name=\"Review\"/>";

    @Test
    @DisplayName("a process without its document type fails the start, and the faulty node is reported with it")
    void missingDeclarationAndBadNodeAreBothReported() {
        assertThatThrownBy(() -> wiringFor(TASK_WITHOUT_RULE).verifyDefinitions())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid approval process definitions")
                .hasMessageContaining("forcitis:documentType").hasMessageContaining("Node review");
    }

    @Test
    @DisplayName("a malformed document type code fails the start")
    void malformedCodeFailsTheStart() {
        assertThatThrownBy(() -> wiringFor("<extensionElements><forcitis:documentType code=\"timesheet\"/></extensionElements><startEvent id=\"s\"/>").verifyDefinitions())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("malformed");
    }

    @Test
    @DisplayName("a declared document type and no faulty node passes")
    void aDeclaredTypeAndNoFaultPasses() {
        assertThatCode(() -> wiringFor("<extensionElements><forcitis:documentType code=\"TIMESHEET\"/><forcitis:routingFlagRequired value=\"false\"/></extensionElements><startEvent id=\"s\"/>").verifyDefinitions())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REG-4: a process that does not say whether it needs routing flags fails the start, never a default")
    void missingRoutingFlagDeclarationFailsTheStart() {
        assertThatThrownBy(() -> wiringFor("<extensionElements><forcitis:documentType code=\"TIMESHEET\"/></extensionElements><startEvent id=\"s\"/>").verifyDefinitions())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("forcitis:routingFlagRequired");
        assertThatThrownBy(() -> wiringFor("<extensionElements><forcitis:documentType code=\"TIMESHEET\"/><forcitis:routingFlagRequired value=\"maybe\"/></extensionElements><startEvent id=\"s\"/>").verifyDefinitions())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("forcitis:routingFlagRequired");
    }

    @Test
    @DisplayName("#1546: a process that can re-enter a countersign node fails the start")
    void aLoopingCountersignFailsTheStart() {
        assertThatThrownBy(() -> wiringFor("<extensionElements><forcitis:documentType code=\"TIMESHEET\"/><forcitis:routingFlagRequired value=\"false\"/></extensionElements>"
                + "<startEvent id=\"s\"/><userTask id=\"cs\"><multiInstanceLoopCharacteristics isSequential=\"false\"/></userTask>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"cs\"/><sequenceFlow id=\"f2\" sourceRef=\"cs\" targetRef=\"cs\"/>").verifyDefinitions())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Invalid approval process definitions").hasMessageContaining("countersign node cs");
    }
}
