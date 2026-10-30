package com.forcitis.one.platform.numbering.application;

import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.platform.numbering.api.DocumentNumbers;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class IssueDocumentNumber implements DocumentNumbers {

    private final NumberIssuance issuance;

    IssueDocumentNumber(NumberIssuance issuance) {
        this.issuance = issuance;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedNumber next(DocumentTypeCode type, LegalEntityId legalEntity, LocalDate businessDate) {
        return issuance.issue(type, legalEntity, businessDate);
    }
}
