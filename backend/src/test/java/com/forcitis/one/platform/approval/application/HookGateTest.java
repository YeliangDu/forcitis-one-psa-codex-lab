package com.forcitis.one.platform.approval.application;

import com.forcitis.one.cost.timesheet.api.TimesheetApprovalProcess;
import com.forcitis.one.fixture.doubles.TestAccounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentTypeCode;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.UserId;
import java.util.Optional;
import com.forcitis.one.platform.approval.api.ApprovalHook;
import com.forcitis.one.platform.approval.api.ApprovalInstanceId;
import com.forcitis.one.platform.approval.api.ApprovalError;
import com.forcitis.one.platform.approval.api.ApprovalTaskId;
import com.forcitis.one.platform.approval.api.ContentHash;
import com.forcitis.one.platform.approval.api.HookAction;
import com.forcitis.one.platform.approval.api.HookContext;
import com.forcitis.one.platform.approval.api.HookDecision;
import com.forcitis.one.platform.approval.domain.TaskView;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class HookGateTest {

    static ApprovalHook hook(String key, HookDecision decision) {
        return new ApprovalHook() {
            @Override
            public String hookKey() {
                return key;
            }

            @Override
            public HookDecision before(HookContext context) {
                return decision;
            }
        };
    }

    static HookGate gate(ApprovalHook... hooks) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        for (int i = 0; i < hooks.length; i++) {
            factory.registerSingleton("hook" + i, hooks[i]);
        }
        return new HookGate(factory.getBeanProvider(ApprovalHook.class));
    }

    static TaskView task(String hookKey) {
        return new TaskView(com.forcitis.one.TestApprovalIds.task("t"), com.forcitis.one.TestApprovalIds.instance("i"), TimesheetApprovalProcess.KEY,
                new DocumentReference(new DocumentTypeCode("TIMESHEET"), com.forcitis.one.TestApprovalIds.document("doc")), new LegalEntityId(TestIds.uuid(1)), "node", "Node", TestAccounts.employee(1),
                Optional.empty(), Set.of(TestAccounts.account(2)), Instant.EPOCH, false, Optional.ofNullable(hookKey), false, new ContentHash("hash"));
    }

    @Test
    @DisplayName("RULE-283 two hooks registering the same key stop the application at startup")
    void duplicateKeysStopStartup() {
        assertThatThrownBy(() -> gate(hook("K", HookDecision.allow()), hook("K", HookDecision.allow())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("K");
    }

    @Test
    @DisplayName("RULE-283 a node without a hook key has no hook; a node naming an unknown key is blocked")
    void unknownKeyBlocksAndNoKeyPasses() {
        HookGate gate = gate(hook("K", HookDecision.allow()));
        assertThat(gate.check(task(null), TestAccounts.account(2), HookAction.APPROVE, null).outcome().commits()).isTrue();
        assertThat(gate.check(task(" "), TestAccounts.account(2), HookAction.APPROVE, null).outcome().commits()).isTrue();
        assertThatThrownBy(() -> gate.check(task("OTHER"), TestAccounts.account(2), HookAction.APPROVE, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_NOT_REGISTERED));
    }

    @Test
    @DisplayName("RULE-277 a hook that returns nothing blocks (a task action with no decision is not committed)")
    void nullDecisionBlocks() {
        HookGate gate = gate(hook("K", null));
        assertThatThrownBy(() -> gate.check(task("K"), TestAccounts.account(2), HookAction.APPROVE, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_BLOCKED));
    }
}
