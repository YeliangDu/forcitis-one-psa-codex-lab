package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.WithdrawCheck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Takes no lock, so looking at a document never waits for an approver who is deciding it. */
@Service
public class CheckWithdraw {

    private final Retraction retraction;

    CheckWithdraw(Retraction retraction) {
        this.retraction = retraction;
    }

    @Transactional(readOnly = true)
    public WithdrawCheck execute(ProcessKey processKey, DocumentReference document, UserId actor) {
        return retraction.check(processKey, document, actor);
    }
}
