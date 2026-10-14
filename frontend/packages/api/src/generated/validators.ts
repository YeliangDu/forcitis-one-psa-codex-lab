// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
import { z } from 'zod';

export const CalendarDayRequestSchema = z.object({
  calendarDate: z.string().optional(),
  dayKind: z.string().optional(),
});

export const CalendarDayResponseSchema = z.object({
  calendarDate: z.string(),
  dayKind: z.string(),
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

export const InboxItemResponseSchema = z.object({
  claimed: z.boolean(),
  createdAt: z.string().optional(),
  detailUrl: z.string().optional(),
  nodeName: z.string().optional(),
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

export const MaintainWorkCalendarRequestSchema = z.object({
  get days() { return z.array(CalendarDayRequestSchema).optional(); },
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
  documentType: z.string(),
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

export const ReleaseResponseSchema = z.object({
  message: z.string().optional(),
  outcome: z.string(),
  get syncRun() { return SyncRunResponseSchema.optional(); },
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
  approvalInstanceId: z.string().optional(),
  errorCode: z.string().optional(),
  outcome: z.string(),
  remainingHours: z.number().optional(),
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
  postCostTimesheetsByIdSubmit: SubmitTimesheetResponseSchema,
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
  getPlatformApprovalTasks: InboxResponseSchema,
  getPlatformApprovalTasksByTaskId: TaskDetailResponseSchema,
  postPlatformApprovalTasksByTaskIdDecide: DecisionResponseSchema,
  postPlatformApprovalTasksByTaskIdReturn: DecisionResponseSchema,
  getPlatformAuditEntries: z.unknown(),
  getPlatformLegacyNumbers: z.union([z.array(LegacyNumberResponseSchema), LegacyNumberResponseSchema]),
  postPlatformLoginStates: z.unknown(),
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
