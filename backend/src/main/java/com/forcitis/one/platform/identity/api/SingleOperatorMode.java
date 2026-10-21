package com.forcitis.one.platform.identity.api;

/** Whether the process was started in the single operator mode (ADR-0030). Fixed at start; a change of the setting needs a restart. */
public interface SingleOperatorMode {
    boolean enabled();
}
