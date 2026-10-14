package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.List;

/** Never cached, never writes, never touches token tables (I-1); an unreachable database says so (available = false) instead of showing an empty list. */
public interface LegacyInbox {

    LegacyTodos readTodos(UserId user);

    record LegacyTodos(boolean available, List<LegacyTodo> todos) {

        public LegacyTodos {
            todos = List.copyOf(todos);
        }

        public static LegacyTodos unavailable() {
            return new LegacyTodos(false, List.of());
        }
    }
}
