package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.ExternalSubjectEmployees;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.domain.OrgSnapshotQueries;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The snapshot's answer to the read-only lookup of the operations; reads only, never asks the organisation source. */
@Component
class ExternalSubjectLookup implements ExternalSubjectEmployees {

    private final OrgSnapshotQueries snapshot;

    ExternalSubjectLookup(OrgSnapshotQueries snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeId> withMessagingAccountId(String messagingAccountId) {
        return snapshot.findEmployeeIdsByWecomUser(new WecomUserId(messagingAccountId));
    }
}
