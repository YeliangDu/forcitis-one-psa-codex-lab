package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommand;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("#910 S3 a read of a countersign that the caller may not decide is answered not found and the attempt is audited")
class CountersignReadAccessTest {
    private final AttributionCountersignRepository countersigns = mock(AttributionCountersignRepository.class);
    private final OrgStructureCommandRepository orgCommands = mock(OrgStructureCommandRepository.class);
    private final CountersignDeciders deciders = mock(CountersignDeciders.class);
    private final CallerPermissions permissions = mock(CallerPermissions.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UserId caller = new UserId(UUID.fromString("00000000-0000-7000-8000-000000000001"));
    private final CountersignReadAccess access = new CountersignReadAccess(countersigns, mock(PersonnelCommandRepository.class), orgCommands, deciders, permissions, audit, currentUser,
            Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC), new BusinessZone(ZoneOffset.UTC));

    CountersignReadAccessTest() {
        when(currentUser.userId()).thenReturn(caller);
    }

    @Test
    void aNonDeciderIsAuditedAndToldNotFound() {
        AttributionCountersignId id = new AttributionCountersignId(UUID.fromString("00000000-0000-7000-8000-0000000000a1"));
        AttributionCountersign row = mock(AttributionCountersign.class);
        when(row.isOrg()).thenReturn(true);
        when(row.orgCommandId()).thenReturn(new OrgStructureCommandId(UUID.fromString("00000000-0000-7000-8000-0000000000b1")));
        when(countersigns.find(id)).thenReturn(Optional.of(row));
        when(deciders.mayDecide(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> access.ofRequest(id)).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).recordIndependently(entry.capture());
        assertThat(entry.getValue().action()).isEqualTo("organization.workforce.countersign-read.denied");
        assertThat(entry.getValue().actorId()).isEqualTo(caller);
    }

    @Test
    void aHolderOutsideTheBasisSetIsAuditedAndToldNotFound() {
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.fromString("00000000-0000-7000-8000-0000000000b2"));
        when(orgCommands.find(command)).thenReturn(Optional.of(mock(OrgStructureCommand.class)));
        when(permissions.holds(OrgStructureCommandService.PERMISSION)).thenReturn(false);
        when(countersigns.ofOrgCommand(command)).thenReturn(List.of());

        assertThatThrownBy(() -> access.requireBasisReadable(command)).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));

        verify(audit).recordIndependently(any());
    }

    @Test
    void theOrgDataAdministratorIsNotAudited() {
        OrgStructureCommandId command = new OrgStructureCommandId(UUID.fromString("00000000-0000-7000-8000-0000000000b3"));
        when(orgCommands.find(command)).thenReturn(Optional.of(mock(OrgStructureCommand.class)));
        when(permissions.holds(OrgStructureCommandService.PERMISSION)).thenReturn(true);

        access.requireBasisReadable(command);

        verify(audit, never()).recordIndependently(any());
    }
}
