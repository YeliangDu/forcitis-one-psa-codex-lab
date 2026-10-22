package com.forcitis.one;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RULE-412 (user decision FD2-05): the timesheet context never reads an overtime approval or a comp-time balance; the leave context owns both.
 * Hours filed on a non-workday are plain time records. The two contexts meet at one point only (RULE-413, leave hours of a day), which is a
 * later decision and a later rule, so this guard does not allow anything beyond it today.
 */
class TimesheetOvertimeSeamGuardTest {

    static final Path TIMESHEET_MAIN = Path.of("src/main/java/com/forcitis/one/cost/timesheet");
    static final Path TIMESHEET_MIGRATIONS = Path.of("src/main/resources/db/migration/cost_timesheet");
    static final Path MODULE_DEPENDENCIES = Path.of("architecture/module-dependencies.txt");
    /** Words of the overtime and comp-time vocabulary, as identifiers (camel or snake case) or as table and column names. */
    static final Pattern OVERTIME_WORD = Pattern.compile("(?i)(overtime|over_time|comp_?time|compensatory|toil_balance)");

    @Test
    @DisplayName("RULE-412: no class of cost.timesheet depends on a class of the leave and overtime context")
    void timesheetClassesDoNotDependOnLeave() {
        noClasses().that().resideInAPackage("..cost.timesheet..")
                .should().dependOnClassesThat().resideInAnyPackage("..organization.leave..", "..leave..")
                .allowEmptyShould(true)
                .because("RULE-412: the timesheet context never reads an overtime approval or a comp-time balance; the leave context owns both")
                .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ArchitectureRulesTest.ROOT));
    }

    @Test
    @DisplayName("RULE-412: cost.timesheet declares no dependency on the leave module in module-dependencies.txt")
    void moduleGraphHasNoEdgeToLeave() throws IOException {
        assertThat(leaveEdges(Files.readString(MODULE_DEPENDENCIES))).isEmpty();
    }

    @Test
    @DisplayName("RULE-412: the timesheet code and tables name no overtime approval or comp-time balance")
    void timesheetNamesNoOvertimeVocabulary() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path root : List.of(TIMESHEET_MAIN, TIMESHEET_MIGRATIONS)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".sql")).toList()) {
                    problems.addAll(vocabularyIn(file.toString(), Files.readString(file)));
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    @Test
    @DisplayName("injected: an edge to the leave module and an overtime or comp-time name are caught")
    void theGuardCatchesInjectedSamples() {
        assertThat(leaveEdges("cost.timesheet -> organization.workforce, organization.leave\norganization.leave -> platform.audit"))
                .containsExactly("cost.timesheet -> organization.workforce, organization.leave");
        assertThat(leaveEdges("cost.timesheet -> organization.workforce\n# cost.timesheet -> organization.leave")).isEmpty();
        assertThat(vocabularyIn("T.java", "class Timesheet { Hours overtimeHours; CompTimeBalance balance; }")).hasSize(2);
        assertThat(vocabularyIn("V.sql", "CREATE TABLE cost_timesheet.t (comp_time_balance numeric);")).hasSize(1);
        assertThat(vocabularyIn("T.java", "class Timesheet { Hours workHours; }")).isEmpty();
    }

    static List<String> leaveEdges(String moduleDependencies) {
        return moduleDependencies.lines().filter(l -> !l.strip().startsWith("#")).filter(l -> l.strip().startsWith("cost.timesheet"))
                .filter(l -> l.contains("organization.leave") || l.matches(".*->.*\\bleave\\b.*")).toList();
    }

    static List<String> vocabularyIn(String where, String text) {
        List<String> found = new ArrayList<>();
        var m = OVERTIME_WORD.matcher(text);
        while (m.find()) {
            found.add(where + " names '" + m.group() + "'");
        }
        return found;
    }
}
