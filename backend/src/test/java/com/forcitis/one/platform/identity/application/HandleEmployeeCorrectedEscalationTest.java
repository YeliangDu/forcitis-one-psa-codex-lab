package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.kernel.WorkdayDeadlines;
import com.forcitis.one.kernel.event.ActorType;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.kernel.event.EventEnvelope;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.DeliveryPurpose;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentKind;
import com.forcitis.one.platform.identity.api.AccessScopeKind;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PositionId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.organization.workforce.api.TriggerFieldKind;
import com.forcitis.one.organization.workforce.events.EmployeeCorrected;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.api.RoleId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.identity.domain.RoleRepository;
import com.forcitis.one.platform.identity.domain.UserAccount;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

@DisplayName("#730 A-6: the escalation deadline of a review item follows the work calendar of the legal entity of the event; none falls back to the default calendar with one P3 alert, and the item is still raised")
class HandleEmployeeCorrectedEscalationTest {

    static final Instant NOW = Instant.parse("2026-11-03T02:00:00Z");
    static final Instant DUE = Instant.parse("2026-11-09T16:00:00Z");

    final AccountRepository accounts = mock(AccountRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final PermissionAdjustmentRepository adjustments = mock(PermissionAdjustmentRepository.class);
    final WorkdayDeadlines deadlines = mock(WorkdayDeadlines.class);
    final DomainEvents events = mock(DomainEvents.class);
    final IdGenerator ids = mock(IdGenerator.class);
    final OrgDirectory directory = mock(OrgDirectory.class);
    final WecomGateway wecom = mock(WecomGateway.class);
    final UserId user = TestAccounts.account(1001);
    final EmployeeId employee = TestAccounts.employee(1001);
    final HandleEmployeeCorrected handler = new HandleEmployeeCorrected(TestAccounts.everyoneHasOne(),
            new OpenAssignmentReviews(accounts, roles, adjustments, deadlines, events, ids, Clock.fixed(NOW, ZoneOffset.UTC),
                    new BusinessZone(ZoneId.of("Asia/Shanghai")), directory, wecom));
    final ListAppender<ILoggingEvent> log = new ListAppender<>();
    final Logger logger = (Logger) LoggerFactory.getLogger(OpenAssignmentReviews.class);

    @BeforeEach
    void setUp() {
        log.start();
        logger.addAppender(log);
        Role sensitive = mock(Role.class);
        when(sensitive.administrative()).thenReturn(true);
        RoleId roleId = new RoleId(UUID.randomUUID());
        when(accounts.findForUpdate(user)).thenReturn(Optional.of(UserAccount.restore(user, employee, "ENABLED", Set.of(), 1, 1)));
        when(accounts.openGrants(user)).thenReturn(List.of(new HeldGrant(new AccountRoleGrantId(UUID.randomUUID()), roleId, new RoleCode("business_admin"))));
        when(roles.findById(roleId)).thenReturn(Optional.of(sensitive));
        when(adjustments.existsForTrigger(any(), any(), any(), any())).thenReturn(false);
        when(ids.next()).thenReturn(UUID.randomUUID());
        when(deadlines.endOfWorkdayAfter(any(), eq(5), any())).thenReturn(DUE);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(log);
    }

    private EmployeeCorrected undoneSeparation(LegalEntityId legalEntity) {
        return correction(legalEntity, List.of(TriggerFieldKind.ORG_UNIT), null);
    }

    private EmployeeCorrected correction(LegalEntityId legalEntity, List<TriggerFieldKind> kinds, OrgUnitId previousOrgUnit) {
        EventEnvelope base = TestEnvelopes.of("organization.workforce.EmployeeCorrected", "Employee", employee.value());
        EventEnvelope envelope = new EventEnvelope(base.eventId(), base.eventType(), base.schemaVersion(), base.occurredAt(), base.aggregateType(),
                base.aggregateId(), base.aggregateVersion(), base.correlationId(), null, ActorType.SYSTEM, base.actorId(), legalEntity, null, null, List.of());
        return new EmployeeCorrected(envelope, new PersonnelCommandId(UUID.randomUUID()), employee, LocalDate.of(2026, 11, 2), null,
                kinds, previousOrgUnit, new OrgUnitId(UUID.randomUUID()), null, null);
    }

    private void handle(EmployeeCorrected event) {
        try (WorkContext.Scope scope = WorkContext.enter(UUID.randomUUID(), null, "platform.identity.permission-adjustment")) {
            handler.on(event);
        }
    }

    private PermissionAdjustment inserted() {
        ArgumentCaptor<PermissionAdjustment> item = ArgumentCaptor.forClass(PermissionAdjustment.class);
        verify(adjustments).insert(item.capture());
        return item.getValue();
    }

    private List<String> alerts() {
        return log.list.stream().filter(line -> line.getLevel() == Level.ERROR && line.getFormattedMessage().startsWith("ALERT P3 platform.identity "))
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("the legal entity of the event reaches the calendar port and the item stores the deadline it answers")
    void theEntityOfTheEventDecidesTheCalendar() {
        LegalEntityId entity = new LegalEntityId(UUID.randomUUID());

        handle(undoneSeparation(entity));

        verify(deadlines).endOfWorkdayAfter(LocalDate.of(2026, 11, 3), 5, entity);
        assertThat(inserted().escalationDueAt()).isEqualTo(DUE);
        assertThat(alerts()).isEmpty();
    }

    @Test
    @DisplayName("an event without a legal entity still raises the item on the default calendar and logs exactly one P3 alert")
    void noEntityStillRaisesTheItem() {
        EmployeeCorrected event = undoneSeparation(null);

        handle(event);

        verify(deadlines).endOfWorkdayAfter(LocalDate.of(2026, 11, 3), 5, null);
        assertThat(inserted().escalationDueAt()).isEqualTo(DUE);
        assertThat(alerts()).singleElement().asString().contains(event.envelope().eventId().toString());
    }

    @Test
    @DisplayName("a person without a sensitive role gets no item and no alert")
    void nothingWithoutASensitiveRole() {
        when(accounts.openGrants(user)).thenReturn(List.of());

        handle(undoneSeparation(null));

        verify(adjustments, never()).insert(any());
        assertThat(alerts()).isEmpty();
    }

    @Test
    @DisplayName("a correction that moves a holder of a sensitive role between departments raises one sensitive-role review for the department, like a transfer")
    void aMoveBetweenDepartmentsRaisesTheSensitiveReview() {
        handle(correction(new LegalEntityId(UUID.randomUUID()), List.of(TriggerFieldKind.ORG_UNIT), new OrgUnitId(UUID.randomUUID())));

        PermissionAdjustment item = inserted();
        assertThat(item.kind()).isEqualTo(PermissionAdjustmentKind.SENSITIVE_ROLE_REVIEW);
        assertThat(item.triggerField()).isEqualTo(TriggerFieldKind.ORG_UNIT);
        assertThat(item.beforeOrgUnit()).isNotNull();
    }

    @Test
    @DisplayName("a correction that changes only the position of a holder of a sensitive role raises one sensitive-role review for the position and no scope review")
    void aPositionOnlyCorrectionRaisesTheSensitiveReview() {
        OrgUnitId unit = new OrgUnitId(UUID.randomUUID());
        EventEnvelope base = TestEnvelopes.of("organization.workforce.EmployeeCorrected", "Employee", employee.value());
        EventEnvelope envelope = new EventEnvelope(base.eventId(), base.eventType(), base.schemaVersion(), base.occurredAt(), base.aggregateType(),
                base.aggregateId(), base.aggregateVersion(), base.correlationId(), null, ActorType.SYSTEM, base.actorId(), null, null, null, List.of());

        handle(new EmployeeCorrected(envelope, new PersonnelCommandId(UUID.randomUUID()), employee, LocalDate.of(2026, 11, 2), null,
                List.of(TriggerFieldKind.POSITION), unit, unit, new PositionId(UUID.randomUUID()), new PositionId(UUID.randomUUID())));

        assertThat(inserted().triggerField()).isEqualTo(TriggerFieldKind.POSITION);
    }

    @Test
    @DisplayName("a correction after which the person is not employed, or that changes no field, raises nothing")
    void noEmploymentOrNoFieldRaisesNothing() {
        EmployeeCorrected ended = correction(new LegalEntityId(UUID.randomUUID()), List.of(TriggerFieldKind.ORG_UNIT), new OrgUnitId(UUID.randomUUID()));
        handle(new EmployeeCorrected(ended.envelope(), ended.personnelCommandId(), employee, ended.affectedFrom(), null, ended.triggerFieldKinds(),
                ended.previousOrgUnitId(), null, null, null));
        handle(correction(new LegalEntityId(UUID.randomUUID()), List.of(), new OrgUnitId(UUID.randomUUID())));

        verify(adjustments, never()).insert(any());
    }

    @Test
    @DisplayName("a correction that moves a holder of a custom-department role to another department raises one scope review for the head of the new department and sends it one card")
    void aMoveGivesTheNewHeadAScopeReview() {
        Role scoped = mock(Role.class);
        when(scoped.accessScope()).thenReturn(AccessScopeKind.CUSTOM_ORG_UNITS);
        when(scoped.code()).thenReturn(new RoleCode("scoped"));
        when(scoped.permissionCodes()).thenReturn(Set.of());
        RoleId roleId = new RoleId(UUID.randomUUID());
        when(accounts.openGrants(user)).thenReturn(List.of(new HeldGrant(new AccountRoleGrantId(UUID.randomUUID()), roleId, new RoleCode("scoped"))));
        when(roles.findById(roleId)).thenReturn(Optional.of(scoped));
        EmployeeCorrected event = correction(new LegalEntityId(UUID.randomUUID()), List.of(TriggerFieldKind.ORG_UNIT), new OrgUnitId(UUID.randomUUID()));
        EmployeeId head = TestAccounts.employee(2002);
        when(directory.findOrgUnitHead(event.newOrgUnitId())).thenReturn(Optional.of(head));
        when(directory.findEmployee(head)).thenReturn(Optional.of(new EmployeeProfile(head, "Head", event.newOrgUnitId(), new WecomUserId("head-wecom"), EmploymentStatus.EMPLOYED, false)));

        handle(event);

        PermissionAdjustment item = inserted();
        assertThat(item.kind()).isEqualTo(PermissionAdjustmentKind.SCOPE_REVIEW);
        assertThat(item.recipient()).isEqualTo(head);
        ArgumentCaptor<WecomCard> card = ArgumentCaptor.forClass(WecomCard.class);
        verify(wecom).send(card.capture());
        assertThat(card.getValue().wecomUserIds()).containsExactly(new WecomUserId("head-wecom"));
        assertThat(card.getValue().subject()).isEqualTo(DeliverySubject.of(DeliveryPurpose.PERMISSION_ADJUSTMENT_ASSIGNED, item.id().value()));
    }

    @Test
    @DisplayName("a head of the new department without a WeCom user id still gets the item, no card is sent, and one P3 alert is logged")
    void aHeadWithoutAWecomIdStillGetsTheItem() {
        Role scoped = mock(Role.class);
        when(scoped.accessScope()).thenReturn(AccessScopeKind.CUSTOM_ORG_UNITS);
        when(scoped.code()).thenReturn(new RoleCode("scoped"));
        when(scoped.permissionCodes()).thenReturn(Set.of());
        RoleId roleId = new RoleId(UUID.randomUUID());
        when(accounts.openGrants(user)).thenReturn(List.of(new HeldGrant(new AccountRoleGrantId(UUID.randomUUID()), roleId, new RoleCode("scoped"))));
        when(roles.findById(roleId)).thenReturn(Optional.of(scoped));
        EmployeeCorrected event = correction(new LegalEntityId(UUID.randomUUID()), List.of(TriggerFieldKind.ORG_UNIT), new OrgUnitId(UUID.randomUUID()));
        EmployeeId head = TestAccounts.employee(2002);
        when(directory.findOrgUnitHead(event.newOrgUnitId())).thenReturn(Optional.of(head));
        when(directory.findEmployee(head)).thenReturn(Optional.of(new EmployeeProfile(head, "Head", event.newOrgUnitId(), null, EmploymentStatus.EMPLOYED, false)));

        handle(event);

        assertThat(inserted().recipient()).isEqualTo(head);
        verify(wecom, never()).send(any());
        assertThat(alerts()).singleElement().asString().contains(event.envelope().eventId().toString());
    }
}
