package com.forcitis.one.organization.payroll.application;

import com.forcitis.one.BusinessDay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.Money;
import com.forcitis.one.kernel.OrgUnitAccess;
import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.payroll.api.RequestBonusInput;
import com.forcitis.one.organization.payroll.api.VoidBonusInput;
import com.forcitis.one.organization.payroll.domain.BonusDeterminationRepository;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationships;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.audit.api.AuditLog;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** #1436: the writes of payroll data are fail-closed on scope like the reads; a grant narrower than all organisations does nothing. */
class BonusInputsServiceScopeTest {

    private final BonusDeterminationRepository inputs = mock(BonusDeterminationRepository.class);
    private final ApprovalProcesses approvals = mock(ApprovalProcesses.class);
    private final EmploymentRelationships relationships = mock(EmploymentRelationships.class);
    private final OrgUnitAccess orgUnits = mock(OrgUnitAccess.class);
    private final BonusInputsService service = new BonusInputsService(inputs, approvals, relationships, mock(AuditLog.class), null, BusinessDay.fixedClock(), orgUnits,
            mock(PayrollApprovers.class));

    private void scopeIsNarrow() {
        when(orgUnits.visibleOrgUnits(any())).thenReturn(new OrgUnitAccess.Visible(false, Set.of(UUID.randomUUID()), Set.of()));
    }

    @Test
    @DisplayName("#1436 a void with a grant narrower than all organisations answers not found and touches nothing")
    void voidWithNarrowScopeIsNotFound() {
        scopeIsNarrow();
        assertThatThrownBy(() -> service.voidInput(new VoidBonusInput(new BonusInputId(UUID.randomUUID()))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.kind()).isEqualTo(ErrorKind.NOT_FOUND));
        verifyNoInteractions(inputs);
    }

    @Test
    @DisplayName("#1436 a request with a grant narrower than all organisations is refused and starts no approval")
    void requestWithNarrowScopeIsRefused() {
        scopeIsNarrow();
        RequestBonusInput command = new RequestBonusInput(new EmploymentRelationshipId(UUID.randomUUID()), new LegalEntityId(UUID.randomUUID()), LocalDate.of(2026, 10, 1),
                Money.of("100.00", "CNY"), "YEAR_END", UUID.randomUUID());
        assertThatThrownBy(() -> service.request(command)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(approvals, relationships);
    }
}
