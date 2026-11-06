package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1546: a countersign node is entered once, because the one-share-per-person rule cannot tell a second visit from the same round. */
class CountersignEnteredOnceTest {

    private static final String COUNTERSIGN = "<userTask id=\"%s\"><multiInstanceLoopCharacteristics isSequential=\"false\"/></userTask>";

    private static Process process(String body) {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="x">
                  <process id="cost_timesheet_approval" isExecutable="true">%s</process>
                </definitions>
                """.formatted(body);
        return new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))), false, false).getMainProcess();
    }

    private static void refused(String body) {
        assertThatThrownBy(() -> ProcessDefinitionDeclarations.requireCountersignEnteredOnce(TimesheetApprovalProcess.KEY, process(body)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID))
                .hasMessageContaining("countersign node cs");
    }

    @Test
    @DisplayName("a straight line through a countersign node is accepted, and so is a loop that does not pass through it")
    void aNonLoopIsAccepted() {
        assertThatCode(() -> ProcessDefinitionDeclarations.requireCountersignEnteredOnce(TimesheetApprovalProcess.KEY, process(
                "<startEvent id=\"s\"/>" + COUNTERSIGN.formatted("cs") + "<userTask id=\"a\"/><userTask id=\"b\"/><endEvent id=\"e\"/>"
                        + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"cs\"/><sequenceFlow id=\"f2\" sourceRef=\"cs\" targetRef=\"a\"/>"
                        + "<sequenceFlow id=\"f3\" sourceRef=\"a\" targetRef=\"b\"/><sequenceFlow id=\"f4\" sourceRef=\"b\" targetRef=\"a\"/>"
                        + "<sequenceFlow id=\"f5\" sourceRef=\"b\" targetRef=\"e\"/>"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a path from a later node back to the countersign node is refused, and so is a self loop and a loop closed through a sub-process")
    void everyWayBackIsRefused() {
        refused("<startEvent id=\"s\"/>" + COUNTERSIGN.formatted("cs") + "<userTask id=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"cs\"/><sequenceFlow id=\"f2\" sourceRef=\"cs\" targetRef=\"a\"/><sequenceFlow id=\"f3\" sourceRef=\"a\" targetRef=\"cs\"/>");
        refused("<startEvent id=\"s\"/>" + COUNTERSIGN.formatted("cs") + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"cs\"/><sequenceFlow id=\"f2\" sourceRef=\"cs\" targetRef=\"cs\"/>");
        refused("<startEvent id=\"s\"/><subProcess id=\"sub\"><startEvent id=\"ss\"/>" + COUNTERSIGN.formatted("cs") + "<endEvent id=\"se\"/>"
                + "<sequenceFlow id=\"g1\" sourceRef=\"ss\" targetRef=\"cs\"/><sequenceFlow id=\"g2\" sourceRef=\"cs\" targetRef=\"se\"/></subProcess><userTask id=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"sub\"/><sequenceFlow id=\"f2\" sourceRef=\"sub\" targetRef=\"a\"/><sequenceFlow id=\"f3\" sourceRef=\"a\" targetRef=\"sub\"/>");
    }

    @Test
    @DisplayName("the shipped approval definitions all pass")
    void theShippedDefinitionsPass() throws IOException {
        for (String file : new String[] {"cost_timesheet_approval", "organization_recruitment_requisition_approval"}) {
            try (var in = getClass().getResourceAsStream("/processes/" + file + ".bpmn20.xml")) {
                Process shipped = new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(in), false, false).getMainProcess();
                assertThatCode(() -> ProcessDefinitionDeclarations.requireCountersignEnteredOnce(TimesheetApprovalProcess.KEY, shipped)).as(file).doesNotThrowAnyException();
            }
        }
    }
}
