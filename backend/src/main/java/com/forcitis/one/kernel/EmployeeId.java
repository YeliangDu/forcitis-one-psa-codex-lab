package com.forcitis.one.kernel;

@ValueObject
public record EmployeeId(long value) {
    public EmployeeId {
        if (value == 0) {
            throw new IllegalArgumentException("EmployeeId must not be 0");
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
