package com.forcitis.one.platform.approval.application;

import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.platform.approval.api.ApprovalProgress;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.api.RoundNumber;
import com.forcitis.one.platform.approval.domain.ApprovalRequestRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetApprovalProgress {

    private final ApprovalRequestRepository requests;

    GetApprovalProgress(ApprovalRequestRepository requests) {
        this.requests = requests;
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalProgress> execute(ProcessKey processKey, DocumentReference document) {
        return requests.find(processKey, document).map(request -> new ApprovalProgress(new RoundNumber(request.round()), request.status()));
    }
}
