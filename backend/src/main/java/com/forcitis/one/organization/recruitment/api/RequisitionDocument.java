package com.forcitis.one.organization.recruitment.api;

import com.forcitis.one.kernel.DocumentTypeCode;

/** The document type this context declares; the numbering registry holds the matching row (R1 migration). */
public interface RequisitionDocument {

    DocumentTypeCode TYPE = new DocumentTypeCode("RECRUITMENT_REQUISITION");
}
