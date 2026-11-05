package com.forcitis.one.organization.workforce.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AssignmentId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1372 review: the per-employee rule of design section 5 counts a command of the person against a command of a relationship, but not a command on a concurrent post. */
class OneCommandPerDayRulesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 10);
    private static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-0000000007e1"));
    private static final EmploymentRelationshipId RELATIONSHIP = new EmploymentRelationshipId(UUID.fromString("019a0000-0000-7000-8000-0000000007c1"));
    private static final AssignmentId POST = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-0000000007a2"));
    private static final AssignmentId PRIMARY = new AssignmentId(UUID.fromString("019a0000-0000-7000-8000-0000000007a1"));
    private static final UserId USER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000007aa"));
    private static int counter;

    private static PersonnelCommand stored(PersonnelCommandKind kind, AssignmentId post, AssignmentKind postKind, boolean ofRelationship) {
        PersonnelCommand c = PersonnelCommand.restore(new PersonnelCommandId(UUID.nameUUIDFromBytes(("d" + counter++).getBytes())), EMPLOYEE, kind, DAY, CommandStatus.PENDING,
                null, null, null, null, null, null, null, null, post, postKind, USER, Instant.parse("2026-11-01T00:00:00Z"), null, 1, 1);
        if (ofRelationship) {
            c.bindTo(RELATIONSHIP);
        }
        return c;
    }

    private static PersonnelHistory history(PersonnelCommand... commands) {
        return new PersonnelHistory(List.of(), List.of(), List.of(), List.of(), List.of(commands), Map.of());
    }

    @Test
    @DisplayName("#1372 评审必改4：结束兼职（关系命令）与同日变更法人（人命令）可并存")
    void endingAConcurrentPostDoesNotBlockTheEmployersChange() {
        PersonnelCommand end = stored(PersonnelCommandKind.END_CONCURRENT, POST, AssignmentKind.CONCURRENT, true);
        PersonnelCommand change = stored(PersonnelCommandKind.CHANGE_LEGAL_ENTITY, null, null, false);
        assertThatCode(() -> PersonnelCommandRules.requireOneCommandPerDay(change, history(end))).doesNotThrowAnyException();
        assertThatCode(() -> PersonnelCommandRules.requireOneCommandPerDay(end, history(change))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("#1372 评审必改4：主任职的更正与同日变更法人互相拒绝（更正可能等会签，不能豁免）")
    void aCorrectionOfThePrimaryPostStillClashesWithTheEmployersChange() {
        PersonnelCommand correction = stored(PersonnelCommandKind.CORRECTION, PRIMARY, AssignmentKind.PRIMARY, true);
        PersonnelCommand change = stored(PersonnelCommandKind.CHANGE_LEGAL_ENTITY, null, null, false);
        assertThatThrownBy(() -> PersonnelCommandRules.requireOneCommandPerDay(change, history(correction))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PersonnelCommandRules.requireOneCommandPerDay(correction, history(change))).isInstanceOf(DomainException.class);
    }
}
