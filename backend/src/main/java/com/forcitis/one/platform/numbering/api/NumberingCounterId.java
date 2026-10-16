package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record NumberingCounterId(UUID value) {

    public NumberingCounterId {
        value = Uuids.requireKey(value, "NumberingCounterId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
