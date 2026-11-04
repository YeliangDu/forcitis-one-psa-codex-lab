import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { fetchEmployee, fetchEmployeePage, fetchOrgUnits, nextCursorOf } from '../api/workforce-api';
import type { EmploymentFilter } from '../model/employee';

export function useOrgUnits(includeRevoked: boolean) {
  return useQuery({ queryKey: ['workforce', 'org-units', includeRevoked], queryFn: () => fetchOrgUnits(includeRevoked) });
}

export function useEmployees(employmentStatus: EmploymentFilter, orgUnitId: string | undefined) {
  const query = useInfiniteQuery({
    queryKey: ['workforce', 'employees', employmentStatus, orgUnitId ?? null],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchEmployeePage(employmentStatus, orgUnitId, pageParam),
    getNextPageParam: nextCursorOf,
  });
  return { ...query, employees: query.data?.pages.flatMap((page) => page.items) };
}

export function useEmployee(employeeId: string) {
  return useQuery({ queryKey: ['workforce', 'employee', employeeId], queryFn: () => fetchEmployee(employeeId), enabled: employeeId !== '' });
}
