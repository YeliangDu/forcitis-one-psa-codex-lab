package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record NumberingRuleVersionId(UUID value) {

    public NumberingRuleVersionId {
        value = Uuids.requireKey(value, "NumberingRuleVersionId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
