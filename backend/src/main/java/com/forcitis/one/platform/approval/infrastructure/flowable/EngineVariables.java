package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.domain.ProcessVariables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps {@link ProcessVariables} to Flowable variables and back. Business fields live under the "field:" prefix, so a
 * business value can never overwrite an engine variable (initiatorId, businessKey). A multi-valued field is stored
 * as one newline-joined text; "true" / "false" engine variables become booleans so BPMN conditions can read them.
 */
final class EngineVariables {

    static final String FIELD_PREFIX = "field:";
    static final String SEPARATOR = "\n";

    private EngineVariables() {
    }

    static Map<String, Object> toEngine(ProcessVariables values) {
        Map<String, Object> out = new LinkedHashMap<>();
        values.variables().forEach((name, list) -> out.put(name, scalar(list)));
        values.formFields().forEach((name, list) -> {
            for (String value : list) {
                if (value != null && value.contains(SEPARATOR)) {
                    throw new IllegalArgumentException("Field " + name + " has a value with a line break");
                }
            }
            out.put(FIELD_PREFIX + name, String.join(SEPARATOR, list));
        });
        return out;
    }

    static ProcessVariables fromEngine(Map<String, Object> engine) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        Map<String, List<String>> variables = new LinkedHashMap<>();
        engine.forEach((name, value) -> {
            if (value == null) {
                return;
            }
            List<String> list = new ArrayList<>(Arrays.asList(String.valueOf(value).split(SEPARATOR, -1)));
            list.removeIf(String::isEmpty);
            if (name.startsWith(FIELD_PREFIX)) {
                fields.put(name.substring(FIELD_PREFIX.length()), list);
            } else {
                variables.put(name, list);
            }
        });
        return new ProcessVariables(fields, variables);
    }

    private static Object scalar(List<String> list) {
        if (list.size() == 1 && ("true".equals(list.getFirst()) || "false".equals(list.getFirst()))) {
            return Boolean.valueOf(list.getFirst());
        }
        return String.join(SEPARATOR, list);
    }
}
