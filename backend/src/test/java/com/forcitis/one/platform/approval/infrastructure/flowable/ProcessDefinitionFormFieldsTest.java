package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessDefinitionFormFieldsTest {

    private static Process process(String extension) {
        String xml = """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:forcitis="https://forcitis.com/psa/approval/extension"
                             xmlns:other="https://example.com/other" targetNamespace="x">
                  <process id="cost_timesheet_approval" isExecutable="true">%s
                    <startEvent id="start"/>
                  </process>
                </definitions>
                """.formatted(extension);
        return new BpmnXMLConverter().convertToBpmnModel(new InputStreamSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))), false, false).getMainProcess();
    }

    @Test
    @DisplayName("#1436 the data-only form fields a process declares are read, none is fine, a foreign-namespace one is ignored")
    void declaredNamesAreRead() {
        assertThat(ProcessDefinitionDeclarations.dataFormFields(TimesheetApprovalProcess.KEY, process(
                "<extensionElements><forcitis:formField name=\"a\"/><forcitis:formField name=\"b\"/><other:formField name=\"c\"/></extensionElements>")))
                .containsExactly("a", "b");
        assertThat(ProcessDefinitionDeclarations.dataFormFields(TimesheetApprovalProcess.KEY, process(""))).isEmpty();
    }

    @Test
    @DisplayName("#1436 a blank, a name-less or a repeated forcitis:formField is a classified configuration fault naming the process")
    void aBlankOrRepeatedNameIsRefused() {
        for (String extension : new String[] {"<extensionElements><forcitis:formField name=\"\"/></extensionElements>",
                "<extensionElements><forcitis:formField/></extensionElements>",
                "<extensionElements><forcitis:formField name=\"a\"/><forcitis:formField name=\"a\"/></extensionElements>"}) {
            assertThatThrownBy(() -> ProcessDefinitionDeclarations.dataFormFields(TimesheetApprovalProcess.KEY, process(extension)))
                    .as(extension).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID))
                    .hasMessageContaining("cost_timesheet_approval");
        }
    }
}
