package com.forcitis.one.organization.workforce.domain;

/** How a hold was released (ADR-0068 item 2): in the interface by a named account, or by the two-person operations command, whose people are only audited. */
public enum ReleaseChannelKind {
    INTERFACE,
    OPERATIONS_COMMAND
}
