package com.forcitis.one.platform.identity.domain;

import java.util.List;

/** The grants one act opens and closes, each already carrying the id of the audit entry written for it in the same transaction. */
public record GrantChanges(List<GrantOpening> opened, List<GrantClosing> closed) {

    public static final GrantChanges NONE = new GrantChanges(List.of(), List.of());

    public GrantChanges {
        opened = List.copyOf(opened);
        closed = List.copyOf(closed);
    }

    public boolean isEmpty() {
        return opened.isEmpty() && closed.isEmpty();
    }
}
