// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
import { z } from 'zod';

export const AttributeLegalEntityRequestSchema = z.object({
  effectiveFrom: z.string().optional(),
  legalEntityId: z.number().int().optional(),
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
  onBehalfOfId: z.string().nullable().optional(),
  result: z.string().nullable().optional(),
  sourceAddress: z.string().nullable().optional(),
  subjectKey: z.string(),
  traceNumber: z.string().nullable().optional(),
});

export const CalendarDayRequestSchema = z.object({
  calendarDate: z.string().optional(),
  dayKind: z.enum(["HOLIDAY", "MAKE_UP_WORKDAY"]).optional(),
});

export const CalendarDayResponseSchema = z.object({
  calendarDate: z.string(),
  dayKind: z.enum(["HOLIDAY", "MAKE_UP_WORKDAY"]),
});

export const CorrectLegalEntityAttributionRequestSchema = z.object({
  effectiveTo: z.string().optional(),
  periodFrom: z.string().optional(),
  reason: z.string().optional(),
});

export const CreateLegalEntityRequestSchema = z.object({
  accountingOrganizationNumber: z.number().int().optional(),
  code: z.string().optional(),
  name: z.string().optional(),
});

export const CurrentUserResponseSchema = z.object({
  displayName: z.string(),
  identityConfirmed: z.boolean(),
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

export const CursorPageRoleResponseSchema = z.object({
  get items() { return z.array(RoleResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const CursorPageSyncRunResponseSchema = z.object({
  get items() { return z.array(SyncRunResponseSchema); },
  nextCursor: z.string().nullable().optional(),
});

export const DecideTaskRequestSchema = z.object({
  outcome: z.enum(["APPROVE", "REJECT"]).optional(),
  reason: z.string().optional(),
});

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

export const ErrorCodeSchema = z.enum(["COST_TIMESHEET_CONCURRENT_CHANGE", "COST_TIMESHEET_CUTOFF_PASSED", "COST_TIMESHEET_EXCEEDS_REMAINING_HOURS", "COST_TIMESHEET_NOT_FOUND", "COST_TIMESHEET_PROJECT_MANAGER_INVALID", "COST_TIMESHEET_REASON_REQUIRED", "COST_TIMESHEET_TRANSITION_ILLEGAL", "COST_TIMESHEET_UNKNOWN_WORK_PACKAGE", "ORGANIZATION_ATTENDANCE_DAY_DUPLICATE", "ORGANIZATION_ATTENDANCE_DAY_KIND_UNKNOWN", "ORGANIZATION_ATTENDANCE_DAY_OUTSIDE_YEAR", "ORGANIZATION_ATTENDANCE_LOCKED_DAY_FORBIDDEN", "ORGANIZATION_ATTENDANCE_LOCKED_DAY_UNCONFIRMED", "ORGANIZATION_ATTENDANCE_MAKE_UP_WORKDAY_ON_WEEKDAY", "ORGANIZATION_ATTENDANCE_YEAR_CONFLICT", "ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND", "ORGANIZATION_ATTENDANCE_YEAR_NOT_MAINTAINED", "ORGANIZATION_ATTENDANCE_YEAR_OUT_OF_RANGE", "ORGANIZATION_WORKFORCE_ACCOUNTING_NUMBER_INVALID", "ORGANIZATION_WORKFORCE_ACCOUNTING_NUMBER_TAKEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_FORBIDDEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_OVERLAP", "ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED", "ORGANIZATION_WORKFORCE_ATTRIBUTION_DATE_INVALID", "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_FOUND", "ORGANIZATION_WORKFORCE_ATTRIBUTION_NOT_OPEN", "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_CLOSED", "ORGANIZATION_WORKFORCE_ATTRIBUTION_PERIOD_NOT_FOUND", "ORGANIZATION_WORKFORCE_ATTRIBUTION_SUBJECT_NOT_FOUND", "ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE", "ORGANIZATION_WORKFORCE_EMPLOYEE_LEGAL_ENTITY_UNRESOLVED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_LOCKED", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NAME_INVALID", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_NOT_FOUND", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_STATUS_UNKNOWN", "ORGANIZATION_WORKFORCE_LEGAL_ENTITY_TRANSITION_ILLEGAL", "ORGANIZATION_WORKFORCE_ORG_UNIT_NOT_FOUND", "ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED", "ORGANIZATION_WORKFORCE_SYNC_RUN_APPLY_FAILED", "ORGANIZATION_WORKFORCE_SYNC_RUN_NOT_FOUND", "ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL", "PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID", "PLATFORM_APPROVAL_CONCURRENT_CHANGE", "PLATFORM_APPROVAL_CUTOFF_PASSED", "PLATFORM_APPROVAL_DECISION_CROSS_INSTANCE", "PLATFORM_APPROVAL_DECISION_DUPLICATE_TASK", "PLATFORM_APPROVAL_DECISION_EMPTY", "PLATFORM_APPROVAL_DECISION_SINGLE_ONLY", "PLATFORM_APPROVAL_HOOK_BLOCKED", "PLATFORM_APPROVAL_HOOK_NOT_REGISTERED", "PLATFORM_APPROVAL_NOT_INITIATOR", "PLATFORM_APPROVAL_NO_ROUTE", "PLATFORM_APPROVAL_REASON_REQUIRED", "PLATFORM_APPROVAL_REQUEST_NOT_FOUND", "PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED", "PLATFORM_APPROVAL_TASK_NOT_FOUND", "PLATFORM_APPROVAL_TRANSFER_TARGET_INVALID", "PLATFORM_APPROVAL_TRANSITION_ILLEGAL", "PLATFORM_AUDIT_QUERY_INVALID", "PLATFORM_IDENTITY_ACCOUNT_DISABLED", "PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND", "PLATFORM_IDENTITY_ACCOUNT_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_CONCURRENT_UPDATE", "PLATFORM_IDENTITY_CREDENTIALS_INVALID", "PLATFORM_IDENTITY_DEFAULT_ROLE_MISSING", "PLATFORM_IDENTITY_EMPLOYEE_SEPARATED", "PLATFORM_IDENTITY_LOGIN_NAME_UNKNOWN", "PLATFORM_IDENTITY_LOGIN_RATE_LIMITED", "PLATFORM_IDENTITY_NETWORK_DENIED", "PLATFORM_IDENTITY_NOT_SIGNED_IN", "PLATFORM_IDENTITY_PERMISSION_UNKNOWN", "PLATFORM_IDENTITY_ROLE_BLANK_NAME", "PLATFORM_IDENTITY_ROLE_DUPLICATE_CODE", "PLATFORM_IDENTITY_ROLE_DUPLICATE_NAME", "PLATFORM_IDENTITY_ROLE_INVALID_CODE", "PLATFORM_IDENTITY_ROLE_IN_USE", "PLATFORM_IDENTITY_ROLE_NOT_ALLOWED", "PLATFORM_IDENTITY_ROLE_NOT_FOUND", "PLATFORM_IDENTITY_ROLE_RESERVED_CODE", "PLATFORM_IDENTITY_ROLE_SYSTEM_FROZEN", "PLATFORM_IDENTITY_ROLE_TRANSITION_ILLEGAL", "PLATFORM_IDENTITY_SSO_UNAVAILABLE", "PLATFORM_IDENTITY_STATE_INVALID", "PLATFORM_IDENTITY_TICKET_INVALID", "PLATFORM_IDENTITY_TICKET_REPLAYED", "PLATFORM_IDENTITY_WECOM_CODE_INVALID", "PLATFORM_IDENTITY_WECOM_NOT_BOUND", "PLATFORM_INTEGRATION_CONCURRENT_UPDATE", "PLATFORM_INTEGRATION_DELIVERY_TRANSITION_ILLEGAL", "PLATFORM_NUMBERING_CONCURRENT_UPDATE", "PLATFORM_NUMBERING_LEGACY_NUMBER_NOT_FOUND", "PLATFORM_NUMBERING_LEGACY_NUMBER_TAKEN", "PLATFORM_NUMBERING_LEGACY_PREFIX_INVALID", "PLATFORM_NUMBERING_LEGAL_ENTITY_UNAVAILABLE", "PLATFORM_NUMBERING_RULE_INVALID", "PLATFORM_NUMBERING_RULE_NOT_FOUND", "PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL", "PLATFORM_WEB_ACCESS_DENIED", "PLATFORM_WEB_CORRELATION_INVALID", "PLATFORM_WEB_CURSOR_INVALID", "PLATFORM_WEB_IDEMPOTENCY_IN_PROGRESS", "PLATFORM_WEB_IDEMPOTENCY_KEY_REUSED", "PLATFORM_WEB_REQUEST_INVALID", "PLATFORM_WEB_SOURCE_BROKEN", "PLATFORM_WEB_SOURCE_UNAVAILABLE", "PLATFORM_WEB_UNAUTHENTICATED", "PLATFORM_WEB_UNEXPECTED", "PLATFORM_WEB_VERSION_MISMATCH", "PLATFORM_WEB_VERSION_REQUIRED"]);

export const InboxItemResponseSchema = z.object({
  claimed: z.boolean(),
  createdAt: z.string().nullable().optional(),
  detailUrl: z.string().nullable().optional(),
  nodeName: z.string().nullable().optional(),
  source: z.string(),
  taskId: z.string(),
  title: z.string(),
});

export const InboxResponseSchema = z.object({
  get items() { return z.array(InboxItemResponseSchema); },
  legacyAvailable: z.boolean(),
  legacyCount: z.number().int(),
  newCount: z.number().int(),
  totalCount: z.number().int(),
});

export const LegacyNumberResponseSchema = z.object({
  documentId: z.string(),
  documentType: z.enum(["QUOTE", "TIMESHEET", "JOURNAL_VOUCHER"]),
  importBatch: z.string(),
  legacyNumber: z.string(),
  legalEntityId: z.string().nullable().optional(),
});

export const LegalEntityAttributionResponseSchema = z.object({
  kind: z.string(),
  get periods() { return z.array(PeriodSchema); },
  subjectId: z.string(),
});

export const LegalEntityResponseSchema = z.object({
  accountingOrganizationNumber: z.number().int().nullable().optional(),
  code: z.string(),
  legalEntityId: z.string(),
  name: z.string(),
  status: z.string(),
});

export const LoginResponseSchema = z.object({
  absoluteExpiresAt: z.string(),
  administrationExpiresAt: z.string().nullable().optional(),
  identityConfirmationRequired: z.boolean(),
  userId: z.string(),
});

export const LoginStateRequestSchema = z.object({
  entry: z.enum(["WORKBENCH", "MINI_PROGRAM", "QR_CODE_LOGIN"]).optional(),
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
  pattern: z.string().optional(),
  resetPolicy: z.enum(["NEVER", "YEARLY", "MONTHLY", "DAILY"]).optional(),
  startValue: z.number().int().optional(),
});

export const NumberingRuleResponseSchema = z.object({
  capacity: z.number().int(),
  confirmationStatus: z.enum(["PENDING_CONFIRMATION", "CONFIRMED"]),
  documentType: z.enum(["QUOTE", "TIMESHEET", "JOURNAL_VOUCHER"]),
  documentTypeCode: z.string().nullable().optional(),
  pattern: z.string(),
  resetPolicy: z.enum(["NEVER", "YEARLY", "MONTHLY", "DAILY"]),
  startValue: z.number().int(),
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
  legacyLoginUrl: z.string().optional(),
  status: z.number().int(),
  title: z.string().optional(),
  type: z.string().optional(),
});

export const ReleaseSyncRunRequestSchema = z.object({
  reason: z.string().optional(),
});

export const ReplaceRolesRequestSchema = z.object({
  roleCodes: z.array(z.string()).optional(),
});

export const RetractTimesheetRequestSchema = z.object({
  reason: z.string().optional(),
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

export const SubmitTimesheetRequestSchema = z.object({
  hours: z.number().optional(),
  projectManagerId: z.number().int().optional(),
  workPackageId: z.number().int().optional(),
});

export const SubmitTimesheetResponseSchema = z.object({
  approvalInstanceId: z.string().nullable().optional(),
  errorCode: z.string().nullable().optional(),
  outcome: z.string(),
  remainingHours: z.number().nullable().optional(),
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
  sourceEmployeeCount: z.number().int().nullable().optional(),
  sourceOrgUnitCount: z.number().int().nullable().optional(),
  startedAt: z.string(),
  status: z.string(),
  syncRunId: z.string(),
  trigger: z.string(),
});

export const TaskDetailResponseSchema = z.object({
  assignee: z.string().nullable().optional(),
  canAct: z.boolean(),
  candidates: z.array(z.string()),
  createdAt: z.string(),
  documentId: z.string(),
  documentType: z.string(),
  initiator: z.string(),
  instanceId: z.string(),
  nodeKey: z.string(),
  nodeName: z.string(),
  processKey: z.string(),
  taskId: z.string(),
});

export const TicketLoginRequestSchema = z.object({
  ticket: z.string().optional(),
});

export const TimesheetResponseSchema = z.object({
  approvalInstanceId: z.string().nullable().optional(),
  canWithdraw: z.boolean(),
  round: z.number().int(),
  status: z.string(),
  timesheetId: z.string(),
});

export const TransferTaskRequestSchema = z.object({
  assigneeId: z.number().int().optional(),
});

export const UpdateLegalEntityRequestSchema = z.object({
  accountingOrganizationNumber: z.number().int().optional(),
  code: z.string().optional(),
  name: z.string().optional(),
  status: z.string().optional(),
});

export const UserAccountResponseSchema = z.object({
  roleCodes: z.array(z.string()),
  status: z.string(),
  userId: z.string(),
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
  postCostTimesheetsByIdSubmit: SubmitTimesheetResponseSchema,
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
  postOrganizationSyncRunsBySyncRunIdRelease: SyncRunResponseSchema,
  postOrganizationSyncRunsReconcile: OrgReconciliationResponseSchema,
  getOrganizationWorkCalendars: CursorPageMaintainedYearResponseSchema,
  getOrganizationWorkCalendarsByCalendarYear: WorkCalendarYearResponseSchema,
  putOrganizationWorkCalendarsByCalendarYear: WorkCalendarYearResponseSchema,
  getPlatformApprovalTasks: InboxResponseSchema,
  getPlatformApprovalTasksByTaskId: TaskDetailResponseSchema,
  postPlatformApprovalTasksByTaskIdDecide: DecisionResponseSchema,
  postPlatformApprovalTasksByTaskIdReturn: DecisionResponseSchema,
  getPlatformAuditEntries: CursorPageAuditEntryResponseSchema,
  getPlatformLegacyNumbers: z.union([CursorPageLegacyNumberResponseSchema, LegacyNumberResponseSchema]),
  postPlatformLoginStates: LoginStateResponseSchema,
  getPlatformNumberingRules: CursorPageNumberingRuleResponseSchema,
  getPlatformNumberingRulesByDocumentType: NumberingRuleResponseSchema,
  putPlatformNumberingRulesByDocumentType: NumberingRuleResponseSchema,
  getPlatformRoles: CursorPageRoleResponseSchema,
  postPlatformRoles: RoleResponseSchema,
  getPlatformRolesByRoleId: RoleResponseSchema,
  putPlatformRolesByRoleId: RoleResponseSchema,
  getPlatformSessionsCurrent: CurrentUserResponseSchema,
  postPlatformSessionsEmergencyLogin: LoginResponseSchema,
  postPlatformSessionsSsoLogin: LoginResponseSchema,
  postPlatformSessionsTicketLogin: LoginResponseSchema,
  postPlatformSessionsWecomLogin: LoginResponseSchema,
  getPlatformUserAccountsByUserId: UserAccountResponseSchema,
} as const;
