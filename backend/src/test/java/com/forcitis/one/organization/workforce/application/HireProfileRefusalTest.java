package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.GlobalDocumentNumberSource;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.EmploymentPhaseKind;
import com.forcitis.one.organization.workforce.api.EnterPersonnelCommand;
import com.forcitis.one.organization.workforce.api.Gender;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntityResolutionSources;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.organization.workforce.domain.SeparationHoldCheck;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #959: the two refusals at the entry of a command that the acceptance tests cannot see, because nothing downstream would act on the ignored fields: a HIRE
 * never names an employee (the server makes the id), and no other kind carries a profile (a profile of an existing employee is changed by its own use case).
 */
class HireProfileRefusalTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final EmployeeId EMPLOYEE = new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001"));
    static final UserId USER = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa"));

    final PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
    final GlobalDocumentNumberSource numbers = mock(GlobalDocumentNumberSource.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final PersonnelCommandService service;

    HireProfileRefusalTest() {
        when(ids.next()).thenReturn(UUID.fromString("019a0000-0000-7000-8000-0000000000c1"));
        when(currentUser.userId()).thenReturn(USER);
        when(commands.lockEmployee(any())).thenReturn(true);
        service = new PersonnelCommandService(commands, new NewEmployees(commands, numbers, ids), new PersonnelPlans(ids), mock(SeparationHoldCheck.class),
                mock(UnpaidLeaveStartFloor.class), mock(LegalEntityResolutionSources.class), mock(AuditLog.class), mock(DomainEvents.class), currentUser, ids,
                Clock.fixed(NOW, ZoneOffset.UTC), new BusinessZone(ZoneId.of("Asia/Shanghai")));
    }

    private static EnterPersonnelCommand hire(EmployeeId employee) {
        return new EnterPersonnelCommand(employee, PersonnelCommandKind.HIRE, LocalDate.of(2026, 11, 2), null, null, null, null, true,
                EmploymentPhaseKind.PROBATION, "Synthetic", null, null, null);
    }

    @Test
    @DisplayName("#959 a hire that names an employee is refused before anything is numbered or written")
    void aHireNamingAnEmployeeIsRefused() {
        assertThatThrownBy(() -> service.enter(hire(EMPLOYEE))).isInstanceOfSatisfying(DomainException.class,
                refusal -> assertThat(refusal.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID));

        verifyNoInteractions(numbers);
    }

    @Test
    @DisplayName("#959 a command of any other kind that carries a name, gender, email or WeCom user id is refused before anything is written")
    void aProfileOnAnotherKindIsRefused() {
        for (EnterPersonnelCommand request : new EnterPersonnelCommand[] {
                new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.REGULARIZE, LocalDate.of(2026, 11, 2), null, null, null, null, null, null, "Synthetic", null, null, null),
                new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.REGULARIZE, LocalDate.of(2026, 11, 2), null, null, null, null, null, null, null, Gender.MALE, null, null),
                new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.REGULARIZE, LocalDate.of(2026, 11, 2), null, null, null, null, null, null, null, null, "a@example.invalid", null),
                new EnterPersonnelCommand(EMPLOYEE, PersonnelCommandKind.REGULARIZE, LocalDate.of(2026, 11, 2), null, null, null, null, null, null, null, null, null,
                        new WecomUserId("synthetic-wecom"))}) {
            assertThatThrownBy(() -> service.enter(request)).isInstanceOfSatisfying(DomainException.class,
                    refusal -> assertThat(refusal.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_INVALID));
        }

        verifyNoMoreInteractions(commands);
    }
}
