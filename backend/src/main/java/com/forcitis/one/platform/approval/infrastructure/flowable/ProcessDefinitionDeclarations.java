package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.ProcessName;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.EventSubProcess;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;

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

    /**
     * A countersign node (a user task with loop characteristics) must not be re-enterable: no path of sequence flows leads from it, through any
     * later node or sub-process, back to it. The one-person-one-share rule (ApprovalRequest.hasDecidedOnNodeOf) counts the decisions of an instance's node and
     * holds only because a node is entered once; a second visit would be a new round it cannot tell apart. A definition with such a loop is a deployment fault.
     * Constructs that re-enter a node without a sequence-flow cycle are refused outright in a definition that has a countersign node: boundary events, event
     * sub-processes, call activities and sub-processes with loop characteristics. Leaving a sub-process by an end event or by a node without outgoing flow is
     * followed alike, and an end event that does not end the sub-process normally (error, terminate) is followed too, which is the safe side.
     * Follow-up if a looping countersign is ever needed: a visit number stored on the task.
     */
    static void requireCountersignEnteredOnce(ProcessKey key, Process process) {
        List<UserTask> countersigns = process.findFlowElementsOfType(UserTask.class).stream().filter(task -> task.getLoopCharacteristics() != null).toList();
        if (countersigns.isEmpty()) {
            return;
        }
        String first = countersigns.getFirst().getId();
        for (BoundaryEvent boundary : process.findFlowElementsOfType(BoundaryEvent.class)) {
            refuseConstruct(key, first, "a boundary event (" + boundary.getId() + ")");
        }
        for (EventSubProcess sub : process.findFlowElementsOfType(EventSubProcess.class)) {
            refuseConstruct(key, first, "an event sub-process (" + sub.getId() + ")");
        }
        for (CallActivity call : process.findFlowElementsOfType(CallActivity.class)) {
            refuseConstruct(key, first, "a call activity (" + call.getId() + ")");
        }
        for (SubProcess sub : process.findFlowElementsOfType(SubProcess.class)) {
            if (sub.getLoopCharacteristics() != null) {
                refuseConstruct(key, first, "a sub-process with loop characteristics (" + sub.getId() + ")");
            }
        }
        Map<String, List<String>> next = new HashMap<>();
        for (SequenceFlow flow : process.findFlowElementsOfType(SequenceFlow.class)) {
            next.computeIfAbsent(flow.getSourceRef(), k -> new ArrayList<>()).add(flow.getTargetRef());
        }
        for (SubProcess sub : process.findFlowElementsOfType(SubProcess.class)) {
            List<String> after = sub.getOutgoingFlows().stream().map(SequenceFlow::getTargetRef).toList();
            for (FlowElement inner : sub.getFlowElements()) {
                if (inner instanceof StartEvent) {
                    next.computeIfAbsent(sub.getId(), k -> new ArrayList<>()).add(inner.getId());
                }
                if (inner instanceof FlowNode node && !(inner instanceof StartEvent) && node.getOutgoingFlows().isEmpty()) {
                    next.computeIfAbsent(inner.getId(), k -> new ArrayList<>()).addAll(after);
                }
            }
        }
        for (UserTask task : countersigns) {
            if (reaches(next, task.getId(), task.getId())) {
                throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key() + " can re-enter its countersign node " + task.getId()
                        + ": a countersign node must be entered once");
            }
        }
    }

    private static void refuseConstruct(ProcessKey key, String countersign, String construct) {
        throw ApprovalError.PLATFORM_APPROVAL_NODE_CONFIG_INVALID.refusal("The process " + key.key() + " has a countersign node (" + countersign + ") and "
                + construct + ": a countersign node must be entered once, and this construct can re-enter it");
    }

    private static boolean reaches(Map<String, List<String>> next, String from, String target) {
        Set<String> seen = new HashSet<>();
        Deque<String> open = new ArrayDeque<>(next.getOrDefault(from, List.of()));
        while (!open.isEmpty()) {
            String id = open.pop();
            if (id.equals(target)) {
                return true;
            }
            if (seen.add(id)) {
                open.addAll(next.getOrDefault(id, List.of()));
            }
        }
        return false;
    }
}
