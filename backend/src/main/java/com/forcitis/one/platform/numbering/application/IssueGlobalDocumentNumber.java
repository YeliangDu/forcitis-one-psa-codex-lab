package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.platform.numbering.api.GlobalDocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class IssueGlobalDocumentNumber implements GlobalDocumentNumbers {

    private final NumberIssuance issuance;

    IssueGlobalDocumentNumber(NumberIssuance issuance) {
        this.issuance = issuance;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedNumber nextGlobal(DocumentTypeCode type, LocalDate businessDate) {
        return issuance.issue(type, null, businessDate);
    }
}
