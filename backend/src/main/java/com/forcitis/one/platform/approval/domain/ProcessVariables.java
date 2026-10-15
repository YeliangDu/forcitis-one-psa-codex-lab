package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.EmployeeId;
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

    /** Blank, non-numeric and zero values are ignored. */
    public static Set<EmployeeId> parseEmployees(List<String> values) {
        List<EmployeeId> found = new ArrayList<>();
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            try {
                long id = Long.parseLong(raw.trim());
                if (id != 0) {
                    found.add(new EmployeeId(id));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return new LinkedHashSet<>(found);
    }
}
