package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.ExpectedVersion;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.event.DomainEvents;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.api.PersonnelCommandId;
import com.forcitis.one.platform.identity.api.PermissionAdjustmentId;
import com.forcitis.one.platform.identity.domain.PermissionAdjustment;
import com.forcitis.one.platform.identity.domain.PermissionAdjustmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpholdPermissionAdjustmentTest {

    private static final Instant AT = Instant.parse("2026-11-02T08:00:00Z");

    private final PermissionAdjustmentRepository adjustments = mock(PermissionAdjustmentRepository.class);
    private final PermissionAdjustmentAccess access = mock(PermissionAdjustmentAccess.class);
    private final UpholdPermissionAdjustment uphold = new UpholdPermissionAdjustment(adjustments, access, mock(CurrentUser.class),
            mock(AccountEmployees.class), mock(OrgDirectory.class), mock(DomainEvents.class), Clock.fixed(AT, ZoneOffset.UTC),
            new BusinessZone(ZoneId.of("Asia/Shanghai")));

    @Test
    @DisplayName("#730 S3: a scope review upheld with a business owner is a 400 and changes nothing")
    void aScopeReviewTakesNoBusinessOwner() {
        PermissionAdjustment scopeReview = PermissionAdjustment.departmentChangeScopeReview(new PermissionAdjustmentId(UUID.randomUUID()),
                TestAccounts.account(1), new PersonnelCommandId(UUID.randomUUID()), new OrgUnitId(UUID.randomUUID()), new OrgUnitId(UUID.randomUUID()), null, null,
                null, AT.plusSeconds(86_400), AT);
        when(access.handleable(any(), any())).thenReturn(scopeReview);

        assertThatThrownBy(() -> uphold.execute(scopeReview.id(), ExpectedVersion.of(scopeReview.version()), "still needed", UUID.randomUUID()))
                .isInstanceOf(InvalidRequestException.class);

        verify(adjustments, never()).update(any());
    }
}
