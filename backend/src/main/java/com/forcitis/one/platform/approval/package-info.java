/**
 * platform.approval — 权限流程与集成平台 BC: workflow and approvals. Flowable 8 embedded (only in infrastructure.flowable), BPMN files in
 * the repository, assignment rules in BPMN extension attributes evaluated by the pure {@code AssigneeResolver}; no DMN, no designer.
 * Phase 1a: idempotent start (api only, never HTTP), claim / decide / return to initiator with server-side actor checks (I-7),
 * typed result events through the event publication registry, merged inbox (new + old system), WeCom cards, TIMESHEET replay.
 * Schema platform_approval (approval_requests, start_rounds, Flowable ACT_*).
 */
package com.forcitis.one.platform.approval;
