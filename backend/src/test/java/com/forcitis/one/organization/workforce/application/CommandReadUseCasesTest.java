package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.SeparationReview;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandKind;
import com.forcitis.one.organization.workforce.api.OrgStructureTargetKind;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandKind;
import com.forcitis.one.organization.workforce.api.SeparationHoldId;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries;
import com.forcitis.one.organization.workforce.domain.CommandReadQueries.Scope;
import com.forcitis.one.organization.workforce.domain.CommandStatus;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import com.forcitis.one.organization.workforce.domain.HoldStatus;
import com.forcitis.one.organization.workforce.domain.OrgCommandStatus;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import com.forcitis.one.organization.workforce.domain.OrgUnit;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.PersonnelCommandRepository;
import com.forcitis.one.platform.audit.api.AuditEntry;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("#1172 command reads: fail-closed scope, paging, existing progress and release review")
class CommandReadUseCasesTest {
    static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 12, 1);
    static final EmployeeId EMPLOYEE = new EmployeeId(uuid(1));
    static final UserId USER = new UserId(uuid(2));
    static final PersonnelCommandId COMMAND = new PersonnelCommandId(uuid(3));
    static final SeparationHoldId HOLD = new SeparationHoldId(uuid(4));
    static final OrgUnitId UNIT = new OrgUnitId(uuid(5));
    static final OrgUnitId CHILD = new OrgUnitId(uuid(6));
    static final Scope EMPTY = new Scope(false, Set.of());
    static final Scope ALL = new Scope(true, Set.of());
    static final String PERMISSION = PersonnelCommandReadScope.PERMISSION;

    final CommandReadQueries queries = mock(CommandReadQueries.class);
    final OrgUnitAccess access = mock(OrgUnitAccess.class);
    final OrgSnapshotQueries snapshots = mock(OrgSnapshotQueries.class);
    final PersonnelCommandReadScope scope = new PersonnelCommandReadScope(access, snapshots);
    final CurrentUser currentUser = mock(CurrentUser.class);
    final AuditLog audit = mock(AuditLog.class);
    final GetPersonnelCommand get = new GetPersonnelCommand(queries, scope, audit, currentUser);
    final ListPersonnelCommands list = new ListPersonnelCommands(queries, scope);

    CommandReadUseCasesTest() {
        when(currentUser.userId()).thenReturn(USER);
    }

    @Test
    void permissionSpecificSubtreesAreExpandedBeforeTheQuery() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(new OrgUnitAccess.Visible(false, Set.of(), Set.of(UNIT.value())));
        when(snapshots.findAllOrgUnits()).thenReturn(List.of(unit(UNIT, null), unit(CHILD, UNIT)));
        Scope allowed = new Scope(false, Set.of(UNIT, CHILD));
        when(queries.personnel(allowed, CommandStatus.PENDING, PersonnelCommandKind.REGULARIZE, EMPLOYEE, null, 2)).thenReturn(List.of(personnel(COMMAND)));
        var result = list.execute(CommandReadViews.Filter.of("PENDING", "REGULARIZE"), EMPLOYEE, new PageRequest(1, null));
        assertThat(result.items()).extracting(CommandReadViews.Personnel::personnelCommandId).containsExactly(COMMAND.value());
        assertThat(result.nextCursor()).isNull();
        verify(queries).personnel(allowed, CommandStatus.PENDING, PersonnelCommandKind.REGULARIZE, EMPLOYEE, null, 2);
    }

    @Test
    void emptyScopeIsPassedToSqlEvenWithAnEmployeeFilter() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(OrgUnitAccess.Visible.nothing());
        when(queries.personnel(EMPTY, null, null, EMPLOYEE, null, 2)).thenReturn(List.of());
        var result = list.execute(CommandReadViews.Filter.of(null, null), EMPLOYEE, new PageRequest(1, null));
        assertThat(result.items()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        verify(queries).personnel(EMPTY, null, null, EMPLOYEE, null, 2);
    }

    @Test
    void allScopeNeedsNoDirectoryLookupAndPagingCarriesTheUniqueTiebreaker() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(OrgUnitAccess.Visible.all());
        PersonnelCommandId second = new PersonnelCommandId(uuid(7));
        when(queries.personnel(ALL, null, null, null, null, 2)).thenReturn(List.of(personnel(COMMAND), personnel(second)));
        var first = list.execute(CommandReadViews.Filter.of(null, null), null, new PageRequest(1, null));
        PageRequest next = PageRequest.of(1, first.nextCursor(), ListPersonnelCommands.LIST);
        var position = new CommandReadQueries.Position(NOW, COMMAND.value());
        when(queries.personnel(ALL, null, null, null, position, 2)).thenReturn(List.of(personnel(second)));
        var last = list.execute(CommandReadViews.Filter.of(null, null), null, next);
        assertThat(first.items()).extracting(CommandReadViews.Personnel::personnelCommandId).containsExactly(COMMAND.value());
        assertThat(last.items()).extracting(CommandReadViews.Personnel::personnelCommandId).containsExactly(second.value());
        assertThat(last.nextCursor()).isNull();
        verifyNoInteractions(snapshots);
    }

    @Test
    void unresolvableScopeIsForbiddenOnListAndNotFoundOnDetailWithoutExistenceLookup() {
        when(access.visibleOrgUnits(PERMISSION)).thenThrow(new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND, ErrorKind.FORBIDDEN, "scope unavailable"));
        assertThatThrownBy(() -> list.execute(CommandReadViews.Filter.of(null, null), null, new PageRequest(1, null)))
                .isInstanceOfSatisfying(DomainException.class, failure -> assertThat(failure.kind()).isEqualTo(ErrorKind.FORBIDDEN));
        notFound(() -> get.execute(COMMAND));
        verifyNoInteractions(queries, audit);
    }

    @Test
    void onlyAnExistingOutOfScopeIdIsIndependentlyAudited() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(OrgUnitAccess.Visible.nothing());
        when(queries.personnel(EMPTY, COMMAND)).thenReturn(Optional.empty());
        when(queries.employeeOf(COMMAND)).thenReturn(Optional.of(EMPLOYEE));
        notFound(() -> get.execute(COMMAND));
        ArgumentCaptor<AuditEntry> written = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).recordIndependently(written.capture());
        assertThat(written.getValue().action()).isEqualTo("organization.workforce.personnel-command.denied");
        assertThat(written.getValue().detail()).isEqualTo("outside the scope of the caller").doesNotContain("Synthetic-code");
    }

    @Test
    void unknownIdHasTheSameNotFoundCodeWithoutAudit() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(OrgUnitAccess.Visible.nothing());
        when(queries.personnel(EMPTY, COMMAND)).thenReturn(Optional.empty());
        when(queries.employeeOf(COMMAND)).thenReturn(Optional.empty());
        notFound(() -> get.execute(COMMAND));
        verifyNoInteractions(audit);
    }

    @Test
    void visibleHeldSeparationPreservesDatesAndHoldInformation() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(OrgUnitAccess.Visible.all());
        CommandReadQueries.Personnel held = new CommandReadQueries.Personnel(COMMAND, PersonnelCommandKind.SEPARATE, EMPLOYEE, "Synthetic", "Synthetic-code",
                DAY, CommandStatus.HELD, USER, NOW, null, null, null, HOLD, HoldStatus.HELD);
        when(queries.personnel(ALL, COMMAND)).thenReturn(Optional.of(held));
        var result = get.execute(COMMAND);
        assertThat(result.status()).isEqualTo("HELD");
        assertThat(result.lastWorkingDay()).isEqualTo(DAY.minusDays(1));
        assertThat(result.effectiveDate()).isEqualTo(DAY);
        assertThat(result.holdStatus()).isEqualTo("HELD");
        assertThat(result.separationHoldId()).isEqualTo(HOLD.value());
        verifyNoInteractions(audit);
    }

    @Test
    void withdrawnCommandReturnsStoredWithdrawalAttribution() {
        when(access.visibleOrgUnits(PERMISSION)).thenReturn(OrgUnitAccess.Visible.all());
        when(queries.personnel(ALL, COMMAND)).thenReturn(Optional.of(new CommandReadQueries.Personnel(COMMAND, PersonnelCommandKind.TRANSFER, EMPLOYEE,
                "Synthetic", "Synthetic-code", DAY, CommandStatus.WITHDRAWN, USER, NOW, USER, NOW.plusSeconds(1), "Synthetic reason", null, null)));
        var result = get.execute(COMMAND);
        assertThat(result.withdrawnBy()).isEqualTo(USER.value());
        assertThat(result.withdrawnAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(result.withdrawalReason()).isEqualTo("Synthetic reason");
        assertThat(result.lastWorkingDay()).isNull();
    }

    @Test
    void orgListAndDetailUseStoredMergeProgressAndExistingCountersigns() {
        OrgStructureCommandId id = new OrgStructureCommandId(uuid(8));
        var row = new CommandReadQueries.Structure(id, OrgStructureCommandKind.MERGE, OrgStructureTargetKind.ORG_UNIT, UNIT, null, DAY, OrgCommandStatus.EFFECTIVE, 3, 3, NOW);
        var request = new CommandReadQueries.Countersign(new AttributionCountersignId(uuid(9)), CountersignRequestKind.CHANGE, new LegalEntityId(uuid(10)), CountersignStatus.OPEN);
        when(queries.countersigns(id)).thenReturn(List.of(request));
        when(queries.structure(id)).thenReturn(Optional.of(row));
        when(queries.structures(null, 2)).thenReturn(List.of(row));
        var detail = new GetOrgStructureCommand(queries).execute(id);
        var page = new ListOrgStructureCommands(queries).execute(new PageRequest(1, null));
        assertThat(page.items()).containsExactly(detail);
        assertThat(detail.mergeProgress()).isEqualTo(new CommandReadViews.MergeProgress(3, 3));
        assertThat(detail.countersigns()).singleElement().satisfies(r -> assertThat(r.decisionKind()).isNull());
        verifyNoInteractions(access, snapshots);
    }

    @Test
    void holdReadsUseReleaseReviewExclusionsAndCanReadAReleasedDetail() {
        PersonnelCommandRepository commands = mock(PersonnelCommandRepository.class);
        SeparationReview kernel = mock(SeparationReview.class);
        SeparationHoldReview review = new SeparationHoldReview(commands, kernel);
        EmployeeId futureReleased = new EmployeeId(uuid(11));
        Set<EmployeeId> excluded = Set.of(EMPLOYEE, futureReleased);
        when(commands.excludedEmployees()).thenReturn(excluded);
        when(kernel.review(List.of(EMPLOYEE), excluded, USER)).thenReturn(new SeparationReview.Verdict(Set.of(), true, 2, "", "", Set.of()));
        var held = new CommandReadQueries.Hold(HOLD, COMMAND, EMPLOYEE, "Synthetic", "Synthetic-code", UNIT, "Synthetic department", DAY, HoldStatus.HELD, NOW);
        var released = new CommandReadQueries.Hold(HOLD, COMMAND, EMPLOYEE, "Synthetic", "Synthetic-code", UNIT, "Synthetic department", DAY, HoldStatus.RELEASED, NOW);
        when(queries.holds(null, 2)).thenReturn(List.of(held));
        when(queries.hold(HOLD)).thenReturn(Optional.of(released));
        var page = new ListSeparationHolds(queries, review, currentUser).execute(null, new PageRequest(1, null));
        assertThat(page.items()).singleElement().satisfies(r -> {
            assertThat(r.remainingAvailableSuperAdministratorCount()).isEqualTo(2);
            assertThat(r.originalEffectiveDate()).isEqualTo(DAY);
            assertThat(r.status()).isEqualTo("HELD");
        });
        assertThat(new GetSeparationHold(queries, review, currentUser).execute(HOLD).status()).isEqualTo("RELEASED");
        verifyNoInteractions(access, snapshots);
    }

    @Test
    void invalidFiltersAndCursorKeysAreBadRequests() {
        assertThatThrownBy(() -> CommandReadViews.Filter.of("UNKNOWN", null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> CommandReadViews.Filter.of(null, "UNKNOWN")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> CommandReadViews.Filter.of("", null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> CommandReadViews.Filter.of(null, "")).isInstanceOf(InvalidRequestException.class);
        for (String key : List.of("missing-separator", "garbage|" + COMMAND.value(), "10000-01-01T00:00:00Z|" + COMMAND.value(), "+300000-01-01T00:00:00Z|" + COMMAND.value(), NOW + "|UNKNOWN")) {
            assertThatThrownBy(() -> CommandReadViews.position(key)).isInstanceOf(InvalidCursorException.class);
        }
        var holds = new ListSeparationHolds(queries, mock(SeparationHoldReview.class), currentUser);
        for (String status : List.of("RELEASED", "WITHDRAWN", "UNKNOWN")) {
            assertThatThrownBy(() -> holds.execute(status, new PageRequest(1, null))).isInstanceOf(InvalidRequestException.class);
        }
        verifyNoInteractions(queries);
    }

    private static void notFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, failure -> {
            assertThat(failure.kind()).isEqualTo(ErrorKind.NOT_FOUND);
            assertThat(failure.errorCode()).isEqualTo(WorkforceError.ORGANIZATION_WORKFORCE_PERSONNEL_COMMAND_NOT_FOUND);
            assertThat(failure.getMessage()).isEqualTo("no such personnel command");
        });
    }

    private static CommandReadQueries.Personnel personnel(PersonnelCommandId id) {
        return new CommandReadQueries.Personnel(id, PersonnelCommandKind.REGULARIZE, EMPLOYEE, "Synthetic", "Synthetic-code", DAY,
                CommandStatus.PENDING, USER, NOW, null, null, null, null, null);
    }

    private static OrgUnit unit(OrgUnitId id, OrgUnitId parent) {
        return new OrgUnit(id, "Synthetic", "Synthetic unit", parent, 0, OrgUnitStatus.ACTIVE, null, null, null, false, null);
    }

    private static UUID uuid(int tail) {
        return UUID.fromString("019a0000-0000-7000-8000-%012d".formatted(tail));
    }
}
