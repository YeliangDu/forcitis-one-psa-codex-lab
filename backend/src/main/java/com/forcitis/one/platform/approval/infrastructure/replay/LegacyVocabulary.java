package com.forcitis.one.platform.approval.infrastructure.replay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The one place where old names are written down: old name to new name; unlisted names pass through. */
final class LegacyVocabulary {

    /** Old business form field and process variable names -> the names in the glossary. */
    static final Map<String, String> FIELD_NAMES = table(
            "manager_id", "projectManagerId",
            "area_leader", "regionalHeadId",
            "deliveryDeptId", "deliveryUnitId");

    /** Old rule type names -> the rule type names of the codec. */
    static final Map<String, String> RULE_TYPES = table(
            "INITIATOR_LEADER", "INITIATOR_SUPERVISOR");

    /** The old employee key of the person-level supervisor in the exported snapshot. */
    static final String SUPERVISOR_KEY = "leaderUserId";

    /** The old rework node (script 10: the initiator redoes the form after a return) has no counterpart: a return ends the round and the initiator starts a new one, so the node is left out of the replay. */
    static final String REWORK_RULE_TYPE = "INITIATOR";

    private LegacyVocabulary() {
    }

    static String field(String name) {
        return FIELD_NAMES.getOrDefault(name, name);
    }

    static String ruleType(String type) {
        return RULE_TYPES.getOrDefault(type, type);
    }

    static boolean isReworkNode(ReplayExport.NodeRecord node) {
        return REWORK_RULE_TYPE.equals(node.rule().get("type"));
    }

    static Map<String, List<String>> fields(Map<String, List<String>> values) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        values.forEach((name, list) -> out.put(field(name), list));
        return out;
    }

    /** A rule element with the new type name and, where the attribute names a field or variable, the new field name. */
    static Map<String, String> rule(Map<String, String> rule) {
        Map<String, String> out = new LinkedHashMap<>(rule);
        out.computeIfPresent("type", (k, v) -> ruleType(v));
        for (String attribute : List.of("field", "department", "variable")) {
            out.computeIfPresent(attribute, (k, v) -> field(v));
        }
        return out;
    }

    private static Map<String, String> table(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return Map.copyOf(out);
    }
}
