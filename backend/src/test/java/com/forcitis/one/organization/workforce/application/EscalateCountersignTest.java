package com.forcitis.one.organization.workforce.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.RoleHolderAccounts;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.api.CountersignRequestKind;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("#896 the escalation of one countersign request on one day")
class EscalateCountersignTest {

    private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");
    private static final LocalDate DUE = LocalDate.of(2026, 11, 5);
    private static final LocalDate DAY_AFTER_DUE = DUE.plusDays(1);

    private final AttributionCountersignRepository countersigns = mock(AttributionCountersignRepository.class);
    private final CountersignDueDates dueDates = mock(CountersignDueDates.class);
    private final RoleHolderAccounts roles = mock(RoleHolderAccounts.class);
    private final AccountEmployees accountEmployees = mock(AccountEmployees.class);
    private final OrgDirectory directory = mock(OrgDirectory.class);
    private final WecomGateway wecom = mock(WecomGateway.class);
    private final CountersignEscalationNotice notice = new CountersignEscalationNotice(countersigns, roles, accountEmployees, directory, wecom);
    private final EscalateCountersign escalate = new EscalateCountersign(countersigns, dueDates, notice, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UserId drafter = new UserId(UUID.randomUUID());
    private final UserId requester = new UserId(UUID.randomUUID());
    private final LegalEntityId entity = new LegalEntityId(UUID.randomUUID());

    private AttributionCountersign row(int level) {
        return AttributionCountersign.restore(new AttributionCountersignId(UUID.randomUUID()), null, new OrgStructureCommandId(UUID.randomUUID()), null, drafter,
                CountersignRequestKind.CHANGE, 1, entity, NOW, requester, DUE, level, null, 0, null, null, null, null);
    }

    private void headWithWecom(String wecomUser) {
        EmployeeId head = new EmployeeId(UUID.randomUUID());
        when(countersigns.financeHeadOn(entity, DAY_AFTER_DUE)).thenReturn(Optional.of(head));
        wecomOf(head, wecomUser);
    }

    private void wecomOf(EmployeeId person, String wecomUser) {
        EmployeeProfile profile = mock(EmployeeProfile.class);
        when(profile.wecomUserId()).thenReturn(new WecomUserId(wecomUser));
        when(directory.findEmployee(person)).thenReturn(Optional.of(profile));
    }

    @Test
    @DisplayName("a second run on the same day at the reached level saves nothing and sends nothing")
    void aRepeatedRunIsSilent() {
        AttributionCountersign reached = row(1);
        when(countersigns.find(reached.id())).thenReturn(Optional.of(reached));
        when(dueDates.workingDaysAfter(DUE, 2, entity)).thenReturn(DUE.plusDays(4));

        escalate.execute(reached.id(), DAY_AFTER_DUE);

        verify(countersigns, never()).saveEscalation(any(), any(), anyLong());
        verifyNoInteractions(wecom, directory);
        assertThat(reached.escalationLevel()).isEqualTo(1);
    }

    @Test
    @DisplayName("the first run on the day after the due date raises level 1 and tells today's finance head")
    void theFirstRunNotifies() {
        AttributionCountersign open = row(0);
        when(countersigns.find(open.id())).thenReturn(Optional.of(open));
        when(dueDates.workingDaysAfter(DUE, 2, entity)).thenReturn(DUE.plusDays(4));
        headWithWecom("wx-head");

        escalate.execute(open.id(), DAY_AFTER_DUE);

        assertThat(open.escalationLevel()).isEqualTo(1);
        verify(countersigns).saveEscalation(open, NOW, 0);
        ArgumentCaptor<WecomCard> card = ArgumentCaptor.forClass(WecomCard.class);
        verify(wecom).send(card.capture());
        assertThat(card.getValue().wecomUserIds()).containsExactly(new WecomUserId("wx-head"));
    }

    @Test
    @DisplayName("the level 2 notice reaches the business administrators but neither the drafter nor the requester")
    void levelTwoLeavesOutDrafterAndRequester() {
        AttributionCountersign open = row(1);
        UserId admin = new UserId(UUID.randomUUID());
        EmployeeId adminPerson = new EmployeeId(UUID.randomUUID());
        EmployeeId drafterPerson = new EmployeeId(UUID.randomUUID());
        EmployeeId requesterPerson = new EmployeeId(UUID.randomUUID());
        when(roles.enabledHolders(CountersignDeciders.ESCALATED_ROLE)).thenReturn(Set.of(admin, drafter, requester));
        when(accountEmployees.employeeOf(admin)).thenReturn(Optional.of(adminPerson));
        when(accountEmployees.employeeOf(drafter)).thenReturn(Optional.of(drafterPerson));
        when(accountEmployees.employeeOf(requester)).thenReturn(Optional.of(requesterPerson));
        wecomOf(adminPerson, "wx-admin");
        wecomOf(drafterPerson, "wx-drafter");
        wecomOf(requesterPerson, "wx-requester");

        notice.send(open, 2, DUE.plusDays(3));

        ArgumentCaptor<WecomCard> card = ArgumentCaptor.forClass(WecomCard.class);
        verify(wecom).send(card.capture());
        assertThat(card.getValue().wecomUserIds()).containsExactly(new WecomUserId("wx-admin"));
    }
}
