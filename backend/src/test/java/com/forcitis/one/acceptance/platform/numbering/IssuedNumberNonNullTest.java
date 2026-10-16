package com.forcitis.one.acceptance.platform.numbering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.platform.numbering.api.DocumentNumber;
import com.forcitis.one.platform.numbering.api.IssuedNumber;
import com.forcitis.one.platform.numbering.api.NumberingCounterId;
import com.forcitis.one.platform.numbering.api.NumberingRuleVersionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IssuedNumberNonNullTest {
    static final DocumentNumber NUMBER = new DocumentNumber("QT-FST-209901-0001");
    static final NumberingCounterId COUNTER = new NumberingCounterId(TestIds.uuid(21));
    static final NumberingRuleVersionId VERSION = new NumberingRuleVersionId(TestIds.uuid(22));

    @Test
    @DisplayName("NV-05 IssuedNumber 的规则版本 ID 为空时构造失败；三个分量齐全时带出规则版本 ID")
    void issuedNumberRefusesAMissingRuleVersionId() {
        assertThatThrownBy(() -> new IssuedNumber(NUMBER, COUNTER, null)).isInstanceOf(NullPointerException.class);
        assertThat(new IssuedNumber(NUMBER, COUNTER, VERSION).ruleVersionId()).isEqualTo(VERSION);
    }

    @Test
    @DisplayName("NV-05 NumberingRuleVersionId 包的 UUID 为空时构造失败")
    void ruleVersionIdRefusesANullValue() {
        assertThatThrownBy(() -> new NumberingRuleVersionId(null)).isInstanceOf(NullPointerException.class);
    }
}
