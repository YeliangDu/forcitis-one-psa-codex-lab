package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.api.ProcessKey;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.flowable.dmn.api.DmnDecision;
import org.flowable.dmn.api.DmnDecisionService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.model.DecisionTable;
import org.flowable.dmn.model.DmnDefinition;
import org.flowable.dmn.model.InputClause;
import org.springframework.stereotype.Component;

/**
 * The decision table of a process type, "{process key}_nodes", answers per node: is it routed, may it be skipped when nobody is found,
 * may a repeat approver pass automatically, and which deadline and escalation interval (in workdays) apply. It is evaluated once at
 * the start, with the version deployed then, and the answers travel as process variables. A missing table or a node it does not
 * cover fails the start; nothing is assumed.
 */
@Component
class NodeDecisions {

    static final String ROUTED = "routed_";
    static final String SKIP_WHEN_EMPTY = "skipWhenEmpty_";
    static final String AUTO_APPROVE_REPEAT = "autoApproveRepeat_";
    static final String DEADLINE_WORKDAYS = "deadlineWorkdays_";
    static final String ESCALATION_WORKDAYS = "escalationWorkdays_";

    private static final String NODE_INPUT = "node";

    record Decision(boolean routed, boolean skipWhenEmpty, boolean autoApproveRepeat, OptionalInt deadlineWorkdays,
                    OptionalInt escalationWorkdays) {

        Map<String, Object> asVariables(String nodeKey) {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put(ROUTED + nodeKey, routed);
            variables.put(SKIP_WHEN_EMPTY + nodeKey, skipWhenEmpty);
            variables.put(AUTO_APPROVE_REPEAT + nodeKey, autoApproveRepeat);
            deadlineWorkdays.ifPresent(n -> variables.put(DEADLINE_WORKDAYS + nodeKey, n));
            escalationWorkdays.ifPresent(n -> variables.put(ESCALATION_WORKDAYS + nodeKey, n));
            return variables;
        }
    }

    private final DmnDecisionService decisions;
    private final DmnRepositoryService repository;
    private final Map<String, Set<String>> inputsByDecisionId = new ConcurrentHashMap<>();

    NodeDecisions(DmnDecisionService decisions, DmnRepositoryService repository) {
        this.decisions = decisions;
        this.repository = repository;
    }

    static String decisionKey(ProcessKey processKey) {
        return processKey.key() + "_nodes";
    }

    boolean isDeployed(ProcessKey processKey) {
        return repository.createDecisionQuery().decisionKey(decisionKey(processKey)).latestVersion().singleResult() != null;
    }

    /** facts: the routing flags the owning module computed (true = set); every other input of the table counts as false. */
    Decision decide(ProcessKey processKey, String nodeKey, Set<String> facts) {
        DmnDecision definition = repository.createDecisionQuery().decisionKey(decisionKey(processKey)).latestVersion().singleResult();
        if (definition == null) {
            throw new IllegalStateException("No decision table " + decisionKey(processKey) + " is deployed");
        }
        Map<String, Object> inputs = new HashMap<>();
        for (String input : inputsOf(definition)) {
            inputs.put(input, facts.contains(input));
        }
        inputs.put(NODE_INPUT, nodeKey);
        Map<String, Object> result = decisions.createExecuteDecisionBuilder().decisionKey(decisionKey(processKey)).variables(inputs)
                .executeWithSingleResult();
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("Decision table " + decisionKey(processKey) + " has no rule for node " + nodeKey
                    + " with the facts " + facts);
        }
        return new Decision(flag(result, "routed"), flag(result, "skipWhenEmpty"), flag(result, "autoApproveRepeat"),
                workdays(result, "deadlineWorkdays"), workdays(result, "escalationWorkdays"));
    }

    private Set<String> inputsOf(DmnDecision definition) {
        return inputsByDecisionId.computeIfAbsent(definition.getId(), id -> {
            DmnDefinition model = repository.getDmnDefinition(id);
            DecisionTable table = (DecisionTable) model.getDecisions().stream().filter(d -> definition.getKey().equals(d.getId())).findFirst().orElseThrow().getExpression();
            Set<String> names = new java.util.LinkedHashSet<>();
            for (InputClause input : table.getInputs()) {
                String name = input.getInputExpression().getText().trim();
                if (!NODE_INPUT.equals(name)) {
                    names.add(name);
                }
            }
            return names;
        });
    }

    private static boolean flag(Map<String, Object> result, String name) {
        Object value = result.get(name);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalStateException("Decision output " + name + " must be true or false, was " + value);
        }
        return flag;
    }

    private static OptionalInt workdays(Map<String, Object> result, String name) {
        Object value = result.get(name);
        if (value == null) {
            return OptionalInt.empty();
        }
        if (!(value instanceof Number number) || number.intValue() < 1 || number.doubleValue() != number.intValue()) {
            throw new IllegalStateException("Decision output " + name + " must be a whole number of workdays, at least 1, was " + value);
        }
        return OptionalInt.of(number.intValue());
    }
}
