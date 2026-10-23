package com.forcitis.one.platform.approval.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
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
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.mockito.Mockito;
import com.forcitis.one.fixture.doubles.TestOrgIds;

/** A claimed task of one approval, for the tests that set the initiator (an employee) against accounts. */
final class OpenTasks {
    static final ApprovalTaskId TASK = com.forcitis.one.TestApprovalIds.task("t-1");
    static final ApprovalInstanceId INSTANCE = com.forcitis.one.TestApprovalIds.instance("i-1");

    private OpenTasks() {
    }

    static TaskView claimedBy(EmployeeId initiator, UserId handler) {
        return new TaskView(TASK, INSTANCE, TimesheetApprovalProcess.KEY, new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("7")),
                new LegalEntityId(TestIds.uuid(1)), "approverReview", "Approver review", initiator, Optional.of(handler), Set.of(handler),
                Instant.EPOCH, false, Optional.empty(), false, new ContentHash("hash"));
    }

    static OrgDirectory employed(long... employees) {
        OrgDirectory directory = Mockito.mock(OrgDirectory.class);
        for (long employee : employees) {
            Mockito.when(directory.findEmployee(TestAccounts.employee(employee))).thenReturn(Optional.of(new EmployeeProfile(TestAccounts.employee(employee),
                    "person-" + employee, TestOrgIds.orgUnit(5), null, EmploymentStatus.EMPLOYED, false)));
        }
        return directory;
    }
}
