export { ERROR_MESSAGES } from './error-messages';
export { describeFailure, failureOf, isVersionConflict, messageForStatus } from './failure';
export type { Failure } from './failure';
export { formatDateTime } from './format-date-time';
export { safeExternalUrl, safeInternalPath } from './safe-url';

export { claimTask, decideTask, fetchInboxPage, fetchTask, fetchTaskRecordPage, returnTask } from './approval/api';
export { INBOX_KEY, TASK_RECORDS_KEY, useInbox, useTask, useTaskActions, useTaskRecords } from './approval/hooks';
export {
  APPROVAL_STATUS_LABELS,
  DOCUMENT_TYPE_LABELS,
  PROCESS_LABELS,
  TASK_RECORD_ACTION_LABELS,
  TASK_RECORD_ACTION_TONES,
} from './approval/labels';
export type { ApprovalStatus, DocumentType, OutcomeTone, ProcessKey, TaskRecordAction } from './approval/labels';
export {
  EMPTY_OPINION,
  REASON_RULES,
  UNRECOGNISED_TASK_TITLE,
  availableActions,
  canConfirm,
  documentText,
  filterInbox,
  handlerText,
  inboxOf,
  inboxRowId,
  inboxTitle,
  opinionText,
} from './approval/model';
export type {
  DecisionResult,
  Inbox,
  InboxFilterKey,
  InboxItem,
  ReasonAction,
  ReasonRule,
  TaskActions,
  TaskDetail,
  TaskRecord,
} from './approval/model';

export { fetchCurrentUser, loginWithWecom, requestLoginState } from './session/api';
export type { LoginEntry } from './session/api';
export { installApiGuards } from './session/api-guards';
export { CURRENT_USER_KEY, useCurrentUser, useCurrentUserQuery } from './session/hooks';
export type { CurrentUser, Session, SessionStatus } from './session/hooks';
