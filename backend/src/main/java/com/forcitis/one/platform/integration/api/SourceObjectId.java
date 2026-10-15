package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.ValueObject;

/** The id of an object in the external system: a signed 64-bit number, negative ones included; it never leaves the integration layer. */
@ValueObject
public record SourceObjectId(long value) {

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
