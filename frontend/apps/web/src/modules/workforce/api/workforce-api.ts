import { DEFAULT_PAGE_LIMIT, checkedPage, collectPages, operations } from '@forcitis/api';

export { nextCursorOf } from '@forcitis/api';
import type { EmploymentFilter } from '../model/employee';
import type { EnterPersonnelCommandBody } from '../model/personnel-command';

export function fetchOrgUnits(includeRevoked: boolean) {
  return operations.getOrganizationOrgUnits({ query: { includeRevoked } });
}

export function fetchEmployeePage(employmentStatus: EmploymentFilter, orgUnitId: string | undefined, cursor: string | undefined) {
  return operations.getOrganizationEmployees({
    query: { employmentStatus, ...(orgUnitId === undefined ? {} : { orgUnitId }), ...(cursor === undefined ? {} : { cursor }) },
  }).then((page) => checkedPage(page, cursor));
}

export function fetchEmployee(employeeId: string) {
  return operations.getOrganizationEmployeesByEmployeeId({ path: { employeeId } });
}

export function fetchPrimaryPostHistory(employeeId: string) {
  return operations.getOrganizationEmployeesByEmployeeIdPrimaryPostHistory({ path: { employeeId } });
}

export interface PersonnelCommandFilter {
  status: string;
  commandKind: string;
  employeeId: string;
}

export function fetchPersonnelCommandPage(filter: PersonnelCommandFilter, cursor: string | undefined) {
  return operations.getOrganizationPersonnelCommands({
    query: {
      ...(filter.status === '' ? {} : { status: filter.status }),
      ...(filter.commandKind === '' ? {} : { commandKind: filter.commandKind }),
      ...(filter.employeeId === '' ? {} : { employeeId: filter.employeeId }),
      ...(cursor === undefined ? {} : { cursor }),
    },
  }).then((page) => checkedPage(page, cursor));
}

export function fetchPersonnelCommand(personnelCommandId: string) {
  return operations.getOrganizationPersonnelCommandsByPersonnelCommandId({ path: { personnelCommandId } });
}

export function enterPersonnelCommand(body: EnterPersonnelCommandBody) {
  return operations.postOrganizationPersonnelCommands({ body });
}

export async function withdrawPersonnelCommand(personnelCommandId: string, reason: string): Promise<void> {
  await operations.postOrganizationPersonnelCommandsByPersonnelCommandIdWithdraw({ path: { personnelCommandId }, body: { reason } });
}

/** Every employee, for choosing one: the form needs the whole short list, and an error instead of a silent cut when it is longer. */
export function fetchEmployeeChoices() {
  return collectPages((cursor) => operations.getOrganizationEmployees({ query: { employmentStatus: 'ALL', limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

export function fetchPositionChoices() {
  return collectPages((cursor) => operations.getOrganizationPositions({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}

export function fetchLegalEntityChoices() {
  return collectPages((cursor) => operations.getOrganizationLegalEntities({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
}
