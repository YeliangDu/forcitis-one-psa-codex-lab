package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RetractionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The initiator ends the whole approval; nothing is deleted, so former approvers still see the document and its record (RULE-288). */
@Service
public class CancelApproval {

    private final Retraction retraction;

    CancelApproval(Retraction retraction) {
        this.retraction = retraction;
    }

    @Transactional
    public RetractionResult execute(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        return retraction.cancel(processKey, document, actor, reason);
    }
}
