// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
import { type Call, type Result, request } from '../runtime';
import type { paths } from './schema';

export const postCostTimesheetsByIdSubmit = (...[args, options]: Call<paths['/api/v1/cost/timesheets/{id}:submit']['post']>) =>
  request<Result<paths['/api/v1/cost/timesheets/{id}:submit']['post']>>('post', '/api/v1/cost/timesheets/{id}:submit', args, options);

export const getOrganizationLegalEntities = (...[args, options]: Call<paths['/api/v1/organization/legal-entities']['get']>) =>
  request<Result<paths['/api/v1/organization/legal-entities']['get']>>('get', '/api/v1/organization/legal-entities', args, options);

export const postOrganizationLegalEntities = (...[args, options]: Call<paths['/api/v1/organization/legal-entities']['post']>) =>
  request<Result<paths['/api/v1/organization/legal-entities']['post']>>('post', '/api/v1/organization/legal-entities', args, options);

export const putOrganizationLegalEntitiesByLegalEntityId = (...[args, options]: Call<paths['/api/v1/organization/legal-entities/{legalEntityId}']['put']>) =>
  request<Result<paths['/api/v1/organization/legal-entities/{legalEntityId}']['put']>>('put', '/api/v1/organization/legal-entities/{legalEntityId}', args, options);

export const getOrganizationOrgUnits = (...[args, options]: Call<paths['/api/v1/organization/org-units']['get']>) =>
  request<Result<paths['/api/v1/organization/org-units']['get']>>('get', '/api/v1/organization/org-units', args, options);

export const getOrganizationOrgUnitsByOrgUnitId = (...[args, options]: Call<paths['/api/v1/organization/org-units/{orgUnitId}']['get']>) =>
  request<Result<paths['/api/v1/organization/org-units/{orgUnitId}']['get']>>('get', '/api/v1/organization/org-units/{orgUnitId}', args, options);

export const getOrganizationSyncRuns = (...[args, options]: Call<paths['/api/v1/organization/sync-runs']['get']>) =>
  request<Result<paths['/api/v1/organization/sync-runs']['get']>>('get', '/api/v1/organization/sync-runs', args, options);

export const getOrganizationSyncRunsBySyncRunId = (...[args, options]: Call<paths['/api/v1/organization/sync-runs/{syncRunId}']['get']>) =>
  request<Result<paths['/api/v1/organization/sync-runs/{syncRunId}']['get']>>('get', '/api/v1/organization/sync-runs/{syncRunId}', args, options);

export const postOrganizationSyncRunsBySyncRunIdRelease = (...[args, options]: Call<paths['/api/v1/organization/sync-runs/{syncRunId}:release']['post']>) =>
  request<Result<paths['/api/v1/organization/sync-runs/{syncRunId}:release']['post']>>('post', '/api/v1/organization/sync-runs/{syncRunId}:release', args, options);

export const getOrganizationWorkCalendars = (...[args, options]: Call<paths['/api/v1/organization/work-calendars']['get']>) =>
  request<Result<paths['/api/v1/organization/work-calendars']['get']>>('get', '/api/v1/organization/work-calendars', args, options);

export const getOrganizationWorkCalendarsByCalendarYear = (...[args, options]: Call<paths['/api/v1/organization/work-calendars/{calendarYear}']['get']>) =>
  request<Result<paths['/api/v1/organization/work-calendars/{calendarYear}']['get']>>('get', '/api/v1/organization/work-calendars/{calendarYear}', args, options);

export const putOrganizationWorkCalendarsByCalendarYear = (...[args, options]: Call<paths['/api/v1/organization/work-calendars/{calendarYear}']['put']>) =>
  request<Result<paths['/api/v1/organization/work-calendars/{calendarYear}']['put']>>('put', '/api/v1/organization/work-calendars/{calendarYear}', args, options);

export const getPlatformApprovalTasks = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks']['get']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks']['get']>>('get', '/api/v1/platform/approval-tasks', args, options);

export const getPlatformApprovalTasksByTaskId = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}']['get']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}']['get']>>('get', '/api/v1/platform/approval-tasks/{taskId}', args, options);

export const postPlatformApprovalTasksByTaskIdClaim = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:claim']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:claim']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:claim', args, options);

export const postPlatformApprovalTasksByTaskIdDecide = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:decide']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:decide']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:decide', args, options);

export const postPlatformApprovalTasksByTaskIdReturn = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:return']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:return']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:return', args, options);

export const getPlatformAuditEntries = (...[args, options]: Call<paths['/api/v1/platform/audit-entries']['get']>) =>
  request<Result<paths['/api/v1/platform/audit-entries']['get']>>('get', '/api/v1/platform/audit-entries', args, options);

export const getPlatformLegacyNumbers = (...[args, options]: Call<paths['/api/v1/platform/legacy-numbers']['get']>) =>
  request<Result<paths['/api/v1/platform/legacy-numbers']['get']>>('get', '/api/v1/platform/legacy-numbers', args, options);

export const postPlatformLoginStates = (...[args, options]: Call<paths['/api/v1/platform/login-states']['post']>) =>
  request<Result<paths['/api/v1/platform/login-states']['post']>>('post', '/api/v1/platform/login-states', args, options);

export const getPlatformNumberingRules = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules']['get']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules']['get']>>('get', '/api/v1/platform/numbering-rules', args, options);

export const getPlatformNumberingRulesByDocumentType = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules/{documentType}']['get']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules/{documentType}']['get']>>('get', '/api/v1/platform/numbering-rules/{documentType}', args, options);

export const putPlatformNumberingRulesByDocumentType = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules/{documentType}']['put']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules/{documentType}']['put']>>('put', '/api/v1/platform/numbering-rules/{documentType}', args, options);

export const getPlatformRoles = (...[args, options]: Call<paths['/api/v1/platform/roles']['get']>) =>
  request<Result<paths['/api/v1/platform/roles']['get']>>('get', '/api/v1/platform/roles', args, options);

export const postPlatformRoles = (...[args, options]: Call<paths['/api/v1/platform/roles']['post']>) =>
  request<Result<paths['/api/v1/platform/roles']['post']>>('post', '/api/v1/platform/roles', args, options);

export const getPlatformRolesByRoleId = (...[args, options]: Call<paths['/api/v1/platform/roles/{roleId}']['get']>) =>
  request<Result<paths['/api/v1/platform/roles/{roleId}']['get']>>('get', '/api/v1/platform/roles/{roleId}', args, options);

export const putPlatformRolesByRoleId = (...[args, options]: Call<paths['/api/v1/platform/roles/{roleId}']['put']>) =>
  request<Result<paths['/api/v1/platform/roles/{roleId}']['put']>>('put', '/api/v1/platform/roles/{roleId}', args, options);

export const deletePlatformRolesByRoleId = (...[args, options]: Call<paths['/api/v1/platform/roles/{roleId}']['delete']>) =>
  request<Result<paths['/api/v1/platform/roles/{roleId}']['delete']>>('delete', '/api/v1/platform/roles/{roleId}', args, options);

export const getPlatformSessionsCurrent = (...[args, options]: Call<paths['/api/v1/platform/sessions:current']['get']>) =>
  request<Result<paths['/api/v1/platform/sessions:current']['get']>>('get', '/api/v1/platform/sessions:current', args, options);

export const postPlatformSessionsEmergencyLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:emergency-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:emergency-login']['post']>>('post', '/api/v1/platform/sessions:emergency-login', args, options);

export const postPlatformSessionsLogout = (...[args, options]: Call<paths['/api/v1/platform/sessions:logout']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:logout']['post']>>('post', '/api/v1/platform/sessions:logout', args, options);

export const postPlatformSessionsSsoLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:sso-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:sso-login']['post']>>('post', '/api/v1/platform/sessions:sso-login', args, options);

export const postPlatformSessionsTicketLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:ticket-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:ticket-login']['post']>>('post', '/api/v1/platform/sessions:ticket-login', args, options);

export const postPlatformSessionsWecomLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:wecom-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:wecom-login']['post']>>('post', '/api/v1/platform/sessions:wecom-login', args, options);

export const postPlatformUserAccountsByUserIdDisable = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:disable']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:disable']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:disable', args, options);

export const postPlatformUserAccountsByUserIdEnable = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:enable']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:enable']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:enable', args, options);

export const postPlatformUserAccountsByUserIdReplaceRoles = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:replace-roles']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:replace-roles']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:replace-roles', args, options);
