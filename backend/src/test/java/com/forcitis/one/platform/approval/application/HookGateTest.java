package com.forcitis.one.platform.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.kernel.DocumentId;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.DocumentReference;
import com.forcitis.one.kernel.DocumentType;
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
import com.forcitis.one.platform.approval.api.ProcessKey;
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
        return new TaskView(new ApprovalTaskId("t"), new ApprovalInstanceId("i"), ProcessKey.COST_TIMESHEET_APPROVAL,
                new DocumentReference(DocumentType.TIMESHEET, new DocumentId("doc")), new LegalEntityId(TestIds.uuid(1)), "node", "Node", new UserId(1),
                Optional.empty(), Set.of(new UserId(2)), Instant.EPOCH, false, Optional.ofNullable(hookKey), false, new ContentHash("hash"));
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
        assertThat(gate.check(task(null), new UserId(2), HookAction.APPROVE, null).outcome().commits()).isTrue();
        assertThat(gate.check(task(" "), new UserId(2), HookAction.APPROVE, null).outcome().commits()).isTrue();
        assertThatThrownBy(() -> gate.check(task("OTHER"), new UserId(2), HookAction.APPROVE, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_NOT_REGISTERED));
    }

    @Test
    @DisplayName("RULE-277 a hook that returns nothing blocks (a task action with no decision is not committed)")
    void nullDecisionBlocks() {
        HookGate gate = gate(hook("K", null));
        assertThatThrownBy(() -> gate.check(task("K"), new UserId(2), HookAction.APPROVE, null))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ApprovalError.PLATFORM_APPROVAL_HOOK_BLOCKED));
    }
}
