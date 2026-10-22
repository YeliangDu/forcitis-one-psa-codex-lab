package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.platform.identity.api.RoleCode;
import java.util.Set;
import java.util.function.Function;

/** Same type names are written in BPMN and in the replay exports of the timesheet process. */
public final class AssignmentRuleCodec {

    /** Types that exist in the rule cards but are not built yet; a process that uses one does not deploy. */
    public static final Set<String> DEFERRED_TYPES = Set.of("REGIONAL_RECEIVABLES_MANAGER", "DEPARTMENT_DIRECTOR",
            "FORM_FIELD_USER_SUPERVISORS");
    /** Types that are not built at all; a role or a form field takes their place. */
    public static final Set<String> NOT_BUILT_TYPES = Set.of("NAMED_USERS", "PROCESS_VARIABLE_USERS");

    private AssignmentRuleCodec() {
    }

    public static AssignmentRule parse(String type, Function<String, String> attribute) {
        if (type == null || type.isBlank()) {
            throw new IllegalStateException("Assignment rule without a type");
        }
        if (DEFERRED_TYPES.contains(type)) {
            throw new IllegalStateException("Assignment rule type " + type + " is deferred to a later phase and cannot be used yet");
        }
        if (NOT_BUILT_TYPES.contains(type)) {
            throw new IllegalStateException("Assignment rule type " + type + " is not offered; use a ROLE or a FORM_FIELD_USERS node");
        }
        return switch (type) {
            case "ROLE" -> new AssignmentRule.Role(new RoleCode(required(type, attribute, "role")));
            case "DEPARTMENT_HEAD" -> new AssignmentRule.DepartmentHead(attribute.apply("department"));
            case "INITIATOR_SUPERVISOR" -> new AssignmentRule.InitiatorSupervisor(level(type, attribute));
            case "FORM_FIELD_USERS" -> new AssignmentRule.FormFieldUsers(required(type, attribute, "field"));
            default -> throw new IllegalStateException("Unknown assignment rule type " + type);
        };
    }

    private static int level(String type, Function<String, String> attribute) {
        String raw = required(type, attribute, "level");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Assignment rule " + type + ": level must be 1 or 2, was " + raw);
        }
    }

    private static String required(String type, Function<String, String> attribute, String name) {
        String value = attribute.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Assignment rule " + type + " needs the attribute " + name);
        }
        return value.trim();
    }
}
