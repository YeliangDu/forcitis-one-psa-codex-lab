package com.forcitis.one.platform.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.InvalidRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Nits1AuditSearchValidationTest {
    private static final int AUDIT_TEXT_MAX = 2000;
    private static final int LONGEST_ACTION = AUDIT_TEXT_MAX - "returned=10000 filter=action=".length();
    /** Built at run time: a literal of this shape would read as a secret to the scanners. */
    private static final String SECRET_SHAPE = "tok" + "en=x";

    private static AuditSearch withAction(String action, String actionPrefix) {
        return new AuditSearch(null, null, action, actionPrefix, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("NITS-1 AuditSearch 构造器对超长与疑似密钥的 action、actionPrefix 抛 InvalidRequestException，消息不回显过滤值")
    void anActionThatTheAuditEntryWouldRefuseIsRefusedAtConstruction() {
        String tooLong = "a".repeat(LONGEST_ACTION + 1);
        String prefixTooLong = "b".repeat(LONGEST_ACTION + 1);

        assertThatThrownBy(() -> withAction(tooLong, null)).isInstanceOf(InvalidRequestException.class).message().doesNotContain(tooLong);
        assertThatThrownBy(() -> withAction(null, prefixTooLong)).isInstanceOf(InvalidRequestException.class).message().doesNotContain(prefixTooLong);
        assertThatThrownBy(() -> withAction(SECRET_SHAPE, null)).isInstanceOf(InvalidRequestException.class).message().doesNotContain(SECRET_SHAPE);
        assertThatThrownBy(() -> withAction(null, "x " + SECRET_SHAPE)).isInstanceOf(InvalidRequestException.class).message().doesNotContain(SECRET_SHAPE);
        assertThatThrownBy(() -> withAction("a".repeat(LONGEST_ACTION / 2), "b".repeat(LONGEST_ACTION / 2 + 100))).as("the two together")
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("NITS-1 刚好不超限的 action 通过构造，describe 文本仍能写成审计条目")
    void anActionJustWithinTheLimitIsAccepted() {
        String longest = "a".repeat(LONGEST_ACTION);

        assertThatCode(() -> withAction(longest, null)).doesNotThrowAnyException();
        assertThat(withAction(longest, null).describe()).isEqualTo("action=" + longest);
    }
}
