package com.forcitis.one.organization.recruitment.api;

import com.forcitis.one.platform.approval.api.ProcessKey;

/** The approval process of a requisition: the id of processes/organization_recruitment_requisition_approval.bpmn20.xml. */
public interface RequisitionApprovalProcess {

    ProcessKey KEY = ProcessKey.of("organization_recruitment_requisition_approval");

    /** The form field the process reads the department head from: the requisition's org unit id. */
    String ORG_UNIT_FIELD = "orgUnitId";
}
