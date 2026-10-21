package com.forcitis.one.platform.file.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

@ValueObject
public record FileId(UUID value) {

    public FileId {
        value = Uuids.requireKey(value, "FileId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
