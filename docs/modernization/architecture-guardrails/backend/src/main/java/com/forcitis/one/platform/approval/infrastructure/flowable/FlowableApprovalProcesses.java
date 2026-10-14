package com.forcitis.one.platform.approval.infrastructure.flowable;

import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalProcesses;
import com.forcitis.one.platform.approval.api.ApprovalRequest;
import com.forcitis.one.kernel.UserId;
import java.util.HashMap;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

/** The only place that touches the Flowable engine. Typed api in, Flowable variables out. */
@Component
class FlowableApprovalProcesses implements ApprovalProcesses {

    private final RuntimeService runtime;
    private final TaskService tasks;

    FlowableApprovalProcesses(RuntimeService runtime, TaskService tasks) {
        this.runtime = runtime;
        this.tasks = tasks;
    }

    @Override
    public ApprovalInstanceId start(ApprovalRequest request) {
        Map<String, Object> variables = new HashMap<>();
        request.routingFlags().forEach(flag -> variables.put(flag, Boolean.TRUE));
        variables.put("initiatorId", String.valueOf(request.initiator().value()));
        return new ApprovalInstanceId(runtime.startProcessInstanceByKey(request.processKey().key(), request.businessKey(), variables).getId());
    }

    @Override
    public void approve(String taskId, UserId approver) {
        tasks.setAssignee(taskId, String.valueOf(approver.value()));
        tasks.complete(taskId, Map.of("approved", Boolean.TRUE));
    }

    @Override
    public boolean isFinished(ApprovalInstanceId instance) {
        return runtime.createProcessInstanceQuery().processInstanceId(instance.value()).count() == 0;
    }
}
