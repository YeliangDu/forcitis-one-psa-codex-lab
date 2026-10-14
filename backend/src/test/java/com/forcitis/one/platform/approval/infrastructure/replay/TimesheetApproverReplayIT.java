package com.forcitis.one.platform.approval.infrastructure.replay;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * C1a-13 / REIMAGINED_ARCHITECTURE §3.5 (a) on a SYNTHETIC export (made-up ids, no real person, no real organisation): the replay
 * computes every node's candidate set with the live AssigneeResolver from the exported old snapshot and compares by "every old
 * approver is in the new candidate set". Real exports stay in ~/.modernize/psa/ and never reach the repository.
 */
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

        assertThat(report.instances()).isEqualTo(8);
        assertThat(report.nodes()).isEqualTo(11);
        assertThat(report.count(ReplayVerdict.MATCH)).isEqualTo(5);
        assertThat(report.count(ReplayVerdict.REVIEWED_REPORTING_LINE)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_DISABLED_APPROVER)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_HARDCODED_FALLBACK)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_SELF_APPROVAL)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.REVIEWED_ADMINISTRATOR_FALLBACK)).isEqualTo(1);
        assertThat(report.count(ReplayVerdict.DEFECT)).isEqualTo(1);
        assertThat(report.hasDefects()).isTrue();
        assertThat(report.differences()).hasSize(5 + 1);
        assertThat(report.differences()).filteredOn(d -> d.verdict() == ReplayVerdict.DEFECT).singleElement().satisfies(d -> {
            assertThat(d.instanceId()).isEqualTo("SYN-7");
            assertThat(d.nodeKey()).isEqualTo("directorReview");
            assertThat(d.ruleType()).isEqualTo("ROLE");
            assertThat(d.candidates()).containsExactly(110L, 112L);
            assertThat(d.actualApprovers()).containsExactly(105L);
        });
        assertThat(report.resolutionCounts().get(ResolutionKind.RESOLVED)).isEqualTo(6);
    }

    @Test
    @DisplayName("RULE-249 'old approver in the new candidate set': one of several role holders matches, all countersigners must be members")
    void matchingIsMembership() throws IOException {
        ReplayReport report = TimesheetApproverReplay.replay(fixture());
        assertThat(report.differences()).noneMatch(d -> d.instanceId().equals("SYN-8"));
        assertThat(report.differences()).noneMatch(d -> d.instanceId().equals("SYN-1"));
    }

    @Test
    @DisplayName("the replay is deterministic and its report names ids only (the markdown holds no name)")
    void deterministicAndIdsOnly() throws IOException {
        ReplayReport first = TimesheetApproverReplay.replay(fixture());
        ReplayReport second = TimesheetApproverReplay.replay(fixture());
        assertThat(second).isEqualTo(first);
        assertThat(first.toMarkdown()).contains("| DEFECT | 1 |").contains("SYN-7").doesNotContain("person-").doesNotContain("exported");
    }

    @Test
    @DisplayName("a fixture without the defect passes the gate")
    void cleanExportPasses() throws IOException {
        ReplayExport all = fixture();
        ReplayExport clean = new ReplayExport(all.formatVersion(), all.processKey(), all.processAdministratorUserId(),
                all.legacyFallbackUserIds(), all.snapshot(), all.instances().stream().filter(i -> !i.instanceId().equals("SYN-7")).toList());
        assertThat(TimesheetApproverReplay.replay(clean).hasDefects()).isFalse();
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
                all.legacyFallbackUserIds(), all.snapshot(), all.instances().stream().filter(i -> !i.instanceId().equals("SYN-7")).toList());
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
    @DisplayName("the old-system exporter vocabulary is exactly the rule codec's: all ten rule types are accepted")
    void ruleVocabulary() {
        for (String type : List.of("ROLE", "DEPARTMENT_HEAD", "INITIATOR_SUPERVISOR", "NAMED_USERS", "REGIONAL_RECEIVABLES_MANAGER",
                "DEPARTMENT_DIRECTOR", "FORM_FIELD_USERS", "FORM_FIELD_USER_SUPERVISORS", "PROCESS_VARIABLE_USERS", "INITIATOR")) {
            assertThat(com.forcitis.one.platform.approval.domain.AssignmentRuleCodec.parse(type,
                    name -> switch (name) {
                        case "role" -> "R";
                        case "level" -> "1";
                        case "users" -> "1";
                        case "field", "variable" -> "f";
                        default -> null;
                    })).as(type).isNotNull();
        }
    }
}
