package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BonusDeterminationRepository {

    /** Writes a new ACTIVE row; false when the database already holds a row of this approval or an ACTIVE one of the same relationship, month and reason. */
    boolean insertApproved(BonusDetermination input);

    /** An ACTIVE row of the relationship, month and reason exists. */
    boolean existsActive(EmploymentRelationshipId relationship, LocalDate month, String reasonKey);

    boolean existsForApproval(ApprovalInstanceId approval);

    Optional<BonusDetermination> find(BonusInputId id);

    /** Reads the row and locks it until the transaction ends. */
    Optional<BonusDetermination> findForUpdate(BonusInputId id);

    /** Writes the void of a row that was read. */
    void saveVoided(BonusDetermination input);

    /** Months fromMonth to toMonth, both included; newest month first, then by id. */
    List<BonusDetermination> listForRelationship(EmploymentRelationshipId relationship, LocalDate fromMonth, LocalDate toMonth);
}
