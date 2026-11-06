package com.forcitis.one.organization.workforce.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.ActingDutyEndCause;
import com.forcitis.one.organization.workforce.api.ActingDutyId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.domain.ActingDuty;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcActingDutyRepositoryTest {
    static final Instant NOW = Instant.parse("2026-11-06T08:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    static final ActingDuty DUTY = new ActingDuty(new ActingDutyId(TestIds.uuid(1)), new EmployeeId(TestIds.uuid(2)),
            new EmploymentRelationshipId(TestIds.uuid(3)), new OrgUnitId(TestIds.uuid(4)), null, null,
            DAY.minusDays(2), DAY.plusDays(3), null, "coverage", new PersonnelCommandId(TestIds.uuid(5)), new UserId(TestIds.uuid(6)), NOW, 2);
    final JdbcClient jdbc = mock(JdbcClient.class);
    final JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class, Answers.RETURNS_SELF);
    final JdbcActingDutyRepository repository = new JdbcActingDutyRepository(jdbc);

    JdbcActingDutyRepositoryTest() {
        when(jdbc.sql(anyString())).thenReturn(statement);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 an end or extension whose optimistic update changes no row fails loudly")
    void staleWriteIsRefused(boolean extension) {
        when(statement.update()).thenReturn(0);
        assertThatThrownBy(() -> write(extension)).isInstanceOf(DomainException.class).hasMessageContaining("changed by someone else");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 an end or extension whose versioned update succeeds is accepted")
    void matchingVersionIsAccepted(boolean extension) {
        when(statement.update()).thenReturn(1);
        assertThatCode(() -> write(extension)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("#1566 database constraint failures from duty writes propagate to the transaction")
    void constraintsPropagate(boolean extension) {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("constraint backstop");
        when(statement.update()).thenThrow(conflict);
        assertThatThrownBy(() -> write(extension)).isSameAs(conflict);
    }

    private void write(boolean extension) {
        if (extension) {
            repository.updateEndsBefore(DUTY, DAY.plusDays(7), NOW);
        } else {
            repository.updateEnd(DUTY, DAY, ActingDutyEndCause.ENDED_EARLY, NOW);
        }
    }
}
