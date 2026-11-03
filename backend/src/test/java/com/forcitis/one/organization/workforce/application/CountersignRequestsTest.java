package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.PersonnelCommand;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** #895 one-design ruling (6595446112): all legal-entity rows of one withdrawal request carry the requester's text; a change request carries none. */
class CountersignRequestsTest {
    static final Instant NOW = Instant.parse("2026-11-02T02:00:00Z");
    static final LegalEntityId E1 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e1"));
    static final LegalEntityId E2 = new LegalEntityId(UUID.fromString("019a0000-0000-7000-8000-0000000000e2"));

    final AttributionCountersignRepository countersigns = mock(AttributionCountersignRepository.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final CountersignDueDates dueDates = mock(CountersignDueDates.class);
    final CountersignRequests requests = new CountersignRequests(countersigns, mock(PersonnelCommandRepository.class), dueDates, mock(AuditLog.class), ids);
    final AtomicLong next = new AtomicLong(1);

    CountersignRequestsTest() {
        when(ids.next()).thenAnswer(call -> new UUID(0x019a000000007000L, 0x8000000000000000L + next.getAndIncrement()));
        when(dueDates.after(any(), any())).thenReturn(LocalDate.of(2026, 11, 5));
    }

    private static PersonnelCommand countersigned() {
        return PersonnelCommand.restore(new PersonnelCommandId(UUID.fromString("019a0000-0000-7000-8000-0000000000c1")),
                new EmployeeId(UUID.fromString("019a0000-0000-7000-8000-000000000001")), PersonnelCommandKind.CHANGE_LEGAL_ENTITY, LocalDate.of(2026, 11, 9),
                CommandStatus.PENDING, null, null, null, null, null, E2, E1, E2, null, null, new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000aa")), NOW, null, 0, 0);
    }

    @Test
    @DisplayName("#895 a withdrawal request writes one row per required legal entity, all with the requester's reason and sequence 2 after a first request; a change request writes no reason")
    void everyRowOfAWithdrawalCarriesTheReason() {
        PersonnelCommand command = countersigned();
        UserId by = new UserId(UUID.fromString("019a0000-0000-7000-8000-0000000000bb"));

        requests.open(command, CountersignRequestKind.CHANGE, null, List.of(), by, NOW, LocalDate.of(2026, 11, 2), UUID.randomUUID());
        requests.open(command, CountersignRequestKind.WITHDRAWAL, "plan changed", List.of(), by, NOW, LocalDate.of(2026, 11, 2), UUID.randomUUID());

        ArgumentCaptor<AttributionCountersign> written = ArgumentCaptor.forClass(AttributionCountersign.class);
        verify(countersigns, times(4)).insert(written.capture());
        List<AttributionCountersign> rows = written.getAllValues();
        assertThat(rows.subList(0, 2)).extracting(AttributionCountersign::requestReason).containsOnlyNulls();
        assertThat(rows.subList(2, 4)).extracting(AttributionCountersign::requestReason).containsOnly("plan changed");
        assertThat(rows.subList(2, 4)).extracting(AttributionCountersign::legalEntityId).containsExactlyInAnyOrder(E1, E2);
    }
}
