package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.EmployeeId;
import java.util.List;

/**
 * The open to-dos of an employee in the old system, already merged (one per process instance and node) and free of the old system's ids
 * (ADR-0033 section 2 item 9, ADR-0039 item 5.2). Never cached, never writes, never touches token tables (I-1). When the old system
 * cannot be read, or the employee has no LEGACY_SYSTEM reference to read it with, the answer says so (available = false) instead of
 * showing an empty list.
 */
public interface LegacyInbox {

    LegacyTodos readTodos(EmployeeId person);

    record LegacyTodos(boolean available, List<LegacyTodo> todos) {

        public LegacyTodos {
            todos = List.copyOf(todos);
        }

        public static LegacyTodos unavailable() {
            return new LegacyTodos(false, List.of());
        }
    }
}
