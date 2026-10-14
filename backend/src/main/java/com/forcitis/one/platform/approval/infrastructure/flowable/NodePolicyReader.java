package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.domain.AssignmentRuleCodec;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import java.util.List;
import java.util.Map;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;

/**
 * Reads the assignment rule written in a user task's extension element
 * {@code <forcitis:assignmentRule type="ROLE" role="reviewer" selfApproval="allowed" beforeHook="KEY"/>}.
 * RULE-262: a node has exactly one rule; none or several is a hard error and no task is created.
 */
final class NodePolicyReader {

    static final String NAMESPACE = "https://forcitis.com/psa/approval/extension";
    static final String ELEMENT = "assignmentRule";

    private NodePolicyReader() {
    }

    static NodePolicy read(UserTask node) {
        List<ExtensionElement> rules = node.getExtensionElements().getOrDefault(ELEMENT, List.of()).stream()
                .filter(e -> NAMESPACE.equals(e.getNamespace())).toList();
        if (rules.isEmpty()) {
            throw new IllegalStateException("Node " + node.getId() + " has no forcitis:assignmentRule (RULE-262: exactly one)");
        }
        if (rules.size() > 1) {
            throw new IllegalStateException("Node " + node.getId() + " has " + rules.size()
                    + " forcitis:assignmentRule elements (RULE-262: exactly one)");
        }
        ExtensionElement element = rules.getFirst();
        try {
            return new NodePolicy(AssignmentRuleCodec.parse(attribute(element, "type"), name -> attribute(element, name)),
                    "allowed".equals(attribute(element, "selfApproval")),
                    attribute(element, "beforeHook"));
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new IllegalStateException("Node " + node.getId() + ": " + e.getMessage(), e);
        }
    }

    private static String attribute(ExtensionElement element, String name) {
        Map<String, List<ExtensionAttribute>> attributes = element.getAttributes();
        List<ExtensionAttribute> found = attributes.get(name);
        return found == null || found.isEmpty() ? null : found.getFirst().getValue();
    }
}
