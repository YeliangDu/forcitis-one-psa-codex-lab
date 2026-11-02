package com.forcitis.one.kernel;

import java.time.LocalDate;

/**
 * The next number of a document type whose template does not depend on a legal entity (an employee code); asked by organization.workforce, implemented by
 * platform.numbering, which itself depends on organization.workforce (legal entities), so a module edge from workforce to numbering would close a cycle.
 * Runs in the caller's transaction like platform.numbering.api.GlobalDocumentNumbers, of which it is the kernel face.
 */
public interface GlobalDocumentNumberSource {

    /** @throws DomainException with the numbering module's error code when the rule has no confirmed template or its template names a legal entity */
    String next(DocumentTypeCode type, LocalDate businessDate);
}
