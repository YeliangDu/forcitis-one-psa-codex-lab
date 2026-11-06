package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.domain.AssignmentRuleCodec;
import com.forcitis.one.platform.approval.domain.NodeDeadline;
import com.forcitis.one.platform.approval.domain.NodePolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;

/**
 * A node has exactly one rule element, which also carries the node's routing flag, skip and auto-approve permissions and deadline
 * (D-1a-100: no decision table). A node with none or several rules, an unknown attribute value, or a deadline without its escalation
 * interval (or the reverse) is a hard error and no task is created.
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
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("Node " + node.getId() + " has no forcitis:assignmentRule (RULE-262: exactly one)");
        }
        if (rules.size() > 1) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("Node " + node.getId() + " has " + rules.size()
                    + " forcitis:assignmentRule elements (RULE-262: exactly one)");
        }
        ExtensionElement element = rules.getFirst();
        try {
            NodePolicy policy = new NodePolicy(AssignmentRuleCodec.parse(attribute(element, "type"), name -> attribute(element, name)),
                    "allowed".equals(attribute(element, "selfApproval")),
                    flag(element, "skipWhenEmpty"),
                    Optional.ofNullable(attribute(element, "beforeHook")).filter(key -> !key.isBlank()),
                    flag(element, "cutoff"),
                    Optional.ofNullable(attribute(element, "routingFlag")).filter(name -> !name.isBlank()),
                    flag(element, "autoApproveRepeat"),
                    deadline(element),
                    flag(element, "noFallback"));
            if (policy.noFallback() && policy.deadline().isPresent()) {
                throw new IllegalStateException("noFallback and a deadline cannot be combined: an overdue task would be escalated to a supervisor or the administrators");
            }
            return policy;
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("Node " + node.getId() + ": " + e.getMessage());
        }
    }

    private static Optional<NodeDeadline> deadline(ExtensionElement element) {
        String deadline = attribute(element, "deadlineWorkdays");
        String escalation = attribute(element, "escalationWorkdays");
        if (deadline == null && escalation == null) {
            return Optional.empty();
        }
        if (deadline == null || escalation == null) {
            throw new IllegalStateException("deadlineWorkdays and escalationWorkdays come as a pair, found " + deadline + "/" + escalation);
        }
        return Optional.of(new NodeDeadline(workdays("deadlineWorkdays", deadline), workdays("escalationWorkdays", escalation)));
    }

    private static int workdays(String name, String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be a whole number of workdays, found '" + raw + "'");
        }
    }

    private static boolean flag(ExtensionElement element, String name) {
        String raw = attribute(element, name);
        if (raw == null) {
            return false;
        }
        if (!"true".equals(raw) && !"false".equals(raw)) {
            throw new IllegalStateException(name + " must be true or false, found '" + raw + "'");
        }
        return Boolean.parseBoolean(raw);
    }

    private static String attribute(ExtensionElement element, String name) {
        Map<String, List<ExtensionAttribute>> attributes = element.getAttributes();
        List<ExtensionAttribute> found = attributes.get(name);
        return found == null || found.isEmpty() ? null : found.getFirst().getValue();
    }
}
