package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record NumberingRuleId(UUID value) {

    public NumberingRuleId {
        value = Uuids.requireKey(value, "NumberingRuleId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
