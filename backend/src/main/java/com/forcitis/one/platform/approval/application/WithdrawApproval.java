package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.api.ProcessKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The initiator pulls the running round back to edit it; the round ends as RETURNED and a new one may be started. */
@Service
public class WithdrawApproval {

    private final Retraction retraction;

    WithdrawApproval(Retraction retraction) {
        this.retraction = retraction;
    }

    @Transactional
    public void execute(ProcessKey processKey, DocumentReference document, UserId actor, String reason) {
        retraction.withdraw(processKey, document, actor, reason);
    }
}
