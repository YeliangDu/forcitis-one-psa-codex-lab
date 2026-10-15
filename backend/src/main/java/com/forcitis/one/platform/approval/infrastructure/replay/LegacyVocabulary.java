package com.forcitis.one.platform.approval.infrastructure.replay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LegacyVocabulary {

    static final Map<String, String> FIELD_NAMES = table(
            "manager_id", "projectManagerId",
            "area_leader", "orgUnitHeadId",
            "regionalHeadId", "orgUnitHeadId",
            "deliveryDeptId", "deliveryUnitId");

    /** The new names of the fields that name a person, and of those that name a department, by the old system's number. */
    static final java.util.Set<String> PERSON_FIELDS = java.util.Set.of("projectManagerId", "orgUnitHeadId");
    static final java.util.Set<String> DEPARTMENT_FIELDS = java.util.Set.of("deliveryUnitId");

    static final Map<String, String> RULE_TYPES = table(
            "INITIATOR_LEADER", "INITIATOR_SUPERVISOR");

    /** Old hour line types (0 project, 1 pre-sales, 2 overhead) -> the routing flags of the owning module. */
    static final Map<String, String> HOUR_TYPE_FLAGS = table(
            "0", "projectHours",
            "1", "preSalesHours",
            "2", "overheadHours");

    static final Map<String, String> NODE_KEYS = table(
            "项目经理审批", "projectManagerReview",
            "区域负责人审批", "orgUnitHeadApproval",
            "regionalHeadReview", "orgUnitHeadApproval",
            "一级汇报人审批", "supervisorReview");

    static final String SUPERVISOR_KEY = "leaderUserId";

    /** The old rework node has no counterpart: a return ends the round and the initiator starts a new one, so the node is left out of the replay. */
    static final String REWORK_RULE_TYPE = "INITIATOR";

    private LegacyVocabulary() {
    }

    static String field(String name) {
        return FIELD_NAMES.getOrDefault(name, name);
    }

    static String nodeKey(String name) {
        return NODE_KEYS.getOrDefault(name, name);
    }

    static String flag(int hourType) {
        String flag = HOUR_TYPE_FLAGS.get(String.valueOf(hourType));
        if (flag == null) {
            throw new IllegalArgumentException("Unknown old hour type " + hourType);
        }
        return flag;
    }

    static String ruleType(String type) {
        return RULE_TYPES.getOrDefault(type, type);
    }

    static boolean isReworkNode(ReplayExport.NodeRecord node) {
        return REWORK_RULE_TYPE.equals(node.rule().get("type"));
    }

    static Map<String, List<String>> fields(Map<String, List<String>> values) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        values.forEach((name, list) -> {
            String field = field(name);
            out.put(field, PERSON_FIELDS.contains(field) ? list.stream().map(ExportedIds::employeeText).toList()
                    : DEPARTMENT_FIELDS.contains(field) ? list.stream().map(ExportedIds::orgUnitText).toList() : list);
        });
        return out;
    }

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
