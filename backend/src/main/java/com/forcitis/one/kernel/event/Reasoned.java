package com.forcitis.one.kernel.event;

import com.forcitis.one.kernel.Reason;

/** An event that carries the reason the person gave for the action; the audit trail records it in its own column. */
public interface Reasoned {

    /** Null when the action was taken by the system. */
    Reason reason();
}
