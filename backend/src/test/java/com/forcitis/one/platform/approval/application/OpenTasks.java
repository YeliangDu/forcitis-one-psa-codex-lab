package com.forcitis.one.platform.approval.application;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.EmploymentStatus;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.ProcessKey;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.mockito.Mockito;

/** A claimed task of one approval, for the tests that set the initiator (an employee) against accounts. */
final class OpenTasks {
    static final ApprovalTaskId TASK = new ApprovalTaskId("t-1");
    static final ApprovalInstanceId INSTANCE = new ApprovalInstanceId("i-1");

    private OpenTasks() {
    }

    static TaskView claimedBy(EmployeeId initiator, UserId handler) {
        return new TaskView(TASK, INSTANCE, ProcessKey.COST_TIMESHEET_APPROVAL, new DocumentReference(DocumentType.TIMESHEET, new DocumentId("7")),
                new LegalEntityId(TestIds.uuid(1)), "approverReview", "Approver review", initiator, Optional.of(handler), Set.of(handler),
                Instant.EPOCH, false, Optional.empty(), false, new ContentHash("hash"));
    }

    static OrgDirectory employed(long... employees) {
        OrgDirectory directory = Mockito.mock(OrgDirectory.class);
        for (long employee : employees) {
            Mockito.when(directory.findEmployee(new EmployeeId(employee))).thenReturn(Optional.of(new EmployeeProfile(new EmployeeId(employee),
                    "person-" + employee, new OrgUnitId(5), null, EmploymentStatus.EMPLOYED, false)));
        }
        return directory;
    }
}
