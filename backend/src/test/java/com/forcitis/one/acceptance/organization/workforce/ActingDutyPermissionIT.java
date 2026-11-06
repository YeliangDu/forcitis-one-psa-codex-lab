package com.forcitis.one.acceptance.organization.workforce;

import static com.forcitis.one.acceptance.organization.workforce.ActingDutyNames.*;
import static com.forcitis.one.acceptance.organization.workforce.PersonnelCommandNames.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #1566 A4: the transfer permission, PersonnelCommandNames.PERMISSION, guards all three acting commands. */
class ActingDutyPermissionIT extends ActingDutySupport {
    static final TestDatabase postgres = TestPostgres.database(ActingDutyPermissionIT.class, TestPostgres.MIGRATED)
            .withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    static Stream<Arguments> forbiddenCallers() {
        return Stream.of(ACTING_START, ACTING_END, ACTING_EXTEND).flatMap(kind -> Stream.of(
                "super_admin", "org_data_admin", "business_admin", "security_auditor", "process_administrator",
                "employee", "no_session", "security_auditor_and_business_admin").map(role -> Arguments.of(kind, role)));
    }

    @ParameterizedTest(name = "{0}, caller {1}")
    @MethodSource("forbiddenCallers")
    @DisplayName("#1566 A4 every other seeded role, employee-only, no session and a two-role holder without the personnel specialist role are refused")
    void everyUnprivilegedCallerIsRefused(String kind, String role) throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        LocalDate day = TODAY.plusDays(1);
        advanceTo(day);
        Map<String, Object> fields = fieldsFor(kind, employee, duty, day);
        Staff caller = switch (role) {
            case "no_session" -> null;
            case "employee" -> staff();
            // two granted roles, none of them the personnel specialist, on top of the default employee role.
            case "security_auditor_and_business_admin" -> staff(SECURITY_AUDITOR, BUSINESS_ADMIN);
            default -> staff(role);
        };
        assertPermissionRefused(employee, caller, fields);
    }

    @ParameterizedTest
    @ValueSource(strings = {ACTING_END, ACTING_EXTEND})
    @DisplayName("#1566 A4 being the stand-in gives an employee no right to end or extend their own duty")
    void theStandInCannotChangeTheirOwnDuty(String kind) throws Exception {
        UUID employee = employed(REGULAR);
        Staff standIn = new Staff(employee, signIn(employee));
        actIn(hr);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        LocalDate day = TODAY.plusDays(1);
        advanceTo(day);
        standIn = signedInAgain(standIn);

        assertPermissionRefused(employee, standIn, fieldsFor(kind, employee, duty, day));
    }

    @ParameterizedTest
    @ValueSource(strings = {ACTING_START, ACTING_END, ACTING_EXTEND})
    @DisplayName("#1566 A4 a real personnel_specialist holder can enter each acting kind using the transfer permission")
    void thePersonnelSpecialistCanEnterEachKind(String kind) throws Exception {
        UUID employee = employed(REGULAR);
        UUID duty = dutyOf(start(employee, newUnit(), newPosition(), TODAY, TODAY.plusDays(10)));
        LocalDate day = TODAY.plusDays(1);
        advanceTo(day);
        Map<String, Object> fields = fieldsFor(kind, employee, duty, day);
        long before = commandCount(employee);
        actIn(hr);

        UUID command = commands().enter(fields);

        assertThat(command(command).get("status")).isEqualTo(EFFECTIVE);
        assertThat(command(command).get("command_kind")).isEqualTo(kind);
        assertThat(commandCount(employee)).isEqualTo(before + 1);
        switch (kind) {
            case ACTING_START -> assertOpen(dutyOf(command), day, day.plusDays(4));
            case ACTING_END -> assertEnded(duty, day, "ENDED_EARLY");
            case ACTING_EXTEND -> assertOpen(duty, TODAY, day.plusDays(15));
            default -> throw new AssertionError(kind);
        }
    }

    private Map<String, Object> fieldsFor(String kind, UUID employee, UUID duty, LocalDate day) throws Exception {
        return switch (kind) {
            case ACTING_START -> startFields(employee, newUnit(), newPosition(), null, day, day.plusDays(4));
            case ACTING_END -> changeFields(employee, kind, duty, day, null);
            case ACTING_EXTEND -> changeFields(employee, kind, duty, day, day.plusDays(15));
            default -> throw new AssertionError(kind);
        };
    }

    private void assertPermissionRefused(UUID employee, Staff caller, Map<String, Object> fields) throws Exception {
        long commandsBefore = commandCount(employee);
        int auditedBefore = auditCount(AUDIT_ENTERED);
        List<Map<String, Object>> dutiesBefore = duties(employee);
        if (caller == null) {
            actAsNobody();
        } else {
            actIn(caller);
        }

        Throwable failure = catchThrowable(() -> commands().enter(fields));

        assertThat(failure).as("entry is refused").isNotNull();
        assertThat(isPermissionRefusal(failure)).as("permission refusal for " + PERMISSION + ": " + failure).isTrue();
        assertThat(commandCount(employee)).isEqualTo(commandsBefore);
        assertThat(auditCount(AUDIT_ENTERED)).isEqualTo(auditedBefore);
        assertThat(duties(employee)).isEqualTo(dutiesBefore);
    }
}
