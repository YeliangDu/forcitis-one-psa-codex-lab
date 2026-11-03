import { useQuery } from '@tanstack/react-query';
import { fetchOrgUnits } from '../api/workforce-api';

export function useOrgUnits(includeRevoked: boolean) {
  return useQuery({ queryKey: ['workforce', 'org-units', includeRevoked], queryFn: () => fetchOrgUnits(includeRevoked) });
}
