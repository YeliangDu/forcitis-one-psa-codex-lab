package com.forcitis.one.acceptance.cost.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.cost.timesheet.api.TimesheetStatus;
import com.forcitis.one.cost.timesheet.domain.Timesheet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TS-UNIQ acceptance items 5 and 10 (card TS-UNIQ section 4, B3a): the status column's CHECK, the enum, the state machine and the
 * event catalogue agree on one set of five statuses, and a rejected timesheet is given back to its owner like a returned one.
 */
class TimesheetStatusConsistencyTest {

    static final Path BASELINE = Path.of("src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql");
    static final Path EVENT_CATALOGUE = Path.of("../docs/architecture/events/timesheet.yaml");

    /** The status each catalogued timesheet event moves the timesheet into. */
    static final Map<String, TimesheetStatus> EVENT_TARGETS = Map.of(
            "TimesheetSubmitted", TimesheetStatus.SUBMITTED,
            "TimesheetApproved", TimesheetStatus.APPROVED,
            "TimesheetRejected", TimesheetStatus.REJECTED,
            "TimesheetReturned", TimesheetStatus.RETURNED,
            "TimesheetApprovalWithdrawn", TimesheetStatus.DRAFT);

    private static Set<String> checkValues() throws IOException {
        Matcher check = Pattern.compile("status\\s+text[^\\n]*CHECK\\s*\\(status IN \\(([^)]*)\\)\\)").matcher(Files.readString(BASELINE));
        assertThat(check.find()).as("the status CHECK in %s", BASELINE).isTrue();
        return Arrays.stream(check.group(1).split(",")).map(value -> value.trim().replace("'", "")).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> catalogueEvents() throws IOException {
        Matcher message = Pattern.compile("(?m)^    (Timesheet\\w+):\\s*$").matcher(Files.readString(EVENT_CATALOGUE));
        Set<String> names = new LinkedHashSet<>();
        while (message.find()) {
            names.add(message.group(1));
        }
        return names;
    }

    private static Set<TimesheetStatus> machineStates() {
        Set<TimesheetStatus> states = EnumSet.noneOf(TimesheetStatus.class);
        for (TimesheetStatus from : TimesheetStatus.values()) {
            Set<TimesheetStatus> to = Timesheet.MACHINE.successors(from);
            if (!to.isEmpty()) {
                states.add(from);
                states.addAll(to);
            }
        }
        return states;
    }

    @Test
    @DisplayName("TS-UNIQ 状态 CHECK 取值 = TimesheetStatus 枚举 = Timesheet.MACHINE 的状态集合 = 事件目录各转换事件的目标状态集合，每个取值都有进入它的转换（初始状态除外）")
    void checkEnumMachineAndCatalogueAgreeOnTheStatuses() throws IOException {
        Set<String> enumNames = Arrays.stream(TimesheetStatus.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> machineNames = machineStates().stream().map(Enum::name).collect(Collectors.toSet());
        Set<String> eventTargets = EVENT_TARGETS.values().stream().map(Enum::name).collect(Collectors.toSet());

        assertThat(checkValues()).as("CHECK vs enum").containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(machineNames).as("state machine vs enum").containsExactlyInAnyOrderElementsOf(enumNames);
        assertThat(catalogueEvents()).as("the catalogued timesheet events, each with a known target status").containsExactlyInAnyOrderElementsOf(EVENT_TARGETS.keySet());
        assertThat(eventTargets).as("event targets vs enum").containsExactlyInAnyOrderElementsOf(enumNames);
        for (TimesheetStatus status : TimesheetStatus.values()) {
            boolean entered = Arrays.stream(TimesheetStatus.values()).anyMatch(from -> Timesheet.MACHINE.allows(from, status));
            assertThat(entered).as("a transition into %s", status).isTrue();
        }
    }

    @Test
    @DisplayName("TS-UNIQ REJECTED 与 DRAFT、RETURNED 一样可编辑，APPROVED 与 SUBMITTED 不可编辑")
    void rejectedIsEditableLikeDraftAndReturned() {
        assertThat(Arrays.stream(TimesheetStatus.values()).filter(TimesheetStatus::isEditable))
                .containsExactlyInAnyOrder(TimesheetStatus.DRAFT, TimesheetStatus.RETURNED, TimesheetStatus.REJECTED);
        assertThat(TimesheetStatus.APPROVED.isEditable()).isFalse();
        assertThat(TimesheetStatus.SUBMITTED.isEditable()).isFalse();
    }

    @Test
    @DisplayName("TS-UNIQ REJECTED 只有一条出边 REJECTED→SUBMITTED，APPROVED 仍是唯一终态")
    void rejectedGoesBackToSubmittedAndApprovedStaysTheOnlyEnd() {
        assertThat(Timesheet.MACHINE.successors(TimesheetStatus.REJECTED)).containsExactly(TimesheetStatus.SUBMITTED);
        assertThat(Arrays.stream(TimesheetStatus.values()).filter(status -> Timesheet.MACHINE.successors(status).isEmpty()))
                .containsExactly(TimesheetStatus.APPROVED);
    }
}
