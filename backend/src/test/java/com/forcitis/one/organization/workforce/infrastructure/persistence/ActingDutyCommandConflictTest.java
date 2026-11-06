package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

class ActingDutyCommandConflictTest {
    final Instant now = Instant.parse("2026-11-06T08:00:00Z");
    final LocalDate day = LocalDate.of(2026, 11, 6);
    final EmployeeId employee = new EmployeeId(TestIds.uuid(1));
    final JdbcClient jdbc = mock(JdbcClient.class);
    final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, Answers.RETURNS_SELF);
    final JdbcPersonnelCommandRepository repository = new JdbcPersonnelCommandRepository(jdbc, () -> TestIds.uuid(2),
            new BusinessZone(ZoneId.of("Asia/Shanghai")), Clock.fixed(now, ZoneId.of("Asia/Shanghai")));

    PersonnelCommand pendingEnd() {
        EnterPersonnelCommand request = new EnterPersonnelCommand(employee, PersonnelCommandKind.ACTING_END, day.plusDays(1), null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                new EmploymentRelationshipId(TestIds.uuid(3)), null, null, null, null, new ActingDutyId(TestIds.uuid(4)), "end cover");
        return PersonnelCommand.enter(new PersonnelCommandId(TestIds.uuid(5)), employee, request, new UserId(TestIds.uuid(6)), now, day, 0);
    }

    @Test
    @DisplayName("#1566 the pending-end unique backstop becomes a clean business refusal")
    void duplicatePendingEndIsARefusal() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        when(statement.update()).thenThrow(new DuplicateKeyException("unique violation", new UniqueViolation("uk_personnel_commands_open_acting_end")));
        assertThatThrownBy(() -> repository.insert(pendingEnd())).isInstanceOf(DomainException.class).hasMessageContaining("pending early end");
    }

    @Test
    @DisplayName("#1566 unrelated unique failures are propagated unchanged")
    void otherConstraintIsNotMisclassified() {
        when(jdbc.sql(anyString())).thenReturn(statement);
        DuplicateKeyException failure = new DuplicateKeyException("unique violation", new UniqueViolation("pk_personnel_commands"));
        when(statement.update()).thenThrow(failure);
        assertThatThrownBy(() -> repository.insert(pendingEnd())).isSameAs(failure);
    }

    public static final class UniqueViolation extends SQLException {
        private final Detail detail;

        UniqueViolation(String constraint) {
            super("unique violation", "23505");
            detail = new Detail(constraint);
        }

        public Detail getServerErrorMessage() {
            return detail;
        }
    }

    public record Detail(String constraint) {
        public String getConstraint() {
            return constraint;
        }
    }
}
