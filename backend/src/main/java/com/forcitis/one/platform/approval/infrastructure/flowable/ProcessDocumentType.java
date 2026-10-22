package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.List;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.Process;

/** The document type a process approves, declared once on the process as forcitis:documentType code="…" (ADR-0066 decision 4). */
final class ProcessDocumentType {

    private ProcessDocumentType() {
    }

    static DocumentTypeCode read(ProcessKey key, Process process) {
        List<ExtensionElement> declared = process.getExtensionElements().getOrDefault("documentType", List.of()).stream()
                .filter(e -> NodePolicyReader.NAMESPACE.equals(e.getNamespace())).toList();
        if (declared.size() != 1 || declared.getFirst().getAttributeValue(null, "code") == null) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key()
                    + " must declare exactly one forcitis:documentType code=\"…\" in its extensionElements");
        }
        try {
            return new DocumentTypeCode(declared.getFirst().getAttributeValue(null, "code"));
        } catch (IllegalArgumentException malformed) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key() + " declares a malformed forcitis:documentType code: "
                    + malformed.getMessage());
        }
    }
}
