package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.kernel.ValueObject;
import java.util.UUID;

/** The migration batch that wrote a row taken over from the legacy system; the batches themselves are kept by the integration layer. */
@ValueObject
public record MigrationBatchId(UUID value) {

    public MigrationBatchId {
        value = Uuids.requireKey(value, "MigrationBatchId");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
