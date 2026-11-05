package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Asks for the approval of one bonus input. idempotencyKey makes a client retry the same request: payroll derives its request id from it, so the approval
 * is started once. Nothing is written to the bonus table until the approval completes. The initiator is the caller, never a field.
 */
public record RequestBonusInput(EmploymentRelationshipId employmentRelationshipId, LegalEntityId payingLegalEntityId, LocalDate payrollMonthStartDate,
                                BigDecimal bonusAmount, String bonusCurrency, String reasonKey, UUID idempotencyKey) {

    public RequestBonusInput {
        Objects.requireNonNull(employmentRelationshipId, "employmentRelationshipId");
        Objects.requireNonNull(payingLegalEntityId, "payingLegalEntityId");
        Objects.requireNonNull(payrollMonthStartDate, "payrollMonthStartDate");
        Objects.requireNonNull(bonusAmount, "bonusAmount");
        Objects.requireNonNull(bonusCurrency, "bonusCurrency");
        Objects.requireNonNull(reasonKey, "reasonKey");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }
}
