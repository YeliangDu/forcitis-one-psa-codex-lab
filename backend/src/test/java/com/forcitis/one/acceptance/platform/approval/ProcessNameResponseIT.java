package com.forcitis.one.acceptance.platform.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.ApprovalP4Support;
import com.forcitis.one.kernel.DocumentReference;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * IMP-1023-NX5-01 完成标准第 1 条（「前端不再自带流程显示名表」）、卡目标 ①（#644 opus 首轮 #6：显示名由响应带 processName）、
 * REG-4 裁定第 3 项第 3 条（流程显示名是流程定义数据，经引擎端口读取）：收件箱、任务详情、已办记录三个响应各带 processName，
 * 值是流程定义的名字，前端不必再按流程键查表。
 */
class ProcessNameResponseIT extends ApprovalP4Support {

    static final String TIMESHEET_PROCESS_NAME = "工时审批";

    static final TestDatabase postgres = TestPostgres.database(ProcessNameResponseIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 目标①：收件箱每条都带 processName，值是流程定义的名字")
    void inboxItemsCarryTheProcessName() throws Exception {
        startProjectHours(projectManager);

        MockHttpServletResponse inbox = mvc.perform(get(TASKS.substring(0, TASKS.length() - 1)).with(session(projectManagerAccount))).andReturn().getResponse();

        assertThat(inbox.getStatus()).as(inbox.getContentAsString()).isEqualTo(200);
        assertThat((List<?>) JsonPath.read(inbox.getContentAsString(), "$.items")).as("收件箱里有这条任务").isNotEmpty();
        List<String> names = JsonPath.read(inbox.getContentAsString(), "$.items[*].processName");
        assertThat(names).isNotEmpty().allMatch(TIMESHEET_PROCESS_NAME::equals);
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 目标①：任务详情带 processName")
    void taskDetailCarriesTheProcessName() throws Exception {
        UUID task = openTaskIds(startProjectHours(projectManager)).getFirst();

        MockHttpServletResponse seen = detail(task, projectManagerAccount);

        assertThat(seen.getStatus()).as(seen.getContentAsString()).isEqualTo(200);
        assertThat((String) JsonPath.read(seen.getContentAsString(), "$.processName")).isEqualTo(TIMESHEET_PROCESS_NAME);
    }

    @Test
    @DisplayName("IMP-1023-NX5-01 目标①：已办记录带 processName（前端 recordTitle 原先也查表）")
    void handledRecordsCarryTheProcessName() throws Exception {
        DocumentReference document = startProjectHours(projectManager);
        actOk(openTaskIds(document).getFirst(), projectManagerAccount, "decide", "{\"outcome\":\"APPROVE\",\"reason\":\"synthetic\"}");

        MockHttpServletResponse records = mvc.perform(get("/api/v1/platform/approval-task-records").with(session(projectManagerAccount))).andReturn().getResponse();

        assertThat(records.getStatus()).as(records.getContentAsString()).isEqualTo(200);
        assertThat((List<?>) JsonPath.read(records.getContentAsString(), "$.items")).as("已办记录里有这条决定").isNotEmpty();
        List<String> names = JsonPath.read(records.getContentAsString(), "$.items[*].processName");
        assertThat(names).isNotEmpty().allMatch(TIMESHEET_PROCESS_NAME::equals);
    }
}
