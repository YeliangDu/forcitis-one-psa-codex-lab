package com.forcitis.one.kernel;

import java.lang.reflect.RecordComponent;

/** What makes a type a typed id: a record of one component named value whose own name ends in Id. */
public final class IdRecords {

    private IdRecords() {
    }

    public static boolean isIdRecord(Class<?> type) {
        if (!type.isRecord() || !type.getSimpleName().endsWith("Id")) {
            return false;
        }
        RecordComponent[] components = type.getRecordComponents();
        return components.length == 1 && components[0].getName().equals("value");
    }
}
