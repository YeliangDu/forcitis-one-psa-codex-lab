package com.forcitis.one.organization.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.P5Support;
import com.forcitis.one.kernel.DocumentReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * #1436 (a), the implementer's own test: the approver read answers only for tasks of the bonus input process. A candidate of an open task of another process (a
 * timesheet's project manager) who asks for that task's id is told the same as for an unknown task; the data of another approval is not read through this path.
 */
class BonusInputApprovalOtherProcessIT extends P5Support {

    static final TestDatabase postgres = TestPostgres.database(BonusInputApprovalOtherProcessIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("#1436 (a) 奖金输入审批人读取只认奖金输入流程的任务：别的流程（工时）任务的候选人拿该任务 id 来读，得到与未知任务相同的 404")
    void aCandidateOfATaskOfAnotherProcessGetsNotFound() throws Exception {
        UUID manager = newEmployeeId();
        employee(manager, unit, null);
        SignedIn managerAccount = signIn(manager);
        DocumentReference document = startProjectHours(manager);
        List<UUID> open = openTaskIds(document);
        assertThat(open).as("the project manager holds an open task of the timesheet process").hasSize(1);

        MockHttpServletResponse response = mvc.perform(get("/api/v1/organization/bonus-input-approvals/" + open.getFirst()).with(session(managerAccount))).andReturn()
                .getResponse();

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(404);
    }
}
