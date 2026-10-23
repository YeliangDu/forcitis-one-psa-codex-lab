// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
import { z } from 'zod';
import { lenientEnum } from '../contract';

export const ErrorCodeValues = ["COST_TIMESHEET_CUTOFF_PASSED", "COST_TIMESHEET_EMPLOYEE_UNKNOWN", "COST_TIMESHEET_NOT_FOUND", "COST_TIMESHEET_NOT_OWNER", "COST_TIMESHEET_PROJECT_MANAGER_INVALID", "COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN", "COST_TIMESHEET_RESULT_PENDING", "COST_TIMESHEET_WORK_DATE_DUPLICATE", "COST_TIMESHEET_WORK_DATE_FIXED", "COST_TIMESHEET_WORK_DATE_IN_FUTURE", "COST_TIMESHEET_WORK_DATE_REQUIRED", "KERNEL_CONCURRENT_CHANGE", "KERNEL_REASON_REQUIRED", "KERNEL_TRANSITION_ILLEGAL", "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE", "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN", "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR", "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED", "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY", "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT", "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND", "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED", "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED", "ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID", "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND", "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED", "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND", "ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND", "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE", "ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL", "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND", "ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN", "ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED", "ORGANIZATION_WORKFORCE_SEPARATION_HOLD_FORBIDDEN", "ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED", "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED", "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND", "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL", "PLATFORM_APPROVAL_ALREADY_APPROVED", "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID", "PLATFORM_APPROVAL_CUTOFF_PASSED", "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE", "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK", "PLATFORM_APPROVAL_DECISION_EMPTY", "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY", "PLATFORM_APPROVAL_DOCUMENT_TYPE_UNREGISTERED", "PLATFORM_APPROVAL_ESCALATION_TARGET_MISSING", "PLATFORM_APPROVAL_FORM_FIELD_UNKNOWN", "PLATFORM_APPROVAL_HOOK_BLOCKED", "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED", "PLATFORM_APPROVAL_NODE_CONFIG_INVALID", "PLATFORM_APPROVAL_NOT_ADMINISTRATOR", "PLATFORM_APPROVAL_NOT_INITIATOR", "PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND", "PLATFORM_APPROVAL_NO_PROCESS", "PLATFORM_APPROVAL_NO_ROUTE", "PLATFORM_APPROVAL_PAGE_INVALID", "PLATFORM_APPROVAL_REQUEST_NOT_FOUND", "PLATFORM_APPROVAL_ROUND_MISMATCH", "PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN", "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED", "PLATFORM_APPROVAL_TASK_NOT_FOUND", "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID", "PLATFORM_AUDIT_EXPORT_TOO_LARGE", "PLATFORM_FILE_BINDING_CONFLICT", "PLATFORM_FILE_CONTENT_READ_FORBIDDEN", "PLATFORM_FILE_DISPOSED", "PLATFORM_FILE_ENCRYPTED_REJECTED", "PLATFORM_FILE_FALSE_POSITIVE_SAME_REVIEWER", "PLATFORM_FILE_HASH_MISMATCH", "PLATFORM_FILE_LINK_VALIDITY_EXCEEDED", "PLATFORM_FILE_LINK_VALIDITY_INVALID", "PLATFORM_FILE_MEDIA_TYPE_NOT_ALLOWED", "PLATFORM_FILE_NOT_AVAILABLE", "PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT", "PLATFORM_FILE_NOT_FOUND", "PLATFORM_FILE_PRESERVATION_CATEGORY_UNKNOWN", "PLATFORM_FILE_PREVIOUS_VERSION_HAS_SUCCESSOR", "PLATFORM_FILE_PREVIOUS_VERSION_NOT_BOUND", "PLATFORM_FILE_QUARANTINED", "PLATFORM_FILE_QUARANTINE_CONTENT_PURGED", "PLATFORM_FILE_QUARANTINE_READ_NOT_APPROVED", "PLATFORM_FILE_SCAN_UNAVAILABLE", "PLATFORM_FILE_SPI_NOT_ACCEPTED", "PLATFORM_FILE_TOO_LARGE", "PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE", "PLATFORM_IDENTITY_ACCOUNT_DISABLED", "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND", "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_ADMINISTRATOR_MODE_DENIED", "PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED", "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_FOUND", "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING", "PLATFORM_IDENTITY_CONCURRENT_UPDATE", "PLATFORM_IDENTITY_CREDENTIALS_INVALID", "PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING", "PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE", "PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND", "PLATFORM_IDENTITY_EMERGENCY_ADMIN_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID", "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED", "PLATFORM_IDENTITY_FORCED_LOGOUT_REASON_REQUIRED", "PLATFORM_IDENTITY_FORCED_LOGOUT_SELF_FORBIDDEN", "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN", "PLATFORM_IDENTITY_LOGIN_RATE_LIMITED", "PLATFORM_IDENTITY_NETWORK_DENIED", "PLATFORM_IDENTITY_NOT_SIGNED_IN", "PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED", "PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN", "PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN", "PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY", "PLATFORM_IDENTITY_PERMISSION_UNKNOWN", "PLATFORM_IDENTITY_ROLE_BLANK_NAME", "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE", "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME", "PLATFORM_IDENTITY_ROLE_INVALID_CODE", "PLATFORM_IDENTITY_ROLE_IN_USE", "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED", "PLATFORM_IDENTITY_ROLE_NOT_FOUND", "PLATFORM_IDENTITY_ROLE_RESERVED_CODE", "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN", "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_SSO_UNAVAILABLE", "PLATFORM_IDENTITY_STATE_INVALID", "PLATFORM_IDENTITY_TICKET_INVALID", "PLATFORM_IDENTITY_TICKET_REPLAYED", "PLATFORM_IDENTITY_WECOM_CODE_INVALID", "PLATFORM_IDENTITY_WECOM_NOT_BOUND", "PLATFORM_INTEGRATION_CONCURRENT_UPDATE", "PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR", "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL", "PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_ALREADY_DECIDED", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_CONFLICT", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_INVALID", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_NOT_FOUND", "PLATFORM_NUMBERING_CONCURRENT_UPDATE", "PLATFORM_NUMBERING_CONFIRMATION_REPEATED", "PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR", "PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED", "PLATFORM_NUMBERING_EFFECTIVE_DATE_PASSED", "PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED", "PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND", "PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN", "PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID", "PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE", "PLATFORM_NUMBERING_NOTHING_TO_CONFIRM", "PLATFORM_NUMBERING_RULE_INVALID", "PLATFORM_NUMBERING_RULE_NOT_CONFIRMED", "PLATFORM_NUMBERING_RULE_NOT_FOUND", "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL", "PLATFORM_NUMBERING_VERSION_MISMATCH", "PLATFORM_WEB_ACCESS_DENIED", "PLATFORM_WEB_CORRELATION_INVALID", "PLATFORM_WEB_CURSOR_INVALID", "PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS", "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED", "PLATFORM_WEB_REQUEST_INVALID", "PLATFORM_WEB_REQUEST_TOO_LARGE", "PLATFORM_WEB_SOURCE_BROKEN", "PLATFORM_WEB_SOURCE_UNAVAILABLE", "PLATFORM_WEB_UNAUTHENTICATED", "PLATFORM_WEB_UNEXPECTED", "PLATFORM_WEB_VERSION_MALFORMED", "PLATFORM_WEB_VERSION_MISMATCH", "PLATFORM_WEB_VERSION_REQUIRED"] as const;

export const AccessScopeKindSchema = lenientEnum("AccessScopeKind", ["ALL", "CUSTOM_ORG_UNITS", "OWN_ORG_UNIT", "OWN_ORG_UNIT_SUBTREE", "SELF", "SELF_AND_DIRECT_REPORTS"]);

export const AdministratorModeResponseSchema = z.object({
  administratorModeExpiresAt: z.string(),
});

export const AttributeLegalEntityRequestSchema = z.object({
  effectiveFrom: z.string().optional(),
  legalEntityId: z.string().optional(),
});

export const AuditEntryResponseSchema = z.object({
  action: z.string(),
  actorId: z.string().nullable().optional(),
  afterText: z.string().nullable().optional(),
  beforeText: z.string().nullable().optional(),
  changeReason: z.string().nullable().optional(),
  createdAt: z.string(),
  detail: z.string().nullable().optional(),
  entryId: z.string(),
  loginMethod: z.string().nullable().optional(),
  onBehalfOfId: z.string().nullable().optional(),
  get outcome() { return AuditOutcomeSchema.optional(); },
  sourceAddress: z.string().nullable().optional(),
  subjectId: z.string().nullable().optional(),
  get subjectKind() { return AuditSubjectKindSchema; },
  traceNumber: z.string().nullable().optional(),
});

export const AuditOutcomeSchema = lenientEnum("AuditOutcome", ["SUCCEEDED", "FAILED"]);

export const AuditSubjectKindSchema = lenientEnum("AuditSubjectKind", ["USER_ACCOUNT", "EMPLOYEE", "ORG_UNIT", "EMERGENCY_ADMIN", "ROLE", "APPROVAL_TASK", "APPROVAL_REQUEST", "SYNC_RUN", "LEGAL_ENTITY", "LEGAL_ENTITY_ATTRIBUTION", "TIMESHEET", "WORK_CALENDAR_YEAR", "NUMBERING_RULE", "SOURCE_REFERENCE", "SOURCE_REFERENCE_REFUSAL", "AUTHORIZATION_REQUEST", "STORED_FILE", "REQUEST", "OPERATION", "LOGIN_ATTEMPT"]);

export const AuthorizationRequestViewSchema = z.object({
  kind: z.string().optional(),
  permissionCodes: z.array(z.string()).optional(),
  requestId: z.string().optional(),
  requestedAt: z.string().optional(),
  requestedBy: z.string().optional(),
  roleCodes: z.array(z.string()).optional(),
  roleId: z.string().optional(),
  roleName: z.string().optional(),
  subjectUserId: z.string().optional(),
});

export const CalendarDayRequestSchema = z.object({
  calendarDate: z.string().optional(),
  get dayKind() { return DayKindSchema.optional(); },
});

export const CalendarDayResponseSchema = z.object({
  calendarDate: z.string(),
  get dayKind() { return DayKindSchema; },
});

export const ConfirmationStatusSchema = lenientEnum("ConfirmationStatus", ["PENDING_CONFIRMATION", "CONFIRMED"]);

export const ConfirmNumberingRuleRequestSchema = z.object({
  templateVersion: z.number().int().optional(),
});

export const CorrectLegalEntityAttributionRequestSchema = z.object({
  effectiveTo: z.string().optional(),
  periodFrom: z.string().optional(),
  reason: z.string().optional(),
});

export const CreateLegalEntityRequestSchema = z.object({
  code: z.string().optional(),
  name: z.string().optional(),
});

export const CurrentUserResponseSchema = z.object({
  administratorModeExpiresAt: z.string().nullable().optional(),
  displayName: z.string(),
  identityConfirmed: z.boolean(),
  mode: z.string(),
  get navigation() { return z.array(NavigationResponseSchema); },
  orgUnitId: z.string().nullable().optional(),
  orgUnitName: z.string().nullable().optional(),
  permissionCodes: z.array(z.string()),
  userId: z.string(),
});

export const CursorPageAuditEntryResponseSchema = z.object({
  get items() { return z.array(AuditEntryResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageAuthorizationRequestViewSchema = z.object({
  get items() { return z.array(AuthorizationRequestViewSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageLegacyNumberResponseSchema = z.object({
  get items() { return z.array(LegacyNumberResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageLegalEntityResponseSchema = z.object({
  get items() { return z.array(LegalEntityResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageMaintainedYearResponseSchema = z.object({
  get items() { return z.array(MaintainedYearResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageNumberingRuleResponseSchema = z.object({
  get items() { return z.array(NumberingRuleResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageOwnLoginRecordResponseSchema = z.object({
  get items() { return z.array(OwnLoginRecordResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageRoleResponseSchema = z.object({
  get items() { return z.array(RoleResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageSourceReferenceResponseSchema = z.object({
  get items() { return z.array(SourceReferenceResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageSyncRunResponseSchema = z.object({
  get items() { return z.array(SyncRunResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const DayKindSchema = lenientEnum("DayKind", ["HOLIDAY", "MAKE_UP_WORKDAY"]);

export const DecideTaskRequestSchema = z.object({
  get outcome() { return DecisionOutcomeSchema.optional(); },
  reason: z.string().optional(),
});

export const DecisionOutcomeSchema = lenientEnum("DecisionOutcome", ["APPROVE", "REJECT"]);

export const DecisionResponseSchema = z.object({
  instanceId: z.string(),
  status: z.string(),
});

export const EmergencyLoginRequestSchema = z.object({
  loginName: z.string().optional(),
  password: z.string().optional(),
  totpCode: z.string().optional(),
});

export const EndLegalEntityAttributionRequestSchema = z.object({
  effectiveTo: z.string().optional(),
});

export const ErrorCodeSchema = lenientEnum("ErrorCode", ["COST_TIMESHEET_CUTOFF_PASSED", "COST_TIMESHEET_EMPLOYEE_UNKNOWN", "COST_TIMESHEET_NOT_FOUND", "COST_TIMESHEET_NOT_OWNER", "COST_TIMESHEET_PROJECT_MANAGER_INVALID", "COST_TIMESHEET_PROJECT_MANAGER_UNKNOWN", "COST_TIMESHEET_RESULT_PENDING", "COST_TIMESHEET_WORK_DATE_DUPLICATE", "COST_TIMESHEET_WORK_DATE_FIXED", "COST_TIMESHEET_WORK_DATE_IN_FUTURE", "COST_TIMESHEET_WORK_DATE_REQUIRED", "KERNEL_CONCURRENT_CHANGE", "KERNEL_REASON_REQUIRED", "KERNEL_TRANSITION_ILLEGAL", "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE", "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN", "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR", "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED", "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY", "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT", "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND", "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED", "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED", "ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID", "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND", "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED", "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND", "ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND", "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE", "ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL", "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND", "ORGANIZATION_WORKFORCE_OWN_ATTRIBUTION_FORBIDDEN", "ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED", "ORGANIZATION_WORKFORCE_SEPARATION_HOLD_FORBIDDEN", "ORGANIZATION_WORKFORCE_SEPARATION_HOLD_REFUSED", "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED", "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND", "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL", "PLATFORM_APPROVAL_ALREADY_APPROVED", "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID", "PLATFORM_APPROVAL_CUTOFF_PASSED", "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE", "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK", "PLATFORM_APPROVAL_DECISION_EMPTY", "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY", "PLATFORM_APPROVAL_DOCUMENT_TYPE_UNREGISTERED", "PLATFORM_APPROVAL_ESCALATION_TARGET_MISSING", "PLATFORM_APPROVAL_FORM_FIELD_UNKNOWN", "PLATFORM_APPROVAL_HOOK_BLOCKED", "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED", "PLATFORM_APPROVAL_NODE_CONFIG_INVALID", "PLATFORM_APPROVAL_NOT_ADMINISTRATOR", "PLATFORM_APPROVAL_NOT_INITIATOR", "PLATFORM_APPROVAL_NO_ASSIGNEE_FOUND", "PLATFORM_APPROVAL_NO_PROCESS", "PLATFORM_APPROVAL_NO_ROUTE", "PLATFORM_APPROVAL_PAGE_INVALID", "PLATFORM_APPROVAL_REQUEST_NOT_FOUND", "PLATFORM_APPROVAL_ROUND_MISMATCH", "PLATFORM_APPROVAL_ROUTING_FLAG_UNKNOWN", "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED", "PLATFORM_APPROVAL_TASK_NOT_FOUND", "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID", "PLATFORM_AUDIT_EXPORT_TOO_LARGE", "PLATFORM_FILE_BINDING_CONFLICT", "PLATFORM_FILE_CONTENT_READ_FORBIDDEN", "PLATFORM_FILE_DISPOSED", "PLATFORM_FILE_ENCRYPTED_REJECTED", "PLATFORM_FILE_FALSE_POSITIVE_SAME_REVIEWER", "PLATFORM_FILE_HASH_MISMATCH", "PLATFORM_FILE_LINK_VALIDITY_EXCEEDED", "PLATFORM_FILE_LINK_VALIDITY_INVALID", "PLATFORM_FILE_MEDIA_TYPE_NOT_ALLOWED", "PLATFORM_FILE_NOT_AVAILABLE", "PLATFORM_FILE_NOT_BOUND_TO_DOCUMENT", "PLATFORM_FILE_NOT_FOUND", "PLATFORM_FILE_PRESERVATION_CATEGORY_UNKNOWN", "PLATFORM_FILE_PREVIOUS_VERSION_HAS_SUCCESSOR", "PLATFORM_FILE_PREVIOUS_VERSION_NOT_BOUND", "PLATFORM_FILE_QUARANTINED", "PLATFORM_FILE_QUARANTINE_CONTENT_PURGED", "PLATFORM_FILE_QUARANTINE_READ_NOT_APPROVED", "PLATFORM_FILE_SCAN_UNAVAILABLE", "PLATFORM_FILE_SPI_NOT_ACCEPTED", "PLATFORM_FILE_TOO_LARGE", "PLATFORM_IDENTITY_ACCESS_SCOPE_UNAVAILABLE", "PLATFORM_IDENTITY_ACCOUNT_DISABLED", "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND", "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_ADMINISTRATOR_MODE_DENIED", "PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED", "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_FOUND", "PLATFORM_IDENTITY_AUTHORIZATION_REQUEST_NOT_PENDING", "PLATFORM_IDENTITY_CONCURRENT_UPDATE", "PLATFORM_IDENTITY_CREDENTIALS_INVALID", "PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING", "PLATFORM_IDENTITY_EMERGENCY_ADMIN_DUPLICATE", "PLATFORM_IDENTITY_EMERGENCY_ADMIN_NOT_FOUND", "PLATFORM_IDENTITY_EMERGENCY_ADMIN_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_EMERGENCY_BINDING_INVALID", "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED", "PLATFORM_IDENTITY_FORCED_LOGOUT_REASON_REQUIRED", "PLATFORM_IDENTITY_FORCED_LOGOUT_SELF_FORBIDDEN", "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN", "PLATFORM_IDENTITY_LOGIN_RATE_LIMITED", "PLATFORM_IDENTITY_NETWORK_DENIED", "PLATFORM_IDENTITY_NOT_SIGNED_IN", "PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED", "PLATFORM_IDENTITY_OWN_ROLES_FORBIDDEN", "PLATFORM_IDENTITY_OWN_ROLE_DEFINITION_FORBIDDEN", "PLATFORM_IDENTITY_PERMISSION_ADMINISTRATOR_MODE_ONLY", "PLATFORM_IDENTITY_PERMISSION_UNKNOWN", "PLATFORM_IDENTITY_ROLE_BLANK_NAME", "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE", "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME", "PLATFORM_IDENTITY_ROLE_INVALID_CODE", "PLATFORM_IDENTITY_ROLE_IN_USE", "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED", "PLATFORM_IDENTITY_ROLE_NOT_FOUND", "PLATFORM_IDENTITY_ROLE_RESERVED_CODE", "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN", "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_SSO_UNAVAILABLE", "PLATFORM_IDENTITY_STATE_INVALID", "PLATFORM_IDENTITY_TICKET_INVALID", "PLATFORM_IDENTITY_TICKET_REPLAYED", "PLATFORM_IDENTITY_WECOM_CODE_INVALID", "PLATFORM_IDENTITY_WECOM_NOT_BOUND", "PLATFORM_INTEGRATION_CONCURRENT_UPDATE", "PLATFORM_INTEGRATION_CONFIRMER_IS_AUTHOR", "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL", "PLATFORM_INTEGRATION_SOURCE_OBJECT_KIND_NOT_ALLOWED", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_ALREADY_DECIDED", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_CONFLICT", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_INVALID", "PLATFORM_INTEGRATION_SOURCE_REFERENCE_NOT_FOUND", "PLATFORM_NUMBERING_CONCURRENT_UPDATE", "PLATFORM_NUMBERING_CONFIRMATION_REPEATED", "PLATFORM_NUMBERING_CONFIRMER_IS_AUTHOR", "PLATFORM_NUMBERING_DOCUMENT_TYPE_UNREGISTERED", "PLATFORM_NUMBERING_EFFECTIVE_DATE_PASSED", "PLATFORM_NUMBERING_FINANCE_CONFIRMATION_NOT_REQUIRED", "PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND", "PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN", "PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID", "PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE", "PLATFORM_NUMBERING_NOTHING_TO_CONFIRM", "PLATFORM_NUMBERING_RULE_INVALID", "PLATFORM_NUMBERING_RULE_NOT_CONFIRMED", "PLATFORM_NUMBERING_RULE_NOT_FOUND", "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL", "PLATFORM_NUMBERING_VERSION_MISMATCH", "PLATFORM_WEB_ACCESS_DENIED", "PLATFORM_WEB_CORRELATION_INVALID", "PLATFORM_WEB_CURSOR_INVALID", "PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS", "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED", "PLATFORM_WEB_REQUEST_INVALID", "PLATFORM_WEB_REQUEST_TOO_LARGE", "PLATFORM_WEB_SOURCE_BROKEN", "PLATFORM_WEB_SOURCE_UNAVAILABLE", "PLATFORM_WEB_UNAUTHENTICATED", "PLATFORM_WEB_UNEXPECTED", "PLATFORM_WEB_VERSION_MALFORMED", "PLATFORM_WEB_VERSION_MISMATCH", "PLATFORM_WEB_VERSION_REQUIRED"]);

export const ExternalSourceSchema = lenientEnum("ExternalSource", ["ORG_SOURCE", "LEGACY_SYSTEM", "ACCOUNTING_SYSTEM"]);

export const FileStatusSchema = lenientEnum("FileStatus", ["AVAILABLE", "QUARANTINED", "EXPIRED_UNBOUND", "DISPOSED"]);

export const FileStatusResponseSchema = z.object({
  fileId: z.string(),
  get status() { return FileStatusSchema; },
});

export const ForcedLogoutRequestSchema = z.object({
  reason: z.string().optional(),
});

export const HandledTaskPageResponseSchema = z.object({
  get items() { return z.array(HandledTaskResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const HandledTaskResponseSchema = z.object({
  get action() { return TaskRecordActionSchema; },
  actorId: z.string(),
  actorName: z.string(),
  decidedAt: z.string(),
  documentId: z.string(),
  documentType: z.string(),
  instanceId: z.string(),
  nodeKey: z.string(),
  nodeName: z.string(),
  onBehalfOfName: z.string().nullable().optional(),
  get processKey() { return ProcessKeySchema; },
  reason: z.string().nullable().optional(),
  recordId: z.string(),
  round: z.number().int(),
  taskId: z.string(),
});

export const HistoryEntrySchema = z.object({
  get action() { return TaskRecordActionSchema; },
  actorId: z.string(),
  actorName: z.string(),
  assigneeName: z.string().nullable().optional(),
  decidedAt: z.string(),
  nodeKey: z.string(),
  nodeName: z.string(),
  onBehalfOfName: z.string().nullable().optional(),
  reason: z.string().nullable().optional(),
  recordId: z.string(),
  round: z.number().int(),
  taskId: z.string(),
});

export const InboxItemResponseSchema = z.object({
  category: z.string(),
  claimed: z.boolean(),
  createdAt: z.string().nullable().optional(),
  deadlineAt: z.string().nullable().optional(),
  documentId: z.string().nullable().optional(),
  nodeName: z.string().nullable().optional(),
  overdue: z.boolean(),
  get processKey() { return ProcessKeySchema.optional(); },
  taskId: z.string(),
  title: z.string(),
});

export const InboxResponseSchema = z.object({
  get items() { return z.array(InboxItemResponseSchema); },
  newCount: z.number().int(),
  nextCursor: z.string().nullable().optional(),
  noticeCount: z.number().int(),
  totalCount: z.number().int(),
});

export const LegacyNumberResponseSchema = z.object({
  documentId: z.string(),
  documentType: z.string(),
  legacyNumber: z.string(),
  legalEntityId: z.string().nullable().optional(),
  migrationBatchId: z.string(),
  get sourceSystem() { return ExternalSourceSchema; },
});

export const LegalEntityAttributionResponseSchema = z.object({
  kind: z.string(),
  get periods() { return z.array(PeriodSchema); },
  subjectId: z.string(),
});

export const LegalEntityResponseSchema = z.object({
  code: z.string(),
  legalEntityId: z.string(),
  name: z.string(),
  status: z.string(),
});

export const LoginResponseSchema = z.object({
  absoluteExpiresAt: z.string(),
  administratorModeExpiresAt: z.string().nullable().optional(),
  identityConfirmationRequired: z.boolean(),
  userId: z.string(),
});

export const LoginStateRequestSchema = z.object({
  get entry() { return WecomEntrySchema.optional(); },
});

export const LoginStateResponseSchema = z.object({
  state: z.string(),
});

export const MaintainedYearResponseSchema = z.object({
  calendarYear: z.number().int(),
});

export const MaintainWorkCalendarRequestSchema = z.object({
  get days() { return z.array(CalendarDayRequestSchema).optional(); },
  lockedDaysConfirmed: z.boolean().optional(),
});

export const NavigationResponseSchema = z.object({
  get children() { return z.array(NavigationResponseSchema); },
  name: z.string(),
  routePath: z.string().nullable().optional(),
});

export const NumberingRuleRequestSchema = z.object({
  effectiveFrom: z.string().optional(),
  pattern: z.string().optional(),
  get resetPolicy() { return ResetPolicySchema.optional(); },
  startValue: z.number().int().optional(),
});

export const NumberingRuleResponseSchema = z.object({
  capacity: z.number().int(),
  get confirmationStatus() { return ConfirmationStatusSchema; },
  documentType: z.string(),
  documentTypeCode: z.string().optional(),
  effectiveFrom: z.string(),
  pattern: z.string(),
  get pending() { return PendingTemplateSchema.optional(); },
  requiresFinanceConfirmation: z.boolean(),
  get resetPolicy() { return ResetPolicySchema; },
  startValue: z.number().int(),
  templateVersion: z.number().int(),
  version: z.number().int(),
});

export const OrgReconciliationResponseSchema = z.object({
  missingInSnapshot: z.array(z.string()),
  parentMismatches: z.array(z.string()),
  reportingLineMismatches: z.array(z.string()),
  responsibilityMismatches: z.array(z.string()),
  snapshotEmployedCount: z.number().int(),
  snapshotOrgUnitCount: z.number().int(),
  sourceEmployedCount: z.number().int(),
  sourceOrgUnitCount: z.number().int(),
  unexpectedInSnapshot: z.array(z.string()),
  wecomUserIdMismatches: z.array(z.string()),
  zeroDifference: z.boolean(),
});

export const OrgUnitDetailResponseSchema = z.object({
  ancestorOrgUnitIds: z.array(z.string()),
  get orgUnit() { return OrgUnitResponseSchema; },
  orgUnitAndBelowIds: z.array(z.string()),
});

export const OrgUnitResponseSchema = z.object({
  code: z.string(),
  deliveryUnit: z.boolean(),
  headEmployeeId: z.string().nullable().optional(),
  name: z.string(),
  orgUnitId: z.string(),
  parentOrgUnitId: z.string().nullable().optional(),
  regionCategory: z.string().nullable().optional(),
  regionalReceivablesManagerId: z.string().nullable().optional(),
  shortName: z.string().nullable().optional(),
  sortOrder: z.number().int().nullable().optional(),
  status: z.string(),
});

export const OwnLoginRecordResponseSchema = z.object({
  action: z.string(),
  loginMethod: z.string().nullable().optional(),
  occurredAt: z.string(),
  outcome: z.string().nullable().optional(),
  sourceAddress: z.string().nullable().optional(),
});

export const PendingTemplateSchema = z.object({
  authorId: z.string().nullable().optional(),
  authoredAt: z.string(),
  capacity: z.number().int(),
  confirmedBy: z.string().nullable().optional(),
  effectiveFrom: z.string(),
  financeConfirmedBy: z.string().nullable().optional(),
  pattern: z.string(),
  get resetPolicy() { return ResetPolicySchema; },
  startValue: z.number().int(),
  templateVersion: z.number().int(),
});

export const PeriodSchema = z.object({
  effectiveFrom: z.string(),
  effectiveTo: z.string().nullable().optional(),
  legalEntityId: z.string(),
});

export const ProblemDetailSchema = z.object({
  correlationId: z.string().optional(),
  detail: z.string().optional(),
  get errorCode() { return ErrorCodeSchema; },
  instance: z.string().optional(),
  invalidParameters: z.string().optional(),
  legacyLoginUrl: z.string().optional(),
  status: z.number().int(),
  title: z.string().optional(),
  type: z.string().optional(),
});

export const ProcessKeySchema = lenientEnum("ProcessKey", ["COST_TIMESHEET_APPROVAL"]).nullable();

export const ReassignTaskRequestSchema = z.object({
  assigneeId: z.string().optional(),
  reason: z.string().optional(),
});

export const RegisterSourceReferenceRequestSchema = z.object({
  effectiveFrom: z.string().optional(),
  ownId: z.string().optional(),
  sourceId: z.string().optional(),
  get sourceObjectKind() { return SourceObjectKindSchema.optional(); },
  get sourceSystem() { return ExternalSourceSchema.optional(); },
});

export const ReleaseRequestSchema = z.object({
  reason: z.string().optional(),
});

export const ReleaseResponseSchema = z.object({
  message: z.string().nullable().optional(),
  outcome: z.string(),
  get syncRun() { return SyncRunResponseSchema.optional(); },
});

export const ReplaceRolesRequestSchema = z.object({
  roleCodes: z.array(z.string()).optional(),
});

export const ResetPolicySchema = lenientEnum("ResetPolicy", ["NEVER", "YEARLY", "MONTHLY", "DAILY"]);

export const ReturnTaskRequestSchema = z.object({
  reason: z.string().optional(),
});

export const RoleRequestSchema = z.object({
  get accessScope() { return AccessScopeKindSchema.optional(); },
  code: z.string().optional(),
  enabled: z.boolean().optional(),
  name: z.string().optional(),
  permissionCodes: z.array(z.string()).optional(),
});

export const RoleResponseSchema = z.object({
  get accessScope() { return AccessScopeKindSchema; },
  administrative: z.boolean(),
  code: z.string(),
  enabled: z.boolean(),
  kind: z.string(),
  name: z.string(),
  permissionCodes: z.array(z.string()),
  roleId: z.string(),
});

export const SourceObjectKindSchema = lenientEnum("SourceObjectKind", ["EMPLOYEE", "ORG_UNIT", "POSITION", "LEGAL_ENTITY"]);

export const SourceReferenceConfirmationStatusSchema = lenientEnum("SourceReferenceConfirmationStatus", ["PENDING", "CONFIRMED", "REJECTED"]);

export const SourceReferenceResponseSchema = z.object({
  get confirmationStatus() { return SourceReferenceConfirmationStatusSchema; },
  effectiveFrom: z.string().nullable().optional(),
  effectiveTo: z.string().nullable().optional(),
  ownId: z.string(),
  sourceId: z.string(),
  get sourceObjectKind() { return SourceObjectKindSchema; },
  sourceReferenceId: z.string(),
  get sourceSystem() { return ExternalSourceSchema; },
});

export const StoredFileResponseSchema = z.object({
  byteCount: z.number().int(),
  containsMacros: z.boolean(),
  contentHash: z.string(),
  fileId: z.string(),
  mediaType: z.string(),
  get status() { return FileStatusSchema; },
});

export const SyncDiffResponseSchema = z.object({
  changeKind: z.string(),
  changedFields: z.array(z.string()),
  detail: z.string().nullable().optional(),
  entityKind: z.string(),
  issueKind: z.string().nullable().optional(),
  subjectKey: z.string(),
});

export const SyncRunDetailResponseSchema = z.object({
  get diffs() { return z.array(SyncDiffResponseSchema); },
  get syncRun() { return SyncRunResponseSchema; },
});

export const SyncRunResponseSchema = z.object({
  employedWithoutWecomCount: z.number().int().nullable().optional(),
  employeeChangeCount: z.number().int().nullable().optional(),
  failureReason: z.string().nullable().optional(),
  finishedAt: z.string().nullable().optional(),
  heldReason: z.string().nullable().optional(),
  heldSeparations: z.array(z.string()),
  holdKinds: z.array(z.string()),
  incomingActiveOrgUnitCount: z.number().int().nullable().optional(),
  incomingEmployedCount: z.number().int().nullable().optional(),
  isolatedRowCount: z.number().int().nullable().optional(),
  issueCount: z.number().int().nullable().optional(),
  orgUnitChangeCount: z.number().int().nullable().optional(),
  positionChangeCount: z.number().int().nullable().optional(),
  previousActiveOrgUnitCount: z.number().int().nullable().optional(),
  previousEmployedCount: z.number().int().nullable().optional(),
  releasedAt: z.string().nullable().optional(),
  releasedBy: z.string().nullable().optional(),
  releasedByRunId: z.string().nullable().optional(),
  releasedKinds: z.array(z.string()),
  sourceEmployeeCount: z.number().int().nullable().optional(),
  sourceOrgUnitCount: z.number().int().nullable().optional(),
  startedAt: z.string(),
  status: z.string(),
  syncRunId: z.string(),
  trigger: z.string(),
});

export const TaskDetailResponseSchema = z.object({
  assignee: z.string().nullable().optional(),
  assigneeName: z.string().nullable().optional(),
  canAct: z.boolean(),
  candidateNames: z.array(z.string()),
  candidates: z.array(z.string()),
  completedAt: z.string().nullable().optional(),
  createdAt: z.string(),
  deadlineAt: z.string().nullable().optional(),
  documentId: z.string(),
  documentType: z.string(),
  dueAt: z.string().nullable().optional(),
  effect: z.string().nullable().optional(),
  get history() { return z.array(HistoryEntrySchema); },
  initiator: z.string(),
  initiatorName: z.string(),
  initiatorOrgUnitName: z.string().nullable().optional(),
  instanceId: z.string(),
  nodeKey: z.string(),
  nodeName: z.string().nullable().optional(),
  outcome: z.string().nullable().optional(),
  overdue: z.boolean(),
  get processKey() { return ProcessKeySchema; },
  reason: z.string().nullable().optional(),
  status: z.string(),
  summary: z.string().nullable().optional(),
  taskId: z.string(),
});

export const TaskOverviewPageResponseSchema = z.object({
  get items() { return z.array(TaskOverviewResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const TaskOverviewResponseSchema = z.object({
  assignee: z.string().nullable().optional(),
  createdAt: z.string(),
  documentId: z.string(),
  documentType: z.string(),
  dueAt: z.string().nullable().optional(),
  nodeKey: z.string(),
  overdue: z.boolean(),
  get processKey() { return ProcessKeySchema; },
  status: z.string(),
  taskId: z.string(),
});

export const TaskRecordActionSchema = lenientEnum("TaskRecordAction", ["CLAIMED", "RELEASED", "APPROVED", "REJECTED", "RETURNED", "ADVISED", "PASSED_AUTOMATICALLY", "TRANSFERRED", "REASSIGNED"]);

export const TicketLoginRequestSchema = z.object({
  ticket: z.string().optional(),
});

export const TransferTaskRequestSchema = z.object({
  assigneeId: z.string().optional(),
  reason: z.string().optional(),
});

export const UpdateLegalEntityRequestSchema = z.object({
  code: z.string().optional(),
  name: z.string().optional(),
  status: z.string().optional(),
});

export const UserAccountResponseSchema = z.object({
  roleCodes: z.array(z.string()),
  status: z.string(),
  userId: z.string(),
});

export const WecomEntrySchema = lenientEnum("WecomEntry", ["WORKBENCH", "MINI_PROGRAM", "QR_CODE_LOGIN"]);

export const WecomLoginRequestSchema = z.object({
  code: z.string().optional(),
  get entry() { return WecomEntrySchema.optional(); },
  state: z.string().optional(),
});

export const WorkCalendarYearResponseSchema = z.object({
  calendarYear: z.number().int(),
  get days() { return z.array(CalendarDayResponseSchema); },
});

export const responseSchemas = {
  getOrganizationEmployeeLegalEntitiesByEmployeeId: LegalEntityAttributionResponseSchema,
  putOrganizationEmployeeLegalEntitiesByEmployeeId: LegalEntityAttributionResponseSchema,
  postOrganizationEmployeeLegalEntitiesByEmployeeIdCorrect: LegalEntityAttributionResponseSchema,
  postOrganizationEmployeeLegalEntitiesByEmployeeIdEnd: LegalEntityAttributionResponseSchema,
  getOrganizationLegalEntities: CursorPageLegalEntityResponseSchema,
  postOrganizationLegalEntities: LegalEntityResponseSchema,
  getOrganizationLegalEntitiesByLegalEntityId: LegalEntityResponseSchema,
  putOrganizationLegalEntitiesByLegalEntityId: LegalEntityResponseSchema,
  getOrganizationOrgUnitLegalEntitiesByOrgUnitId: LegalEntityAttributionResponseSchema,
  putOrganizationOrgUnitLegalEntitiesByOrgUnitId: LegalEntityAttributionResponseSchema,
  postOrganizationOrgUnitLegalEntitiesByOrgUnitIdCorrect: LegalEntityAttributionResponseSchema,
  postOrganizationOrgUnitLegalEntitiesByOrgUnitIdEnd: LegalEntityAttributionResponseSchema,
  getOrganizationOrgUnits: z.array(OrgUnitResponseSchema),
  getOrganizationOrgUnitsByOrgUnitId: OrgUnitDetailResponseSchema,
  getOrganizationSyncRuns: CursorPageSyncRunResponseSchema,
  getOrganizationSyncRunsBySyncRunId: SyncRunDetailResponseSchema,
  postOrganizationSyncRunsBySyncRunIdRelease: ReleaseResponseSchema,
  postOrganizationSyncRunsReconcile: OrgReconciliationResponseSchema,
  getOrganizationWorkCalendars: CursorPageMaintainedYearResponseSchema,
  getOrganizationWorkCalendarsByCalendarYear: WorkCalendarYearResponseSchema,
  putOrganizationWorkCalendarsByCalendarYear: WorkCalendarYearResponseSchema,
  getPlatformApprovalTaskOverviews: TaskOverviewPageResponseSchema,
  getPlatformApprovalTaskRecords: HandledTaskPageResponseSchema,
  getPlatformApprovalTasks: InboxResponseSchema,
  getPlatformApprovalTasksByTaskId: TaskDetailResponseSchema,
  postPlatformApprovalTasksByTaskIdDecide: DecisionResponseSchema,
  postPlatformApprovalTasksByTaskIdReturn: DecisionResponseSchema,
  getPlatformAuditEntries: CursorPageAuditEntryResponseSchema,
  getPlatformAuditEntriesExport: z.string(),
  getPlatformAuthorizationRequests: CursorPageAuthorizationRequestViewSchema,
  postPlatformFiles: StoredFileResponseSchema,
  getPlatformFilesByFileId: FileStatusResponseSchema,
  getPlatformLegacyNumbers: CursorPageLegacyNumberResponseSchema,
  getPlatformLegacyNumbersResolve: LegacyNumberResponseSchema,
  postPlatformLoginStates: LoginStateResponseSchema,
  getPlatformNumberingRules: CursorPageNumberingRuleResponseSchema,
  getPlatformNumberingRulesByDocumentType: NumberingRuleResponseSchema,
  putPlatformNumberingRulesByDocumentType: NumberingRuleResponseSchema,
  postPlatformNumberingRulesByDocumentTypeConfirm: NumberingRuleResponseSchema,
  postPlatformNumberingRulesByDocumentTypeConfirmFinance: NumberingRuleResponseSchema,
  getPlatformOwnLoginRecords: CursorPageOwnLoginRecordResponseSchema,
  getPlatformRoles: CursorPageRoleResponseSchema,
  postPlatformRoles: z.unknown(),
  getPlatformRolesByRoleId: RoleResponseSchema,
  putPlatformRolesByRoleId: z.unknown(),
  getPlatformSessionsCurrent: CurrentUserResponseSchema,
  postPlatformSessionsEmergencyLogin: LoginResponseSchema,
  postPlatformSessionsEnterAdministratorMode: AdministratorModeResponseSchema,
  postPlatformSessionsSsoLogin: LoginResponseSchema,
  postPlatformSessionsTicketLogin: LoginResponseSchema,
  postPlatformSessionsWecomLogin: LoginResponseSchema,
  getPlatformSourceReferences: CursorPageSourceReferenceResponseSchema,
  postPlatformSourceReferences: SourceReferenceResponseSchema,
  postPlatformSourceReferencesBySourceReferenceIdConfirm: SourceReferenceResponseSchema,
  postPlatformSourceReferencesBySourceReferenceIdReject: SourceReferenceResponseSchema,
  getPlatformUserAccountsByUserId: UserAccountResponseSchema,
  postPlatformUserAccountsByUserIdReplaceRoles: z.unknown(),
} as const;
