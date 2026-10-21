package com.forcitis.one.cost.timesheet.api;

import com.forcitis.one.kernel.DocumentTypeCode;

/** The document type this context declares (ADR-0066 decision 4); the numbering registry holds the matching row. */
public interface TimesheetDocument {

    DocumentTypeCode TYPE = new DocumentTypeCode("TIMESHEET");
}
