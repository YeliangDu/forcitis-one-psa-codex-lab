import { operations } from '@forcitis/api';

export function fetchOrgUnits(includeRevoked: boolean) {
  return operations.getOrganizationOrgUnits({ query: { includeRevoked } });
}
