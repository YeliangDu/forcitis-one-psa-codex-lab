package com.forcitis.one.organization.payroll.domain;

import com.forcitis.one.organization.payroll.api.BonusInputId;
import com.forcitis.one.organization.workforce.api.EmploymentRelationshipId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BonusInputRepository {

    /** Writes a new ACTIVE row; false when the database already holds a row of this approval or an ACTIVE one of the same relationship, month and reason. */
    boolean insertApproved(BonusInput input);

    boolean existsForApproval(ApprovalInstanceId approval);

    Optional<BonusInput> find(BonusInputId id);

    /** Reads the row and locks it until the transaction ends. */
    Optional<BonusInput> findForUpdate(BonusInputId id);

    /** Writes the void of a row that was read. */
    void saveVoided(BonusInput input);

    /** Months fromMonth to toMonth, both included; newest month first, then by id. */
    List<BonusInput> listForRelationship(EmploymentRelationshipId relationship, LocalDate fromMonth, LocalDate toMonth);
}
