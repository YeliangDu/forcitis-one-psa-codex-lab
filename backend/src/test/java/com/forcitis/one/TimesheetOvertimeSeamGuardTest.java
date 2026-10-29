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
 * RULE-412 (user decision FD2-05): the timesheet context never reads an overtime approval or a compensatory-leave balance; the leave context owns both.
 * Hours filed on a non-workday are plain time records. The two contexts meet at one point only (RULE-413, leave hours of a day), which is a
 * later decision and a later rule, so this guard does not allow anything beyond it today.
 */
class TimesheetOvertimeSeamGuardTest {

    static final Path TIMESHEET_MAIN = Path.of("src/main/java/com/forcitis/one/cost/timesheet");
    static final Path TIMESHEET_MIGRATIONS = Path.of("src/main/resources/db/migration/cost_timesheet");
    static final Path TIMESHEET_PROCESS = Path.of("src/main/resources/processes/cost_timesheet_approval.bpmn20.xml");
    static final Path TIMESHEET_CONFIG = Path.of("src/main/resources/config/cost_timesheet.properties");
    static final Path MODULE_DEPENDENCIES = Path.of("architecture/module-dependencies.txt");
    static final String LEAVE_ROOT = "com.forcitis.one.organization.leave..";
    /** Terms of the overtime and compensatory-leave vocabulary as identifiers (camel or snake case) or table and column names; the lookbehind keeps words like compTimestamp out. */
    static final Pattern OVERTIME_WORD = Pattern.compile(
            "(?i)(?<![a-z])(overtime|over_time|compensatory|leave_balance_account|organization_leave)|(?<![A-Za-z])(OvertimeRecord|CompensatoryLeave|LeaveBalanceAccount)");

    @Test
    @DisplayName("RULE-412: no class of cost.timesheet depends on a class of the leave and overtime context")
    void timesheetClassesDoNotDependOnLeave() {
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ArchitectureRulesTest.ROOT);
        assertThat(classes.stream().filter(c -> c.getPackageName().startsWith("com.forcitis.one.cost.timesheet")))
                .as(GuardMessage.of("RULE-412", "the guard found no cost.timesheet classes, so it checks nothing",
                        "check the package name in TimesheetOvertimeSeamGuardTest")).isNotEmpty();
        noClasses().that().resideInAPackage("com.forcitis.one.cost.timesheet..")
                .should().dependOnClassesThat().resideInAPackage(LEAVE_ROOT)
                .because(GuardMessage.of("RULE-412",
                        "a cost.timesheet class depends on organization.leave: the timesheet context never reads an overtime approval or a compensatory-leave balance",
                        "remove the dependency; hours filed on a non-workday are plain time records and the leave context owns overtime and compensatory leave"))
                .check(classes);
    }

    @Test
    @DisplayName("RULE-412: cost.timesheet declares no dependency on the leave module in module-dependencies.txt")
    void moduleGraphHasNoEdgeToLeave() throws IOException {
        assertThat(leaveEdges(Files.readString(MODULE_DEPENDENCIES))).as(GuardMessage.of("RULE-412",
                "cost.timesheet declares a dependency on organization.leave in module-dependencies.txt",
                "delete organization.leave from the cost.timesheet line in " + MODULE_DEPENDENCIES)).isEmpty();
    }

    @Test
    @DisplayName("RULE-412: the timesheet code, tables, process and config name no overtime approval or compensatory-leave balance")
    void timesheetNamesNoOvertimeVocabulary() throws IOException {
        List<Path> scanned = new ArrayList<>();
        for (Path root : List.of(TIMESHEET_MAIN, TIMESHEET_MIGRATIONS)) {
            try (Stream<Path> files = Files.walk(root)) {
                scanned.addAll(files.filter(f -> f.toString().endsWith(".java") || f.toString().endsWith(".sql")).toList());
            }
        }
        scanned.add(TIMESHEET_PROCESS);
        scanned.add(TIMESHEET_CONFIG);
        assertThat(scanned).as(GuardMessage.of("RULE-412", "the guard scanned 2 or fewer timesheet files, so it checks nothing",
                "check the TIMESHEET_* paths in TimesheetOvertimeSeamGuardTest")).hasSizeGreaterThan(2);
        List<String> problems = new ArrayList<>();
        for (Path file : scanned) {
            problems.addAll(vocabularyIn(file.toString(), Files.readString(file)));
        }
        assertThat(problems).as(GuardMessage.of("RULE-412",
                "timesheet code, tables, process or config names an overtime or compensatory-leave term",
                "remove the term from cost.timesheet; the leave context owns overtime approvals and compensatory-leave balances")).isEmpty();
    }

    @Test
    @DisplayName("injected: an edge to the leave module and an overtime or comp-time name are caught")
    void theGuardCatchesInjectedSamples() {
        assertThat(leaveEdges("cost.timesheet -> organization.workforce, organization.leave\norganization.leave -> platform.audit"))
                .containsExactly("cost.timesheet -> organization.workforce, organization.leave");
        assertThat(leaveEdges("cost.timesheet -> organization.workforce\n# cost.timesheet -> organization.leave")).isEmpty();
        assertThat(vocabularyIn("T.java", "class Timesheet { Hours overtimeHours; CompensatoryLeave balance; }")).hasSize(2);
        assertThat(vocabularyIn("V.sql", "select * from organization_leave.leave_balance_accounts")).hasSize(2);
        assertThat(vocabularyIn("T.java", "class Timesheet { long compTimestamp; }")).isEmpty();
        assertThat(vocabularyIn("T.java", "class Timesheet { Hours workHours; }")).isEmpty();
    }

    static List<String> leaveEdges(String moduleDependencies) {
        return moduleDependencies.lines().filter(l -> !l.strip().startsWith("#")).filter(l -> l.strip().startsWith("cost.timesheet"))
                .filter(l -> l.matches(".*->.*\\borganization\\.leave\\b.*")).toList();
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
