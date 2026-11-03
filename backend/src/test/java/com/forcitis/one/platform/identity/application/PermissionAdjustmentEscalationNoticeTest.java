package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.Availability;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.Role;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("#1151 the escalation notice: recipients without a WeCom user id are left out, the others are told")
class PermissionAdjustmentEscalationNoticeTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountEmployees accountEmployees = mock(AccountEmployees.class);
    private final OrgDirectory directory = mock(OrgDirectory.class);
    private final WecomGateway wecom = mock(WecomGateway.class);
    private final PermissionAdjustmentEscalationNotice notice = new PermissionAdjustmentEscalationNotice(accounts, accountEmployees, directory, wecom,
            Clock.fixed(Instant.parse("2026-11-03T08:00:00Z"), ZoneOffset.UTC), new BusinessZone(ZoneOffset.UTC));

    private UserId holder(String wecom) {
        UserId account = new UserId(UUID.randomUUID());
        EmployeeId employee = new EmployeeId(UUID.randomUUID());
        when(accountEmployees.employeeOf(account)).thenReturn(Optional.of(employee));
        EmployeeProfile profile = mock(EmployeeProfile.class);
        when(profile.employmentStatus()).thenReturn(EmploymentStatus.EMPLOYED);
        when(profile.wecomUserId()).thenReturn(wecom == null ? null : new WecomUserId(wecom));
        when(directory.findEmployee(employee)).thenReturn(Optional.of(profile));
        when(directory.availabilityOn(any(), any())).thenReturn(new Availability(false, false, false));
        return account;
    }

    private PermissionAdjustment item() {
        PermissionAdjustment item = mock(PermissionAdjustment.class);
        when(item.id()).thenReturn(new PermissionAdjustmentId(UUID.randomUUID()));
        return item;
    }

    @Test
    @DisplayName("#1151 mixed ids: the card goes to the holders who have a WeCom user id, none is sent to one without")
    void aHolderWithoutAWecomIdIsLeftOutAndTheOthersAreTold() {
        Set<UserId> holders = Set.of(holder("a"), holder(null), holder("b"));
        when(accounts.findEnabledHolders(Role.SUPER_ADMIN)).thenReturn(holders);

        notice.send(item());

        ArgumentCaptor<WecomCard> card = ArgumentCaptor.forClass(WecomCard.class);
        verify(wecom).send(card.capture());
        assertThat(card.getValue().wecomUserIds()).extracting(WecomUserId::value).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    @DisplayName("#1151 nobody has a WeCom user id: no card and no exception")
    void nobodyToTellSendsNothing() {
        Set<UserId> holders = Set.of(holder(null));
        when(accounts.findEnabledHolders(Role.SUPER_ADMIN)).thenReturn(holders);

        notice.send(item());

        verify(wecom, never()).send(any());
    }
}
