package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.List;

/**
 * Read-only port to the old system's to-do view (REIMAGINED_ARCHITECTURE §3.6). Never cached (each call queries), never
 * writes, never touches token tables (I-1). When the old database cannot be reached the result says so
 * (available = false) instead of silently showing an empty list.
 */
public interface LegacyInbox {

    LegacyTodos readTodos(UserId user);

    /** available = false when the old database is not configured or not reachable; todos are then empty. */
    record LegacyTodos(boolean available, List<LegacyTodo> todos) {

        public LegacyTodos {
            todos = List.copyOf(todos);
        }

        public static LegacyTodos unavailable() {
            return new LegacyTodos(false, List.of());
        }
    }
}
