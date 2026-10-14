// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
import { z } from 'zod';

export const ApprovalStatusSchema = z.enum(["APPROVED", "CANCELLED", "REJECTED", "RETURNED", "RUNNING"]);

export const AuditEntryPageResponseSchema = z.object({
  get items() { return z.array(AuditEntryResponseSchema); },
  nextCursor: z.string().optional(),
});

export const AuditEntryResponseSchema = z.object({
  action: z.string(),
  actorId: z.string().optional(),
  createdAt: z.string(),
  detail: z.string().optional(),
  entryId: z.string(),
  onBehalfOfId: z.string().optional(),
  subjectKey: z.string(),
});

export const CalendarDayRequestSchema = z.object({
  calendarDate: z.string(),
  get dayKind() { return DayKindSchema; },
});

export const CalendarDayResponseSchema = z.object({
  calendarDate: z.string(),
  get dayKind() { return DayKindSchema; },
});

export const CreateLegalEntityRequestSchema = z.object({
  code: z.string().optional(),
  name: z.string().optional(),
});

export const CurrentUserResponseSchema = z.object({
  displayName: z.string(),
  get navigation() { return z.array(NavigationResponseSchema); },
  orgUnitId: z.string().optional(),
  orgUnitName: z.string().optional(),
  permissionCodes: z.array(z.string()),
  userId: z.string(),
});

export const DayKindSchema = z.enum(["HOLIDAY", "MAKE_UP_WORKDAY"]);

export const DecideTaskRequestSchema = z.object({
  outcome: z.enum(["APPROVE", "REJECT"]).optional(),
  reason: z.string().optional(),
});

export const DecisionResponseSchema = z.object({
  instanceId: z.string(),
  get status() { return ApprovalStatusSchema; },
});

export const DocumentTypeSchema = z.enum(["JOURNAL_VOUCHER", "QUOTE", "TIMESHEET"]);

export const EmergencyLoginRequestSchema = z.object({
  loginName: z.string().optional(),
  password: z.string().optional(),
  totpCode: z.string().optional(),
});

export const ErrorCodeSchema = z.enum(["COST_TIMESHEET_CONCURRENT_CHANGE", "COST_TIMESHEET_CUTOFF_PASSED", "COST_TIMESHEET_EXCEEDS_REMAINING_HOURS", "COST_TIMESHEET_NOT_FOUND", "COST_TIMESHEET_REASON_REQUIRED", "COST_TIMESHEET_TRANSITION_ILLEGAL", "COST_TIMESHEET_UNKNOWN_WORK_PACKAGE", "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE", "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN", "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR", "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED", "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY", "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT", "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND", "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED", "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE", "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL", "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED", "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND", "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL", "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID", "PLATFORM_APPROVAL_CONCURRENT_CHANGE", "PLATFORM_APPROVAL_CUTOFF_PASSED", "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE", "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK", "PLATFORM_APPROVAL_DECISION_EMPTY", "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY", "PLATFORM_APPROVAL_HOOK_BLOCKED", "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED", "PLATFORM_APPROVAL_NOT_INITIATOR", "PLATFORM_APPROVAL_NO_ROUTE", "PLATFORM_APPROVAL_REASON_REQUIRED", "PLATFORM_APPROVAL_REQUEST_NOT_FOUND", "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED", "PLATFORM_APPROVAL_TASK_NOT_FOUND", "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID", "PLATFORM_APPROVAL_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_ACCOUNT_DISABLED", "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND", "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_CONCURRENT_UPDATE", "PLATFORM_IDENTITY_CREDENTIALS_INVALID", "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED", "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN", "PLATFORM_IDENTITY_NETWORK_DENIED", "PLATFORM_IDENTITY_PERMISSION_UNKNOWN", "PLATFORM_IDENTITY_ROLE_BLANK_NAME", "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE", "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME", "PLATFORM_IDENTITY_ROLE_INVALID_CODE", "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED", "PLATFORM_IDENTITY_ROLE_NOT_FOUND", "PLATFORM_IDENTITY_ROLE_RESERVED_CODE", "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN", "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_SSO_UNAVAILABLE", "PLATFORM_IDENTITY_STATE_INVALID", "PLATFORM_IDENTITY_TICKET_INVALID", "PLATFORM_IDENTITY_TICKET_REPLAYED", "PLATFORM_IDENTITY_WECOM_CODE_INVALID", "PLATFORM_IDENTITY_WECOM_NOT_BOUND", "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL", "PLATFORM_NUMBERING_CONCURRENT_UPDATE", "PLATFORM_NUMBERING_RULE_INVALID", "PLATFORM_NUMBERING_RULE_NOT_FOUND", "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL", "PLATFORM_WEB_ACCESS_DENIED", "PLATFORM_WEB_CORRELATION_INVALID", "PLATFORM_WEB_CURSOR_INVALID", "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED", "PLATFORM_WEB_REQUEST_INVALID", "PLATFORM_WEB_SOURCE_BROKEN", "PLATFORM_WEB_SOURCE_UNAVAILABLE", "PLATFORM_WEB_UNAUTHENTICATED", "PLATFORM_WEB_UNEXPECTED", "PLATFORM_WEB_VERSION_MISMATCH", "PLATFORM_WEB_VERSION_REQUIRED"]);

export const InboxCategorySchema = z.enum(["NOTICE", "TODO"]);

export const InboxItemResponseSchema = z.object({
  get category() { return InboxCategorySchema; },
  claimed: z.boolean(),
  createdAt: z.string().optional(),
  deadlineAt: z.string().optional(),
  detailUrl: z.string().optional(),
  documentId: z.string().optional(),
  nodeName: z.string().optional(),
  overdue: z.boolean(),
  get processKey() { return ProcessKeySchema.optional(); },
  get source() { return InboxSourceSchema; },
  taskId: z.string(),
  title: z.string().optional(),
});

export const InboxResponseSchema = z.object({
  get items() { return z.array(InboxItemResponseSchema); },
  legacyAvailable: z.boolean(),
  legacyCount: z.number().int(),
  newCount: z.number().int(),
  nextCursor: z.string().optional(),
  noticeCount: z.number().int(),
  totalCount: z.number().int(),
});

export const InboxSourceSchema = z.enum(["LEGACY", "NEW"]);

export const LegacyNumberResponseSchema = z.object({
  documentId: z.string(),
  documentType: z.string(),
  importBatch: z.string(),
  legacyNumber: z.string(),
  legalEntityId: z.string(),
});

export const LegalEntityResponseSchema = z.object({
  code: z.string(),
  legalEntityId: z.string(),
  name: z.string(),
  status: z.string(),
});

export const LoginStateRequestSchema = z.object({
  entry: z.enum(["WORKBENCH", "MINI_PROGRAM", "QR_CODE_LOGIN"]).optional(),
});

export const LoginStateResponseSchema = z.object({
  state: z.string(),
});

export const MaintainWorkCalendarRequestSchema = z.object({
  get days() { return z.array(CalendarDayRequestSchema); },
  lockedDaysConfirmed: z.boolean().optional(),
});

export const NavigationResponseSchema = z.object({
  get children() { return z.array(NavigationResponseSchema); },
  name: z.string(),
  routePath: z.string().optional(),
});

export const NumberingRuleRequestSchema = z.object({
  pattern: z.string().optional(),
  resetPolicy: z.string().optional(),
  startValue: z.number().int().optional(),
});

export const NumberingRuleResponseSchema = z.object({
  capacity: z.number().int(),
  confirmationStatus: z.string(),
  get documentType() { return DocumentTypeSchema; },
  documentTypeCode: z.string().optional(),
  pattern: z.string(),
  resetPolicy: z.string(),
  startValue: z.number().int(),
});

export const OrgUnitDetailResponseSchema = z.object({
  ancestorOrgUnitIds: z.array(z.string()),
  get orgUnit() { return OrgUnitResponseSchema; },
  orgUnitAndBelowIds: z.array(z.string()),
});

export const OrgUnitResponseSchema = z.object({
  code: z.string(),
  deliveryUnit: z.boolean(),
  headEmployeeId: z.string().optional(),
  name: z.string(),
  orgUnitId: z.string(),
  parentOrgUnitId: z.string().optional(),
  regionCategory: z.string().optional(),
  regionalReceivablesManagerId: z.string().optional(),
  shortName: z.string().optional(),
  sortOrder: z.number().int().optional(),
  status: z.string(),
});

export const ProblemDetailSchema = z.object({
  correlationId: z.string().optional(),
  detail: z.string().optional(),
  get errorCode() { return ErrorCodeSchema; },
  instance: z.string().optional(),
  legacyLoginUrl: z.string().optional(),
  status: z.number().int(),
  title: z.string().optional(),
  type: z.string().optional(),
});

export const ProcessKeySchema = z.enum(["COST_TIMESHEET_APPROVAL"]);

export const ReleaseResponseSchema = z.object({
  message: z.string().optional(),
  outcome: z.string(),
  get syncRun() { return SyncRunResponseSchema.optional(); },
});

export const ReplaceRolesRequestSchema = z.object({
  roleCodes: z.array(z.string()).optional(),
});

export const ReturnTaskRequestSchema = z.object({
  reason: z.string().optional(),
});

export const RoleRequestSchema = z.object({
  accessScope: z.enum(["ALL", "CUSTOM_ORG_UNITS", "OWN_ORG_UNIT", "OWN_ORG_UNIT_SUBTREE", "SELF", "SELF_AND_DIRECT_REPORTS"]).optional(),
  code: z.string().optional(),
  enabled: z.boolean().optional(),
  name: z.string().optional(),
  permissionCodes: z.array(z.string()).optional(),
});

export const RoleResponseSchema = z.object({
  accessScope: z.enum(["ALL", "CUSTOM_ORG_UNITS", "OWN_ORG_UNIT", "OWN_ORG_UNIT_SUBTREE", "SELF", "SELF_AND_DIRECT_REPORTS"]),
  administrative: z.boolean(),
  code: z.string(),
  enabled: z.boolean(),
  kind: z.string(),
  name: z.string(),
  permissionCodes: z.array(z.string()),
  roleId: z.string(),
});

export const SyncDiffResponseSchema = z.object({
  changeKind: z.string(),
  changedFields: z.array(z.string()),
  detail: z.string().optional(),
  entityKind: z.string(),
  issueKind: z.string().optional(),
  subjectKey: z.string(),
});

export const SyncRunDetailResponseSchema = z.object({
  get diffs() { return z.array(SyncDiffResponseSchema); },
  get syncRun() { return SyncRunResponseSchema; },
});

export const SyncRunResponseSchema = z.object({
  employedWithoutWecomCount: z.number().int().optional(),
  employeeChangeCount: z.number().int().optional(),
  failureReason: z.string().optional(),
  finishedAt: z.string().optional(),
  heldReason: z.string().optional(),
  incomingActiveOrgUnitCount: z.number().int().optional(),
  incomingEmployedCount: z.number().int().optional(),
  isolatedRowCount: z.number().int().optional(),
  issueCount: z.number().int().optional(),
  orgUnitChangeCount: z.number().int().optional(),
  positionChangeCount: z.number().int().optional(),
  previousActiveOrgUnitCount: z.number().int().optional(),
  previousEmployedCount: z.number().int().optional(),
  releasedAt: z.string().optional(),
  releasedBy: z.string().optional(),
  releasedByRunId: z.string().optional(),
  sourceEmployeeCount: z.number().int().optional(),
  sourceOrgUnitCount: z.number().int().optional(),
  startedAt: z.string(),
  status: z.string(),
  syncRunId: z.string(),
  trigger: z.string(),
});

export const TaskDetailResponseSchema = z.object({
  assignee: z.string().optional(),
  assigneeName: z.string().optional(),
  canAct: z.boolean(),
  candidateNames: z.array(z.string()),
  candidates: z.array(z.string()),
  createdAt: z.string(),
  deadlineAt: z.string().optional(),
  documentId: z.string(),
  get documentType() { return DocumentTypeSchema; },
  get history() { return z.array(TaskRecordResponseSchema); },
  initiator: z.string(),
  initiatorName: z.string(),
  initiatorOrgUnitName: z.string().optional(),
  instanceId: z.string(),
  nodeKey: z.string(),
  nodeName: z.string(),
  overdue: z.boolean(),
  get processKey() { return ProcessKeySchema; },
  summary: z.string().optional(),
  taskId: z.string(),
});

export const TaskRecordActionSchema = z.enum(["APPROVED", "AUTO_APPROVED", "REJECTED", "RETURNED", "TRANSFERRED"]);

export const TaskRecordPageResponseSchema = z.object({
  get items() { return z.array(TaskRecordResponseSchema); },
  nextCursor: z.string().optional(),
});

export const TaskRecordResponseSchema = z.object({
  get action() { return TaskRecordActionSchema; },
  actorId: z.string(),
  actorName: z.string(),
  decidedAt: z.string(),
  documentId: z.string(),
  get documentType() { return DocumentTypeSchema; },
  instanceId: z.string(),
  nodeKey: z.string(),
  nodeName: z.string(),
  onBehalfOfName: z.string().optional(),
  get processKey() { return ProcessKeySchema; },
  reason: z.string().optional(),
  recordId: z.string(),
  round: z.number().int(),
  taskId: z.string(),
});

export const TicketLoginRequestSchema = z.object({
  ticket: z.string().optional(),
});

export const TimesheetResponseSchema = z.object({
  approvalInstanceId: z.string().optional(),
  canWithdraw: z.boolean(),
  round: z.number().int(),
  status: z.string(),
  timesheetId: z.string(),
});

export const TransferTaskRequestSchema = z.object({
  assigneeId: z.number().int().optional(),
});

export const UpdateLegalEntityRequestSchema = z.object({
  code: z.string().optional(),
  name: z.string().optional(),
  status: z.string().optional(),
});

export const WecomLoginRequestSchema = z.object({
  code: z.string().optional(),
  entry: z.enum(["WORKBENCH", "MINI_PROGRAM", "QR_CODE_LOGIN"]).optional(),
  state: z.string().optional(),
});

export const WorkCalendarYearResponseSchema = z.object({
  calendarYear: z.number().int(),
  get days() { return z.array(CalendarDayResponseSchema); },
});

export const responseSchemas = {
  getCostTimesheetsById: TimesheetResponseSchema,
  getOrganizationLegalEntities: z.array(LegalEntityResponseSchema),
  postOrganizationLegalEntities: LegalEntityResponseSchema,
  putOrganizationLegalEntitiesByLegalEntityId: LegalEntityResponseSchema,
  getOrganizationOrgUnits: z.array(OrgUnitResponseSchema),
  getOrganizationOrgUnitsByOrgUnitId: OrgUnitDetailResponseSchema,
  getOrganizationSyncRuns: z.array(SyncRunResponseSchema),
  getOrganizationSyncRunsBySyncRunId: SyncRunDetailResponseSchema,
  postOrganizationSyncRunsBySyncRunIdRelease: ReleaseResponseSchema,
  getOrganizationWorkCalendars: z.array(z.number().int()),
  getOrganizationWorkCalendarsByCalendarYear: WorkCalendarYearResponseSchema,
  putOrganizationWorkCalendarsByCalendarYear: WorkCalendarYearResponseSchema,
  getPlatformApprovalTaskRecords: TaskRecordPageResponseSchema,
  getPlatformApprovalTasks: InboxResponseSchema,
  getPlatformApprovalTasksByTaskId: TaskDetailResponseSchema,
  postPlatformApprovalTasksByTaskIdDecide: DecisionResponseSchema,
  postPlatformApprovalTasksByTaskIdReturn: DecisionResponseSchema,
  getPlatformAuditEntries: AuditEntryPageResponseSchema,
  getPlatformLegacyNumbers: z.union([z.array(LegacyNumberResponseSchema), LegacyNumberResponseSchema]),
  postPlatformLoginStates: LoginStateResponseSchema,
  getPlatformNumberingRules: z.array(NumberingRuleResponseSchema),
  getPlatformNumberingRulesByDocumentType: NumberingRuleResponseSchema,
  getPlatformRoles: z.array(RoleResponseSchema),
  postPlatformRoles: z.unknown(),
  getPlatformRolesByRoleId: z.unknown(),
  putPlatformRolesByRoleId: z.unknown(),
  deletePlatformRolesByRoleId: z.unknown(),
  getPlatformSessionsCurrent: CurrentUserResponseSchema,
  postPlatformSessionsEmergencyLogin: z.unknown(),
  postPlatformSessionsSsoLogin: z.unknown(),
  postPlatformSessionsTicketLogin: z.unknown(),
  postPlatformSessionsWecomLogin: z.unknown(),
  postPlatformUserAccountsByUserIdDisable: z.unknown(),
  postPlatformUserAccountsByUserIdEnable: z.unknown(),
  postPlatformUserAccountsByUserIdReplaceRoles: z.unknown(),
} as const;
