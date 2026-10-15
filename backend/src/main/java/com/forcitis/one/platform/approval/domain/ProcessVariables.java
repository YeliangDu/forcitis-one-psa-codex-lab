package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** formFields and variables are kept apart so a business field can never shadow an engine variable. */
public record ProcessVariables(Map<String, List<String>> formFields, Map<String, List<String>> variables) {

    public ProcessVariables {
        formFields = Map.copyOf(formFields);
        variables = Map.copyOf(variables);
    }

    public static ProcessVariables empty() {
        return new ProcessVariables(Map.of(), Map.of());
    }

    public List<String> formField(String name) {
        return formFields.getOrDefault(name, List.of());
    }

    public List<String> variable(String name) {
        return variables.getOrDefault(name, List.of());
    }

    public Optional<String> firstFormField(String name) {
        return formField(name).stream().findFirst();
    }

    public Optional<String> firstVariable(String name) {
        return variable(name).stream().findFirst();
    }

    /**
     * Blank values are ignored; any other value that is not an employee id in its canonical UUID form refuses the assignment as a whole
     * (ADR-0033 section 3 item 3): it is never skipped while the others go on.
     */
    public static Set<EmployeeId> parseEmployees(List<String> values) {
        List<EmployeeId> found = new ArrayList<>();
        for (String raw : values) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                found.add(EmployeeId.parse(raw.trim()));
            } catch (IllegalArgumentException notAnEmployeeId) {
                throw ApprovalError.PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID.refusal("a person is named by a value that is not an employee id");
            }
        }
        return new LinkedHashSet<>(found);
    }
}
