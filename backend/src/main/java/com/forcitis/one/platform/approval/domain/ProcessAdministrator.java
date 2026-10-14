package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Set;

/** The process administrators are the people who hold the process administrator role (D-1a-61): one or several, empty when nobody does. */
public interface ProcessAdministrator {

    Set<UserId> holders();

    default boolean isAdministrator(UserId user) {
        return holders().contains(user);
    }
}
