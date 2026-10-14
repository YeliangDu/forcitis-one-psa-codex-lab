package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The vocabulary of assignment rules outside Java: the same type names are written in BPMN (forcitis:assignmentRule type=...)
 * and in TIMESHEET replay exports. An unknown type or a missing required attribute is a configuration error and fails
 * loudly (RULE-300): a misconfigured node is never silently skipped.
 */
public final class AssignmentRuleCodec {

    private AssignmentRuleCodec() {
    }

    /**
     * @param type  ROLE, DEPARTMENT_HEAD, INITIATOR_SUPERVISOR, NAMED_USERS, REGIONAL_RECEIVABLES_MANAGER, DEPARTMENT_DIRECTOR,
     *              FORM_FIELD_USERS, FORM_FIELD_USER_SUPERVISORS, PROCESS_VARIABLE_USERS or INITIATOR
     * @param attribute reads an attribute of the rule element (role, department, level, users, field, variable); null when absent
     */
    public static AssignmentRule parse(String type, Function<String, String> attribute) {
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("Assignment rule without a type");
        }
        return switch (type) {
            case "ROLE" -> new AssignmentRule.Role(new RoleCode(required(type, attribute, "role")));
            case "DEPARTMENT_HEAD" -> new AssignmentRule.DepartmentHead(attribute.apply("department"));
            case "INITIATOR_SUPERVISOR" -> new AssignmentRule.InitiatorSupervisor(Integer.parseInt(required(type, attribute, "level")));
            case "NAMED_USERS" -> new AssignmentRule.NamedUsers(users(required(type, attribute, "users")));
            case "REGIONAL_RECEIVABLES_MANAGER" -> new AssignmentRule.RegionalReceivablesOwner(attribute.apply("department"));
            case "DEPARTMENT_DIRECTOR" -> new AssignmentRule.DepartmentDirector(attribute.apply("department"));
            case "FORM_FIELD_USERS" -> new AssignmentRule.FormFieldUsers(required(type, attribute, "field"));
            case "FORM_FIELD_USER_SUPERVISORS" -> new AssignmentRule.FormFieldUserSupervisors(required(type, attribute, "field"));
            case "PROCESS_VARIABLE_USERS" -> new AssignmentRule.ProcessVariableUsers(required(type, attribute, "variable"));
            case "INITIATOR" -> new AssignmentRule.Initiator();
            default -> throw new IllegalStateException("Unknown assignment rule type " + type);
        };
    }

    private static String required(String type, Function<String, String> attribute, String name) {
        String value = attribute.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Assignment rule " + type + " needs the attribute " + name);
        }
        return value.trim();
    }

    private static Set<UserId> users(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> new UserId(Long.parseLong(s))).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
