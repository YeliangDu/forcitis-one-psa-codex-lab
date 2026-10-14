package com.forcitis.one.platform.approval.infrastructure.replay;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The export file of the TIMESHEET approver replay (JSON, formatVersion 1; documented in
 * docs/modernization/phase1a/TIMESHEET_REPLAY.md). It carries the OLD organisation snapshot of the time the instances ran,
 * the assignment rule of every node and the approvers who actually acted. Exports contain real people: they live in
 * ~/.modernize/psa/ and never in the repository; tests use synthetic fixtures only.
 */
record ReplayExport(int formatVersion, String processKey, Long processAdministratorUserId, List<Long> legacyFallbackUserIds,
                    Snapshot snapshot, List<InstanceRecord> instances) {

    /** The one JSON reader / writer of the replay: absent flags mean false, so exports stay terse. */
    static JsonMapper mapper() {
        return JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    }

    ReplayExport {
        legacyFallbackUserIds = legacyFallbackUserIds == null ? List.of() : List.copyOf(legacyFallbackUserIds);
        instances = instances == null ? List.of() : List.copyOf(instances);
    }

    record Snapshot(List<EmployeeRecord> employees, List<OrgUnitRecord> orgUnits, Map<String, List<Long>> roleHolders) {

        Snapshot {
            employees = employees == null ? List.of() : List.copyOf(employees);
            orgUnits = orgUnits == null ? List.of() : List.copyOf(orgUnits);
            roleHolders = roleHolders == null ? Map.of() : Map.copyOf(roleHolders);
        }
    }

    /** employed = false for a disabled or separated user; supervisorUserId is the person-level supervisor (RULE-372). */
    record EmployeeRecord(long userId, long orgUnitId,
                          @JsonAlias(LegacyVocabulary.SUPERVISOR_KEY) Long supervisorUserId, boolean employed) {
    }

    record OrgUnitRecord(long orgUnitId, Long parentOrgUnitId, Long headUserId, Long regionalReceivablesManagerId, boolean active) {
    }

    record InstanceRecord(String instanceId, long initiatorId, Map<String, List<String>> formFields,
                          Map<String, List<String>> variables, List<NodeRecord> nodes) {

        InstanceRecord {
            formFields = formFields == null ? Map.of() : Map.copyOf(formFields);
            variables = variables == null ? Map.of() : Map.copyOf(variables);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    /** rule: the type plus its attributes (role, department, level, users, field, variable); selfApproval: "allowed" exempts. */
    record NodeRecord(String nodeKey, Map<String, String> rule, boolean selfApprovalAllowed, List<Long> actualApprovers) {

        NodeRecord {
            rule = rule == null ? Map.of() : Map.copyOf(rule);
            actualApprovers = actualApprovers == null ? List.of() : List.copyOf(actualApprovers);
        }
    }
}
