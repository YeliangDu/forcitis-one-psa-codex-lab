package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.GlobalDocumentNumberSource;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The kernel face of {@code GlobalDocumentNumbers.nextGlobal}, for modules that platform.numbering itself depends on (organization.workforce). */
@Service
class IssueNumberForKernelPort implements GlobalDocumentNumberSource {

    private final NumberIssuance issuance;

    IssueNumberForKernelPort(NumberIssuance issuance) {
        this.issuance = issuance;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(DocumentTypeCode type, LocalDate businessDate) {
        return issuance.issue(type, null, businessDate).value();
    }
}
