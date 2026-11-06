package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.Process;

/**
 * What the platform needs to know about a process besides its nodes, all declared by the process itself so that a new process is only a
 * BPMN file: the name a person reads (the process name attribute) and forcitis:routingFlagRequired value="true|false". A definition
 * without either is a deployment fault, never a default (ADR-0066 decision 4). A process may also declare data-only form fields
 * (forcitis:formField name="..."): fields no node reads for an approver but that the owning module needs back with the result.
 */
final class ProcessDefinitionDeclarations {

    private ProcessDefinitionDeclarations() {
    }

    static ProcessName nameOf(ProcessKey key, Process process) {
        String name = process.getName();
        if (name == null || name.isBlank()) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key() + " must carry the name a person reads in its name attribute");
        }
        return new ProcessName(name);
    }

    static boolean routingFlagRequired(ProcessKey key, Process process) {
        List<ExtensionElement> declared = process.getExtensionElements().getOrDefault("routingFlagRequired", List.of()).stream()
                .filter(e -> NodePolicyReader.NAMESPACE.equals(e.getNamespace())).toList();
        String value = declared.size() == 1 ? declared.getFirst().getAttributeValue(null, "value") : null;
        if (!"true".equals(value) && !"false".equals(value)) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key()
                    + " must declare exactly one forcitis:routingFlagRequired value=\"true\" or \"false\" in its extensionElements");
        }
        return Boolean.parseBoolean(value);
    }

    /** The data-only form fields the process declares; a blank or repeated name is a deployment fault. */
    static Set<String> dataFormFields(ProcessKey key, Process process) {
        Set<String> names = new LinkedHashSet<>();
        for (ExtensionElement element : process.getExtensionElements().getOrDefault("formField", List.of())) {
            if (!NodePolicyReader.NAMESPACE.equals(element.getNamespace())) {
                continue;
            }
            String name = element.getAttributeValue(null, "name");
            if (name == null || name.isBlank() || !names.add(name)) {
                throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key()
                        + " declares a forcitis:formField with a blank or repeated name");
            }
        }
        return names;
    }
}
