package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The values an assignment rule may read. formFields come from the owning module (business data, RULE-258..260);
 * variables are set by the engine or by hooks (initiator, flags). Kept apart so a business field can never shadow
 * an engine variable. Values are lists of text; scalars are one-element lists.
 */
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

    /** Distinct user ids found in the values; blank, non-numeric and zero values are ignored (never an NPE, RULE-253). */
    public static Set<UserId> parseUsers(List<String> values) {
        List<UserId> found = new ArrayList<>();
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            try {
                long id = Long.parseLong(raw.trim());
                if (id != 0) {
                    found.add(new UserId(id));
                }
            } catch (NumberFormatException ignored) {
                // a value that is not an id is not an approver
            }
        }
        return new LinkedHashSet<>(found);
    }
}
