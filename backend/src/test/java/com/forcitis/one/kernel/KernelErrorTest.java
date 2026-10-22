package com.forcitis.one.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KernelErrorTest {

    enum Phase {
        OPEN, CLOSED
    }

    static final StateMachine<Phase> MACHINE = StateMachine.of(Phase.class).allow(Phase.OPEN, Phase.CLOSED).build();

    @Test
    @DisplayName("IMP-1022-COO-02 通用错误码只在 kernel：状态转换不合法与被他人同时修改各是一个冲突类（409）拒绝，码以 KERNEL_ 开头")
    void commonRefusalsAreKernelConflicts() {
        assertThat(KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("x").errorCode().code()).isEqualTo("KERNEL_TRANSITION_ILLEGAL");
        assertThat(KernelError.KERNEL_TRANSITION_ILLEGAL.refusal("x").kind()).isEqualTo(ErrorKind.CONFLICT);
        assertThat(KernelError.KERNEL_CONCURRENT_CHANGE.refusal("x").errorCode().code()).isEqualTo("KERNEL_CONCURRENT_CHANGE");
        assertThat(KernelError.KERNEL_CONCURRENT_CHANGE.refusal("x").kind()).isEqualTo(ErrorKind.CONFLICT);
    }

    @Test
    @DisplayName("IMP-1022-COO-04 必须填写原因是一个 422 码 KERNEL_REASON_REQUIRED")
    void reasonRequiredIsAKernelUnprocessable() {
        assertThat(KernelError.KERNEL_REASON_REQUIRED.refusal("x").errorCode().code()).isEqualTo("KERNEL_REASON_REQUIRED");
        assertThat(KernelError.KERNEL_REASON_REQUIRED.refusal("x").kind()).isEqualTo(ErrorKind.UNPROCESSABLE);
    }

    @Test
    @DisplayName("IMP-1022-COO-02 StateMachine.require 不给码时默认用 KERNEL_TRANSITION_ILLEGAL，合法转换照常返回目标状态")
    void stateMachineDefaultsToTheKernelCode() {
        assertThat(MACHINE.require(Phase.OPEN, Phase.CLOSED)).isEqualTo(Phase.CLOSED);

        assertThatThrownBy(() -> MACHINE.require(Phase.CLOSED, Phase.OPEN))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_TRANSITION_ILLEGAL);
                    assertThat(e.kind()).isEqualTo(ErrorKind.CONFLICT);
                });
    }

    @Test
    @DisplayName("IMP-1022-COO-02 StateMachine.require 给了模块自己的码时仍用那个码（其它模块不受影响）")
    void anExplicitCodeStillWins() {
        assertThatThrownBy(() -> MACHINE.require(Phase.CLOSED, Phase.OPEN, KernelError.KERNEL_CONCURRENT_CHANGE))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(KernelError.KERNEL_CONCURRENT_CHANGE));
    }
}
