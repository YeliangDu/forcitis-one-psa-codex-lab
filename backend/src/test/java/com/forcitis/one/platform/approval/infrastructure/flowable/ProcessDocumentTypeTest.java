package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.Process;
import org.flowable.common.engine.impl.util.io.InputStreamSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessDocumentTypeTest {

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
    @DisplayName("one forcitis:documentType code is read")
    void oneDeclarationIsRead() {
        assertThat(ProcessDocumentType.read(ProcessKey.COST_TIMESHEET_APPROVAL,
                process("<extensionElements><forcitis:documentType code=\"TIMESHEET\"/></extensionElements>")).value()).isEqualTo("TIMESHEET");
    }

    @Test
    @DisplayName("a missing, a doubled, a code-less, a malformed or a foreign-namespace declaration is a classified configuration fault naming the process")
    void everyOtherShapeIsRefused() {
        for (String extension : new String[] {"", "<extensionElements/>",
                "<extensionElements><forcitis:documentType code=\"TIMESHEET\"/><forcitis:documentType code=\"QUOTE\"/></extensionElements>",
                "<extensionElements><forcitis:documentType/></extensionElements>",
                "<extensionElements><other:documentType code=\"TIMESHEET\"/></extensionElements>",
                "<extensionElements><forcitis:documentType code=\"timesheet\"/></extensionElements>"}) {
            assertThatThrownBy(() -> ProcessDocumentType.read(ProcessKey.COST_TIMESHEET_APPROVAL, process(extension)))
                    .as(extension).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID))
                    .hasMessageContaining("cost_timesheet_approval");
        }
    }
}
