package com.forcitis.one.platform.approval.infrastructure.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.platform.approval.domain.AssignmentRuleCodec;
import com.forcitis.one.platform.approval.domain.ResolutionKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class TimesheetApproverReplayIT {

    static final JsonMapper MAPPER = ReplayExport.mapper();

    static ReplayExport fixture() throws IOException {
        try (InputStream in = TimesheetApproverReplayIT.class.getResourceAsStream("/replay/synthetic-timesheet-export.json")) {
            return MAPPER.readValue(in, ReplayExport.class);
        }
    }

    @Test
    @DisplayName("C1a-13 every node is classified: matches, reviewed corrections (by category) and the one real defect")
    void classifiesEveryNode() throws IOException {
        ReplayReport report = TimesheetApproverReplay.replay(fixture());

        assertThat(report.instances()).isEqualTo(10);
        assertThat(report.nodes()).isEqualTo(13);
        assertThat(report.count(ReplayVerdict.MATCH)).isEqualTo(7);
        assertThat(report.count(ReplayVerdict.REVIEWED_REPORTING_LINE)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_DISABLED_APPROVER)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_HARDCODED_FALLBACK)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_SELF_APPROVAL)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_ADMINISTRATOR_FALLBACK)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.DEFECT)).isEqualTo(1);
        assertThat(report.hasDefects()).isTrue();
        assertThat(report.differences()).hasSize(5 + 1);
        assertThat(report.differences()).filteredOn(d -> d.verdict() == ReplayVerdict.DEFECT).singleElement().satisfies(d -> {
            assertThat(d.instanceId()).isEqualTo("SYN-8");
            assertThat(d.nodeKey()).isEqualTo("regionalHeadReview");
            assertThat(d.ruleType()).isEqualTo("FORM_FIELD_USERS");
            assertThat(d.candidates()).containsExactly(110L);
            assertThat(d.actualApprovers()).containsExactly(105L);
        });
        assertThat(report.resolutionCounts().get(ResolutionKind.RESOLVED)).isEqualTo(7);
    }

    @Test
    @DisplayName("RULE-249 'old approver in the new candidate set': every actual approver of a node is a member, in any order")
    void matchingIsMembership() throws IOException {
        ReplayReport report = TimesheetApproverReplay.replay(fixture());
        assertThat(report.differences()).noneMatch(d -> d.instanceId().equals("SYN-3"));
        assertThat(report.differences()).noneMatch(d -> d.instanceId().equals("SYN-2"));
    }

    @Test
    @DisplayName("the replay is deterministic and its report names ids only (the markdown holds no name)")
    void deterministicAndIdsOnly() throws IOException {
        ReplayReport first = TimesheetApproverReplay.replay(fixture());
        ReplayReport second = TimesheetApproverReplay.replay(fixture());
        assertThat(second).isEqualTo(first);
        assertThat(first.toMarkdown()).contains("| DEFECT | 1 |").contains("SYN-8").doesNotContain("person-").doesNotContain("exported");
    }

    @Test
    @DisplayName("a fixture without the defect passes the gate")
    void cleanExportPasses() throws IOException {
        ReplayExport all = fixture();
        ReplayExport clean = new ReplayExport(all.formatVersion(), all.processKey(), all.processAdministratorUserId(),
                all.legacyFallbackUserIds(), all.snapshot(), all.instances().stream().filter(i -> !i.instanceId().equals("SYN-8")).toList());
        assertThat(TimesheetApproverReplay.replay(clean).hasDefects()).isFalse();
    }

    private static ReplayReport replayOnly(String instanceId) throws IOException {
        ReplayExport all = fixture();
        return TimesheetApproverReplay.replay(new ReplayExport(all.formatVersion(), all.processKey(), all.processAdministratorUserId(),
                all.legacyFallbackUserIds(), all.snapshot(), all.instances().stream().filter(i -> i.instanceId().equals(instanceId)).toList()));
    }

    @Test
    @DisplayName("TIMESHEET v15 the branches run in parallel: only project hours, project plus pre-sales, and all three hour types each match by node set and candidates")
    void threeShapesMatch() throws IOException {
        assertThat(replayOnly("SYN-1").nodes()).isEqualTo(1);
        assertThat(replayOnly("SYN-2").nodes()).isEqualTo(2);
        assertThat(replayOnly("SYN-3").nodes()).isEqualTo(3);
        for (String id : List.of("SYN-1", "SYN-2", "SYN-3")) {
            assertThat(replayOnly(id).count(ReplayVerdict.MATCH)).as(id).isEqualTo(replayOnly(id).nodes());
            assertThat(replayOnly(id).hasDefects()).as(id).isFalse();
        }
    }

    @Test
    @DisplayName("TIMESHEET v15 skip_when_empty: a node nobody acted on matches when the new rule finds nobody either")
    void skippedNodeMatches() throws IOException {
        ReplayReport report = replayOnly("SYN-9");
        assertThat(report.count(ReplayVerdict.MATCH)).isEqualTo(1);
        assertThat(report.resolutionCounts().get(ResolutionKind.SKIPPED)).isEqualTo(1);
    }

    @Test
    @DisplayName("TIMESHEET v15 node set: a node the new process would not run, and a node the old system skipped but the new process assigns, are defects")
    void nodeSetDifferencesAreDefects() throws IOException {
        ReplayExport all = fixture();
        ReplayExport.InstanceRecord base = all.instances().stream().filter(i -> i.instanceId().equals("SYN-1")).findFirst().orElseThrow();
        ReplayExport.NodeRecord extra = new ReplayExport.NodeRecord("regionalHeadReview",
                java.util.Map.of("type", "FORM_FIELD_USERS", "field", "regionalHeadId"), false, List.of(112L));
        ReplayExport.InstanceRecord withExtra = new ReplayExport.InstanceRecord("X-1", base.initiatorId(), base.legacyHourTypes(),
                base.formFields(), base.variables(), List.of(base.nodes().getFirst(), extra));
        ReplayExport.InstanceRecord oldSkipped = new ReplayExport.InstanceRecord("X-2", base.initiatorId(), base.legacyHourTypes(),
                base.formFields(), base.variables(), List.of());
        ReplayReport report = TimesheetApproverReplay.replay(new ReplayExport(1, "TIMESHEET", 900L, List.of(), all.snapshot(),
                List.of(withExtra, oldSkipped)));
        assertThat(report.differences()).extracting(ReplayReport.Difference::instanceId, ReplayReport.Difference::verdict)
                .containsExactlyInAnyOrder(org.assertj.core.api.Assertions.tuple("X-1", ReplayVerdict.DEFECT),
                        org.assertj.core.api.Assertions.tuple("X-2", ReplayVerdict.DEFECT));
    }

    @Test
    @DisplayName("TIMESHEET v15 the old hour types map to the routing flags and the old task names to the node keys, in the one vocabulary table")
    void vocabularyTable() {
        assertThat(LegacyVocabulary.flag(0)).isEqualTo("projectHours");
        assertThat(LegacyVocabulary.flag(1)).isEqualTo("preSalesHours");
        assertThat(LegacyVocabulary.flag(2)).isEqualTo("overheadHours");
        assertThat(LegacyVocabulary.HOUR_TYPE_FLAGS.values()).containsExactlyInAnyOrderElementsOf(
                TimesheetBranches.ALL.stream().map(TimesheetBranches.Branch::flag).toList());
        assertThat(LegacyVocabulary.NODE_KEYS.values()).containsExactlyInAnyOrderElementsOf(
                TimesheetBranches.ALL.stream().map(TimesheetBranches.Branch::nodeKey).toList());
    }

    @Test
    @DisplayName("TIMESHEET v15 the replay's branch table equals the shipped BPMN and decision table: flags, nodes, rules and routing-flag constants")
    void branchTableEqualsTheProcess() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml"));
        String nodesTable = Files.readString(Path.of("src/main/resources/dmn/cost_timesheet_approval_nodes.dmn"));
        for (TimesheetBranches.Branch branch : TimesheetBranches.ALL) {
            assertThat(xml).contains("getOrDefault('routed_" + branch.nodeKey() + "', false)").contains("<userTask id=\"" + branch.nodeKey() + "\"");
            assertThat(nodesTable).contains("<text>" + branch.flag() + "</text>").contains("== \"" + branch.nodeKey() + "\"");
            String attributes = branch.rule().entrySet().stream().filter(e -> !e.getKey().equals("type"))
                    .map(e -> e.getKey() + "=\"" + e.getValue() + "\"").sorted().collect(java.util.stream.Collectors.joining(" "));
            assertThat(xml).contains("type=\"" + branch.rule().get("type") + "\" " + attributes);
        }
        assertThat(java.util.Arrays.stream(com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag.values())
                .map(com.forcitis.one.cost.timesheet.api.TimesheetRoutingFlag::flagName).toList())
                .containsExactlyInAnyOrderElementsOf(TimesheetBranches.ALL.stream().map(TimesheetBranches.Branch::flag).toList());
    }

    @Test
    @DisplayName("command line: reads the export, writes replay-report.json / .md, exits 1 on defects (java <class> <export> <dir>)")
    void commandLineEntry(@TempDir Path directory) throws Exception {
        Path export = directory.resolve("export.json");
        try (InputStream in = getClass().getResourceAsStream("/replay/synthetic-timesheet-export.json")) {
            Files.write(export, in.readAllBytes());
        }
        Path withDefect = directory.resolve("with-defect");
        int exit = launch(export, withDefect);
        assertThat(exit).isEqualTo(1);
        assertThat(withDefect.resolve("replay-report.md")).exists();
        assertThat(MAPPER.readTree(Files.readString(withDefect.resolve("replay-report.json"))).path("verdictCounts").path("DEFECT").asInt())
                .isEqualTo(1);

        ReplayExport all = fixture();
        ReplayExport clean = new ReplayExport(all.formatVersion(), all.processKey(), all.processAdministratorUserId(),
                all.legacyFallbackUserIds(), all.snapshot(), all.instances().stream().filter(i -> !i.instanceId().equals("SYN-8")).toList());
        Path cleanExport = directory.resolve("clean.json");
        Files.writeString(cleanExport, MAPPER.writeValueAsString(clean));
        Path withoutDefect = directory.resolve("clean");
        assertThat(launch(cleanExport, withoutDefect)).isZero();
        assertThat(withoutDefect.resolve("replay-report.json")).exists();
    }

    private static int launch(Path export, Path output) throws Exception {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", classPath,
                TimesheetApproverReplayCommand.class.getName(), export.toString(), output.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return process.exitValue();
    }

    @Test
    @DisplayName("the exporter vocabulary is exactly the rule codec's: the four types of the 1a catalogue are accepted, deferred and not-offered types are refused")
    void ruleVocabulary() {
        java.util.function.Function<String, String> attributes = name -> switch (name) {
            case "role" -> "R";
            case "level" -> "1";
            case "field" -> "f";
            default -> null;
        };
        for (String type : List.of("ROLE", "DEPARTMENT_HEAD", "INITIATOR_SUPERVISOR", "FORM_FIELD_USERS")) {
            assertThat(AssignmentRuleCodec.parse(type, attributes)).as(type).isNotNull();
        }
        for (String type : java.util.stream.Stream.concat(AssignmentRuleCodec.DEFERRED_TYPES.stream(), AssignmentRuleCodec.NOT_BUILT_TYPES.stream()).toList()) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> AssignmentRuleCodec.parse(type, attributes)).as(type)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private static <T> java.util.Map<T, T> inverse(java.util.Map<T, T> table) {
        java.util.Map<T, T> inverse = new java.util.LinkedHashMap<>();
        table.forEach((oldName, newName) -> inverse.put(newName, oldName));
        return inverse;
    }

    private static java.util.Map<String, java.util.List<String>> rename(java.util.Map<String, java.util.List<String>> values,
            java.util.Map<String, String> names) {
        java.util.Map<String, java.util.List<String>> out = new java.util.LinkedHashMap<>();
        values.forEach((name, list) -> out.put(names.getOrDefault(name, name), list));
        return out;
    }

    @Test
    @DisplayName("H2 an export that still spells the old names (field, variable, rule type, rework node) replays exactly like the same export in the new names")
    void oldNamesAreTranslatedByTheTable() throws IOException {
        ReplayExport current = fixture();
        java.util.Map<String, String> oldFields = inverse(LegacyVocabulary.FIELD_NAMES);
        java.util.Map<String, String> oldTypes = inverse(LegacyVocabulary.RULE_TYPES);
        List<ReplayExport.InstanceRecord> spelledOld = current.instances().stream().map(i -> {
            List<ReplayExport.NodeRecord> nodes = new java.util.ArrayList<>();
            nodes.add(new ReplayExport.NodeRecord("rework", java.util.Map.of("type", LegacyVocabulary.REWORK_RULE_TYPE), false, List.of(i.initiatorId())));
            i.nodes().forEach(n -> {
                java.util.Map<String, String> rule = new java.util.LinkedHashMap<>(n.rule());
                rule.computeIfPresent("type", (k, v) -> oldTypes.getOrDefault(v, v));
                for (String attribute : List.of("field", "department", "variable")) {
                    rule.computeIfPresent(attribute, (k, v) -> oldFields.getOrDefault(v, v));
                }
                nodes.add(new ReplayExport.NodeRecord(inverse(LegacyVocabulary.NODE_KEYS).getOrDefault(n.nodeKey(), n.nodeKey()), rule, n.selfApprovalAllowed(), n.actualApprovers()));
            });
            return new ReplayExport.InstanceRecord(i.instanceId(), i.initiatorId(), i.legacyHourTypes(), rename(i.formFields(), oldFields),
                    rename(i.variables(), oldFields), nodes);
        }).toList();
        ReplayExport old = new ReplayExport(current.formatVersion(), current.processKey(), current.processAdministratorUserId(),
                current.legacyFallbackUserIds(), current.snapshot(), spelledOld);

        assertThat(oldFields).isNotEmpty();
        assertThat(TimesheetApproverReplay.replay(old)).isEqualTo(TimesheetApproverReplay.replay(current));
        assertThat(old.instances().getFirst().formFields().keySet()).doesNotContain(
                com.forcitis.one.cost.timesheet.api.TimesheetApprovalField.PROJECT_MANAGER.fieldName());
    }

    @Test
    @DisplayName("H2 the old employee key of the supervisor is read as supervisorUserId; the new key reads the same")
    void supervisorKeyAlias() throws Exception {
        String old = "{\"userId\":1,\"orgUnitId\":2,\"" + LegacyVocabulary.SUPERVISOR_KEY + "\":3,\"employed\":true}";
        String current = "{\"userId\":1,\"orgUnitId\":2,\"supervisorUserId\":3,\"employed\":true}";

        assertThat(MAPPER.readValue(old, ReplayExport.EmployeeRecord.class).supervisorUserId()).isEqualTo(3L);
        assertThat(MAPPER.readValue(current, ReplayExport.EmployeeRecord.class).supervisorUserId()).isEqualTo(3L);
    }
}
