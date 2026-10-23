// Generated from packages/api/openapi.json by scripts/generate-api.mjs. Do not edit by hand.
import { type Call, type Result, request } from '../runtime';
import type { paths } from './schema';
import { responseSchemas } from './validators';

export const getOrganizationEmployeeLegalEntitiesByEmployeeId = (...[args, options]: Call<paths['/api/v1/organization/employee-legal-entities/{employeeId}']['get']>) =>
  request<Result<paths['/api/v1/organization/employee-legal-entities/{employeeId}']['get']>>('get', '/api/v1/organization/employee-legal-entities/{employeeId}', args, options, responseSchemas.getOrganizationEmployeeLegalEntitiesByEmployeeId);

export const putOrganizationEmployeeLegalEntitiesByEmployeeId = (...[args, options]: Call<paths['/api/v1/organization/employee-legal-entities/{employeeId}']['put']>) =>
  request<Result<paths['/api/v1/organization/employee-legal-entities/{employeeId}']['put']>>('put', '/api/v1/organization/employee-legal-entities/{employeeId}', args, options, responseSchemas.putOrganizationEmployeeLegalEntitiesByEmployeeId, { versioned: true });

export const postOrganizationEmployeeLegalEntitiesByEmployeeIdCorrect = (...[args, options]: Call<paths['/api/v1/organization/employee-legal-entities/{employeeId}:correct']['post']>) =>
  request<Result<paths['/api/v1/organization/employee-legal-entities/{employeeId}:correct']['post']>>('post', '/api/v1/organization/employee-legal-entities/{employeeId}:correct', args, options, responseSchemas.postOrganizationEmployeeLegalEntitiesByEmployeeIdCorrect, { versioned: true, idempotent: true });

export const postOrganizationEmployeeLegalEntitiesByEmployeeIdEnd = (...[args, options]: Call<paths['/api/v1/organization/employee-legal-entities/{employeeId}:end']['post']>) =>
  request<Result<paths['/api/v1/organization/employee-legal-entities/{employeeId}:end']['post']>>('post', '/api/v1/organization/employee-legal-entities/{employeeId}:end', args, options, responseSchemas.postOrganizationEmployeeLegalEntitiesByEmployeeIdEnd, { versioned: true, idempotent: true });

export const getOrganizationLegalEntities = (...[args, options]: Call<paths['/api/v1/organization/legal-entities']['get']>) =>
  request<Result<paths['/api/v1/organization/legal-entities']['get']>>('get', '/api/v1/organization/legal-entities', args, options, responseSchemas.getOrganizationLegalEntities);

export const postOrganizationLegalEntities = (...[args, options]: Call<paths['/api/v1/organization/legal-entities']['post']>) =>
  request<Result<paths['/api/v1/organization/legal-entities']['post']>>('post', '/api/v1/organization/legal-entities', args, options, responseSchemas.postOrganizationLegalEntities, { idempotent: true });

export const getOrganizationLegalEntitiesByLegalEntityId = (...[args, options]: Call<paths['/api/v1/organization/legal-entities/{legalEntityId}']['get']>) =>
  request<Result<paths['/api/v1/organization/legal-entities/{legalEntityId}']['get']>>('get', '/api/v1/organization/legal-entities/{legalEntityId}', args, options, responseSchemas.getOrganizationLegalEntitiesByLegalEntityId);

export const putOrganizationLegalEntitiesByLegalEntityId = (...[args, options]: Call<paths['/api/v1/organization/legal-entities/{legalEntityId}']['put']>) =>
  request<Result<paths['/api/v1/organization/legal-entities/{legalEntityId}']['put']>>('put', '/api/v1/organization/legal-entities/{legalEntityId}', args, options, responseSchemas.putOrganizationLegalEntitiesByLegalEntityId, { versioned: true });

export const getOrganizationOrgUnitLegalEntitiesByOrgUnitId = (...[args, options]: Call<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}']['get']>) =>
  request<Result<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}']['get']>>('get', '/api/v1/organization/org-unit-legal-entities/{orgUnitId}', args, options, responseSchemas.getOrganizationOrgUnitLegalEntitiesByOrgUnitId);

export const putOrganizationOrgUnitLegalEntitiesByOrgUnitId = (...[args, options]: Call<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}']['put']>) =>
  request<Result<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}']['put']>>('put', '/api/v1/organization/org-unit-legal-entities/{orgUnitId}', args, options, responseSchemas.putOrganizationOrgUnitLegalEntitiesByOrgUnitId, { versioned: true });

export const postOrganizationOrgUnitLegalEntitiesByOrgUnitIdCorrect = (...[args, options]: Call<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}:correct']['post']>) =>
  request<Result<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}:correct']['post']>>('post', '/api/v1/organization/org-unit-legal-entities/{orgUnitId}:correct', args, options, responseSchemas.postOrganizationOrgUnitLegalEntitiesByOrgUnitIdCorrect, { versioned: true, idempotent: true });

export const postOrganizationOrgUnitLegalEntitiesByOrgUnitIdEnd = (...[args, options]: Call<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}:end']['post']>) =>
  request<Result<paths['/api/v1/organization/org-unit-legal-entities/{orgUnitId}:end']['post']>>('post', '/api/v1/organization/org-unit-legal-entities/{orgUnitId}:end', args, options, responseSchemas.postOrganizationOrgUnitLegalEntitiesByOrgUnitIdEnd, { versioned: true, idempotent: true });

export const getOrganizationOrgUnits = (...[args, options]: Call<paths['/api/v1/organization/org-units']['get']>) =>
  request<Result<paths['/api/v1/organization/org-units']['get']>>('get', '/api/v1/organization/org-units', args, options, responseSchemas.getOrganizationOrgUnits);

export const getOrganizationOrgUnitsByOrgUnitId = (...[args, options]: Call<paths['/api/v1/organization/org-units/{orgUnitId}']['get']>) =>
  request<Result<paths['/api/v1/organization/org-units/{orgUnitId}']['get']>>('get', '/api/v1/organization/org-units/{orgUnitId}', args, options, responseSchemas.getOrganizationOrgUnitsByOrgUnitId);

export const getOrganizationSyncRuns = (...[args, options]: Call<paths['/api/v1/organization/sync-runs']['get']>) =>
  request<Result<paths['/api/v1/organization/sync-runs']['get']>>('get', '/api/v1/organization/sync-runs', args, options, responseSchemas.getOrganizationSyncRuns);

export const getOrganizationSyncRunsBySyncRunId = (...[args, options]: Call<paths['/api/v1/organization/sync-runs/{syncRunId}']['get']>) =>
  request<Result<paths['/api/v1/organization/sync-runs/{syncRunId}']['get']>>('get', '/api/v1/organization/sync-runs/{syncRunId}', args, options, responseSchemas.getOrganizationSyncRunsBySyncRunId);

export const postOrganizationSyncRunsBySyncRunIdRelease = (...[args, options]: Call<paths['/api/v1/organization/sync-runs/{syncRunId}:release']['post']>) =>
  request<Result<paths['/api/v1/organization/sync-runs/{syncRunId}:release']['post']>>('post', '/api/v1/organization/sync-runs/{syncRunId}:release', args, options, responseSchemas.postOrganizationSyncRunsBySyncRunIdRelease);

export const postOrganizationSyncRunsReconcile = (...[args, options]: Call<paths['/api/v1/organization/sync-runs:reconcile']['post']>) =>
  request<Result<paths['/api/v1/organization/sync-runs:reconcile']['post']>>('post', '/api/v1/organization/sync-runs:reconcile', args, options, responseSchemas.postOrganizationSyncRunsReconcile);

export const getOrganizationWorkCalendars = (...[args, options]: Call<paths['/api/v1/organization/work-calendars']['get']>) =>
  request<Result<paths['/api/v1/organization/work-calendars']['get']>>('get', '/api/v1/organization/work-calendars', args, options, responseSchemas.getOrganizationWorkCalendars);

export const getOrganizationWorkCalendarsByCalendarYear = (...[args, options]: Call<paths['/api/v1/organization/work-calendars/{calendarYear}']['get']>) =>
  request<Result<paths['/api/v1/organization/work-calendars/{calendarYear}']['get']>>('get', '/api/v1/organization/work-calendars/{calendarYear}', args, options, responseSchemas.getOrganizationWorkCalendarsByCalendarYear);

export const putOrganizationWorkCalendarsByCalendarYear = (...[args, options]: Call<paths['/api/v1/organization/work-calendars/{calendarYear}']['put']>) =>
  request<Result<paths['/api/v1/organization/work-calendars/{calendarYear}']['put']>>('put', '/api/v1/organization/work-calendars/{calendarYear}', args, options, responseSchemas.putOrganizationWorkCalendarsByCalendarYear, { versioned: true });

export const getPlatformApprovalTaskOverviews = (...[args, options]: Call<paths['/api/v1/platform/approval-task-overviews']['get']>) =>
  request<Result<paths['/api/v1/platform/approval-task-overviews']['get']>>('get', '/api/v1/platform/approval-task-overviews', args, options, responseSchemas.getPlatformApprovalTaskOverviews);

export const getPlatformApprovalTaskRecords = (...[args, options]: Call<paths['/api/v1/platform/approval-task-records']['get']>) =>
  request<Result<paths['/api/v1/platform/approval-task-records']['get']>>('get', '/api/v1/platform/approval-task-records', args, options, responseSchemas.getPlatformApprovalTaskRecords);

export const getPlatformApprovalTasks = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks']['get']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks']['get']>>('get', '/api/v1/platform/approval-tasks', args, options, responseSchemas.getPlatformApprovalTasks);

export const getPlatformApprovalTasksByTaskId = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}']['get']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}']['get']>>('get', '/api/v1/platform/approval-tasks/{taskId}', args, options, responseSchemas.getPlatformApprovalTasksByTaskId);

export const postPlatformApprovalTasksByTaskIdClaim = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:claim']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:claim']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:claim', args, options, undefined, { versioned: true, idempotent: true });

export const postPlatformApprovalTasksByTaskIdDecide = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:decide']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:decide']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:decide', args, options, responseSchemas.postPlatformApprovalTasksByTaskIdDecide, { versioned: true, idempotent: true });

export const postPlatformApprovalTasksByTaskIdReassign = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:reassign']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:reassign']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:reassign', args, options, undefined, { versioned: true, idempotent: true });

export const postPlatformApprovalTasksByTaskIdRelease = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:release']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:release']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:release', args, options, undefined, { versioned: true, idempotent: true });

export const postPlatformApprovalTasksByTaskIdReturn = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:return']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:return']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:return', args, options, responseSchemas.postPlatformApprovalTasksByTaskIdReturn, { versioned: true, idempotent: true });

export const postPlatformApprovalTasksByTaskIdTransfer = (...[args, options]: Call<paths['/api/v1/platform/approval-tasks/{taskId}:transfer']['post']>) =>
  request<Result<paths['/api/v1/platform/approval-tasks/{taskId}:transfer']['post']>>('post', '/api/v1/platform/approval-tasks/{taskId}:transfer', args, options, undefined, { versioned: true, idempotent: true });

export const getPlatformAuditEntries = (...[args, options]: Call<paths['/api/v1/platform/audit-entries']['get']>) =>
  request<Result<paths['/api/v1/platform/audit-entries']['get']>>('get', '/api/v1/platform/audit-entries', args, options, responseSchemas.getPlatformAuditEntries);

export const getPlatformAuditEntriesExport = (...[args, options]: Call<paths['/api/v1/platform/audit-entries:export']['get']>) =>
  request<Result<paths['/api/v1/platform/audit-entries:export']['get']>>('get', '/api/v1/platform/audit-entries:export', args, options, responseSchemas.getPlatformAuditEntriesExport);

export const getPlatformAuthorizationRequests = (...[args, options]: Call<paths['/api/v1/platform/authorization-requests']['get']>) =>
  request<Result<paths['/api/v1/platform/authorization-requests']['get']>>('get', '/api/v1/platform/authorization-requests', args, options, responseSchemas.getPlatformAuthorizationRequests);

export const postPlatformAuthorizationRequestsByRequestIdConfirm = (...[args, options]: Call<paths['/api/v1/platform/authorization-requests/{requestId}:confirm']['post']>) =>
  request<Result<paths['/api/v1/platform/authorization-requests/{requestId}:confirm']['post']>>('post', '/api/v1/platform/authorization-requests/{requestId}:confirm', args, options, undefined, { idempotent: true });

export const postPlatformAuthorizationRequestsByRequestIdReject = (...[args, options]: Call<paths['/api/v1/platform/authorization-requests/{requestId}:reject']['post']>) =>
  request<Result<paths['/api/v1/platform/authorization-requests/{requestId}:reject']['post']>>('post', '/api/v1/platform/authorization-requests/{requestId}:reject', args, options, undefined, { idempotent: true });

export const postPlatformFiles = (...[args, options]: Call<paths['/api/v1/platform/files']['post']>) =>
  request<Result<paths['/api/v1/platform/files']['post']>>('post', '/api/v1/platform/files', args, options, responseSchemas.postPlatformFiles, { idempotent: true });

export const getPlatformFilesByFileId = (...[args, options]: Call<paths['/api/v1/platform/files/{fileId}']['get']>) =>
  request<Result<paths['/api/v1/platform/files/{fileId}']['get']>>('get', '/api/v1/platform/files/{fileId}', args, options, responseSchemas.getPlatformFilesByFileId);

export const getPlatformLegacyNumbers = (...[args, options]: Call<paths['/api/v1/platform/legacy-numbers']['get']>) =>
  request<Result<paths['/api/v1/platform/legacy-numbers']['get']>>('get', '/api/v1/platform/legacy-numbers', args, options, responseSchemas.getPlatformLegacyNumbers);

export const getPlatformLegacyNumbersResolve = (...[args, options]: Call<paths['/api/v1/platform/legacy-numbers:resolve']['get']>) =>
  request<Result<paths['/api/v1/platform/legacy-numbers:resolve']['get']>>('get', '/api/v1/platform/legacy-numbers:resolve', args, options, responseSchemas.getPlatformLegacyNumbersResolve);

export const postPlatformLoginStates = (...[args, options]: Call<paths['/api/v1/platform/login-states']['post']>) =>
  request<Result<paths['/api/v1/platform/login-states']['post']>>('post', '/api/v1/platform/login-states', args, options, responseSchemas.postPlatformLoginStates);

export const getPlatformNumberingRules = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules']['get']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules']['get']>>('get', '/api/v1/platform/numbering-rules', args, options, responseSchemas.getPlatformNumberingRules);

export const getPlatformNumberingRulesByDocumentType = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules/{documentType}']['get']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules/{documentType}']['get']>>('get', '/api/v1/platform/numbering-rules/{documentType}', args, options, responseSchemas.getPlatformNumberingRulesByDocumentType);

export const putPlatformNumberingRulesByDocumentType = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules/{documentType}']['put']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules/{documentType}']['put']>>('put', '/api/v1/platform/numbering-rules/{documentType}', args, options, responseSchemas.putPlatformNumberingRulesByDocumentType, { versioned: true });

export const postPlatformNumberingRulesByDocumentTypeConfirm = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules/{documentType}:confirm']['post']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules/{documentType}:confirm']['post']>>('post', '/api/v1/platform/numbering-rules/{documentType}:confirm', args, options, responseSchemas.postPlatformNumberingRulesByDocumentTypeConfirm, { idempotent: true });

export const postPlatformNumberingRulesByDocumentTypeConfirmFinance = (...[args, options]: Call<paths['/api/v1/platform/numbering-rules/{documentType}:confirm-finance']['post']>) =>
  request<Result<paths['/api/v1/platform/numbering-rules/{documentType}:confirm-finance']['post']>>('post', '/api/v1/platform/numbering-rules/{documentType}:confirm-finance', args, options, responseSchemas.postPlatformNumberingRulesByDocumentTypeConfirmFinance, { idempotent: true });

export const getPlatformOwnLoginRecords = (...[args, options]: Call<paths['/api/v1/platform/own-login-records']['get']>) =>
  request<Result<paths['/api/v1/platform/own-login-records']['get']>>('get', '/api/v1/platform/own-login-records', args, options, responseSchemas.getPlatformOwnLoginRecords);

export const getPlatformRoles = (...[args, options]: Call<paths['/api/v1/platform/roles']['get']>) =>
  request<Result<paths['/api/v1/platform/roles']['get']>>('get', '/api/v1/platform/roles', args, options, responseSchemas.getPlatformRoles);

export const postPlatformRoles = (...[args, options]: Call<paths['/api/v1/platform/roles']['post']>) =>
  request<Result<paths['/api/v1/platform/roles']['post']>>('post', '/api/v1/platform/roles', args, options, responseSchemas.postPlatformRoles, { idempotent: true });

export const getPlatformRolesByRoleId = (...[args, options]: Call<paths['/api/v1/platform/roles/{roleId}']['get']>) =>
  request<Result<paths['/api/v1/platform/roles/{roleId}']['get']>>('get', '/api/v1/platform/roles/{roleId}', args, options, responseSchemas.getPlatformRolesByRoleId);

export const putPlatformRolesByRoleId = (...[args, options]: Call<paths['/api/v1/platform/roles/{roleId}']['put']>) =>
  request<Result<paths['/api/v1/platform/roles/{roleId}']['put']>>('put', '/api/v1/platform/roles/{roleId}', args, options, responseSchemas.putPlatformRolesByRoleId, { versioned: true });

export const deletePlatformRolesByRoleId = (...[args, options]: Call<paths['/api/v1/platform/roles/{roleId}']['delete']>) =>
  request<Result<paths['/api/v1/platform/roles/{roleId}']['delete']>>('delete', '/api/v1/platform/roles/{roleId}', args, options, undefined, { versioned: true });

export const postPlatformSessionsConfirmIdentity = (...[args, options]: Call<paths['/api/v1/platform/sessions:confirm-identity']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:confirm-identity']['post']>>('post', '/api/v1/platform/sessions:confirm-identity', args, options, undefined);

export const getPlatformSessionsCurrent = (...[args, options]: Call<paths['/api/v1/platform/sessions:current']['get']>) =>
  request<Result<paths['/api/v1/platform/sessions:current']['get']>>('get', '/api/v1/platform/sessions:current', args, options, responseSchemas.getPlatformSessionsCurrent);

export const postPlatformSessionsEmergencyLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:emergency-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:emergency-login']['post']>>('post', '/api/v1/platform/sessions:emergency-login', args, options, responseSchemas.postPlatformSessionsEmergencyLogin);

export const postPlatformSessionsEnterAdministratorMode = (...[args, options]: Call<paths['/api/v1/platform/sessions:enter-administrator-mode']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:enter-administrator-mode']['post']>>('post', '/api/v1/platform/sessions:enter-administrator-mode', args, options, responseSchemas.postPlatformSessionsEnterAdministratorMode);

export const postPlatformSessionsExitAdministratorMode = (...[args, options]: Call<paths['/api/v1/platform/sessions:exit-administrator-mode']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:exit-administrator-mode']['post']>>('post', '/api/v1/platform/sessions:exit-administrator-mode', args, options, undefined);

export const postPlatformSessionsLogout = (...[args, options]: Call<paths['/api/v1/platform/sessions:logout']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:logout']['post']>>('post', '/api/v1/platform/sessions:logout', args, options, undefined);

export const postPlatformSessionsSsoLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:sso-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:sso-login']['post']>>('post', '/api/v1/platform/sessions:sso-login', args, options, responseSchemas.postPlatformSessionsSsoLogin);

export const postPlatformSessionsTicketLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:ticket-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:ticket-login']['post']>>('post', '/api/v1/platform/sessions:ticket-login', args, options, responseSchemas.postPlatformSessionsTicketLogin);

export const postPlatformSessionsWecomLogin = (...[args, options]: Call<paths['/api/v1/platform/sessions:wecom-login']['post']>) =>
  request<Result<paths['/api/v1/platform/sessions:wecom-login']['post']>>('post', '/api/v1/platform/sessions:wecom-login', args, options, responseSchemas.postPlatformSessionsWecomLogin);

export const getPlatformSourceReferences = (...[args, options]: Call<paths['/api/v1/platform/source-references']['get']>) =>
  request<Result<paths['/api/v1/platform/source-references']['get']>>('get', '/api/v1/platform/source-references', args, options, responseSchemas.getPlatformSourceReferences);

export const postPlatformSourceReferences = (...[args, options]: Call<paths['/api/v1/platform/source-references']['post']>) =>
  request<Result<paths['/api/v1/platform/source-references']['post']>>('post', '/api/v1/platform/source-references', args, options, responseSchemas.postPlatformSourceReferences);

export const postPlatformSourceReferencesBySourceReferenceIdConfirm = (...[args, options]: Call<paths['/api/v1/platform/source-references/{sourceReferenceId}:confirm']['post']>) =>
  request<Result<paths['/api/v1/platform/source-references/{sourceReferenceId}:confirm']['post']>>('post', '/api/v1/platform/source-references/{sourceReferenceId}:confirm', args, options, responseSchemas.postPlatformSourceReferencesBySourceReferenceIdConfirm);

export const postPlatformSourceReferencesBySourceReferenceIdReject = (...[args, options]: Call<paths['/api/v1/platform/source-references/{sourceReferenceId}:reject']['post']>) =>
  request<Result<paths['/api/v1/platform/source-references/{sourceReferenceId}:reject']['post']>>('post', '/api/v1/platform/source-references/{sourceReferenceId}:reject', args, options, responseSchemas.postPlatformSourceReferencesBySourceReferenceIdReject);

export const getPlatformUserAccountsByUserId = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}']['get']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}']['get']>>('get', '/api/v1/platform/user-accounts/{userId}', args, options, responseSchemas.getPlatformUserAccountsByUserId);

export const postPlatformUserAccountsByUserIdDisable = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:disable']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:disable']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:disable', args, options, undefined, { versioned: true, idempotent: true });

export const postPlatformUserAccountsByUserIdEnable = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:enable']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:enable']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:enable', args, options, undefined, { versioned: true, idempotent: true });

export const postPlatformUserAccountsByUserIdForceLogout = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:force-logout']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:force-logout']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:force-logout', args, options, undefined, { idempotent: true });

export const postPlatformUserAccountsByUserIdReplaceRoles = (...[args, options]: Call<paths['/api/v1/platform/user-accounts/{userId}:replace-roles']['post']>) =>
  request<Result<paths['/api/v1/platform/user-accounts/{userId}:replace-roles']['post']>>('post', '/api/v1/platform/user-accounts/{userId}:replace-roles', args, options, responseSchemas.postPlatformUserAccountsByUserIdReplaceRoles, { versioned: true, idempotent: true });
