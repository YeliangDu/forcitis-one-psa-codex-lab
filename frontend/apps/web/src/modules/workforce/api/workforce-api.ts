import { checkedPage, operations } from '@forcitis/api';

export { nextCursorOf } from '@forcitis/api';
import type { EmploymentFilter } from '../model/employee';

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
