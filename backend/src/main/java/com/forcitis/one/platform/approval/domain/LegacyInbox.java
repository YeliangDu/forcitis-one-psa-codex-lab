package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.EmployeeId;
import java.util.List;

/**
 * The old system names a person by the organisation source's person id, which is the employee id until R-1b P3b moves this to the
 * integration layer. Never cached, never writes, never touches token tables (I-1); an unreachable database says so (available = false) instead of showing an empty list. */
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
