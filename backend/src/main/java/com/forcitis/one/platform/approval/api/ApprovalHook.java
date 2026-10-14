package com.forcitis.one.platform.approval.api;

/**
 * BEFORE-hook SPI (RULE-277/283). A business module implements it as a Spring bean; a BPMN node names it with
 * forcitis:beforeHook="{hookKey}". It runs INSIDE the decide transaction, before the task completes, so a blocked
 * action changes nothing. A node naming an unregistered key blocks the action; two beans with the same key stop startup.
 */
public interface ApprovalHook {

    String hookKey();

    HookDecision before(HookContext context);
}
