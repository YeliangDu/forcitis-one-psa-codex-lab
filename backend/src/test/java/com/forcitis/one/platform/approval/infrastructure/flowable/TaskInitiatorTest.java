package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.forcitis.one.kernel.EmployeeId;
import java.util.Map;
import java.util.UUID;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TaskInitiatorTest {

    private static Task task(String id, String instanceId) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getProcessInstanceId()).thenReturn(instanceId);
        return task;
    }

    @Test
    @DisplayName("P3B-L2 复核 Low-5 待办的发起人变量缺失或不是员工 ID：写一条 ALERT 并抛出原异常，不当作无人或默认值")
    void anInvalidInitiatorIsAlertedAndThrown() {
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(FlowableApprovalEngine.class);
        logger.addAppender(logged);
        try {
            assertThatThrownBy(() -> FlowableApprovalEngine.initiatorOfTask(
                    EngineVariables.fromEngine(Map.of(FlowableApprovalEngine.VARIABLE_INITIATOR, "42")), task("task-1", "instance-1")))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FlowableApprovalEngine.initiatorOfTask(EngineVariables.fromEngine(Map.of()), task("task-2", "instance-2")))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("has no initiator");

            assertThat(logged.list).hasSize(2).allSatisfy(event -> assertThat(event.getFormattedMessage()).startsWith("ALERT platform.approval"));
            assertThat(logged.list.get(0).getFormattedMessage()).contains("task-1").contains("instance-1");
            assertThat(logged.list.get(1).getFormattedMessage()).contains("task-2").contains("instance-2");
        } finally {
            logger.detachAppender(logged);
        }
    }

    @Test
    @DisplayName("P3B-L2 对照：发起人变量是员工 ID 时照常返回，不写 ALERT")
    void aValidInitiatorIsReturned() {
        UUID initiator = UUID.randomUUID();
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        Logger logger = (Logger) LoggerFactory.getLogger(FlowableApprovalEngine.class);
        logger.addAppender(logged);
        try {
            EmployeeId found = FlowableApprovalEngine.initiatorOfTask(
                    EngineVariables.fromEngine(Map.of(FlowableApprovalEngine.VARIABLE_INITIATOR, initiator.toString())), task("task-3", "instance-3"));

            assertThat(found).isEqualTo(new EmployeeId(initiator));
            assertThat(logged.list).isEmpty();
        } finally {
            logger.detachAppender(logged);
        }
    }
}
