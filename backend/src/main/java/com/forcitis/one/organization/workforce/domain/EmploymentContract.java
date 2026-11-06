package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A labour contract as stored (spec 4.1). The row rules that need no other row are held here as the table holds them; the cross-row rules (probation tiers, once only, the
 * period overlap) are commands of WC2 and the database. A contract carries no employer: it takes the employer of its employment relationship (ADR-0086 decision 5).
 */
public record EmploymentContract(EmploymentContractId id, EmploymentRelationshipId relationshipId, EmployeeId employeeId, ContractKind kind, LocalDate contractStart,
                                 @Nullable LocalDate contractEnd, @Nullable LocalDate signedOn, int probationMonths, @Nullable EmploymentContractId renews,
                                 @Nullable ContractFileId contractFileId, ContractStatus status, @Nullable UserId submittedBy, @Nullable UserId approvedBy,
                                 @Nullable LocalDate terminatedOn, @Nullable String terminationBasisKey, long version) {
    public static final int MAX_PROBATION_MONTHS = 6;

    public EmploymentContract {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(employeeId, "employeeId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(contractStart, "contractStart");
        Objects.requireNonNull(status, "status");
        if ((kind == ContractKind.FIXED_PERIOD) != (contractEnd != null) || (contractEnd != null && contractEnd.isBefore(contractStart))) {
            throw new IllegalArgumentException("a fixed-term contract has an end on or after its start, any other kind has none");
        }
        if (probationMonths < 0 || probationMonths > MAX_PROBATION_MONTHS || (renews != null && probationMonths != 0)) {
            throw new IllegalArgumentException("probation is 0 to " + MAX_PROBATION_MONTHS + " months and a renewal has none");
        }
        if (approvedBy != null && approvedBy.equals(submittedBy)) {
            throw new IllegalArgumentException("the approver of a contract is not its submitter");
        }
    }
}
