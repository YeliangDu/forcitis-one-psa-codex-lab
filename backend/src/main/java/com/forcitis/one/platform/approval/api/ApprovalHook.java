package com.forcitis.one.platform.approval.api;

/** Runs inside the decide transaction before the task completes; an unregistered key blocks the action, duplicate keys stop startup. */
public interface ApprovalHook {

    String hookKey();

    HookDecision before(HookContext context);
}
