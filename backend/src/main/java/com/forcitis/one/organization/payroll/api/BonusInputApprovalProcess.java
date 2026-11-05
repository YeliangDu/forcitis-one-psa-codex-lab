package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.approval.api.ProcessKey;

/** The approval process of a bonus input (G47: {subdomain}_{bc}_{name}); its file is processes/organization_payroll_bonus_input.bpmn20.xml. */
public interface BonusInputApprovalProcess {

    ProcessKey KEY = ProcessKey.of("organization_payroll_bonus_input");
    DocumentTypeCode DOCUMENT_TYPE = new DocumentTypeCode("BONUS_INPUT");

    /** The form fields the process declares; the approval keeps them as submitted and payroll reads them back when the approval completes. */
    String EMPLOYMENT_RELATIONSHIP_ID = "employmentRelationshipId";
    String PAYROLL_MONTH_START_DATE = "payrollMonthStartDate";
    String BONUS_AMOUNT = "bonusAmount";
    String BONUS_CURRENCY = "bonusCurrency";
    String REASON_KEY = "reasonKey";
}
