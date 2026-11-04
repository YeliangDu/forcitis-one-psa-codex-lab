import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  type PersonnelCommandFilter,
  enterPersonnelCommand,
  fetchEmployee,
  fetchEmployeeChoices,
  fetchEmployeePage,
  fetchLegalEntityChoices,
  fetchOrgUnits,
  fetchPersonnelCommand,
  fetchPersonnelCommandPage,
  fetchPositionChoices,
  nextCursorOf,
  withdrawPersonnelCommand,
} from '../api/workforce-api';
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

export function usePersonnelCommands(filter: PersonnelCommandFilter) {
  const query = useInfiniteQuery({
    queryKey: ['workforce', 'personnel-commands', filter.status, filter.commandKind, filter.employeeId],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchPersonnelCommandPage(filter, pageParam),
    getNextPageParam: nextCursorOf,
  });
  return { ...query, commands: query.data?.pages.flatMap((page) => page.items) };
}

export function usePersonnelCommand(personnelCommandId: string) {
  return useQuery({ queryKey: ['workforce', 'personnel-command', personnelCommandId], queryFn: () => fetchPersonnelCommand(personnelCommandId), enabled: personnelCommandId !== '' });
}

export function useEnterPersonnelCommand() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: enterPersonnelCommand,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workforce'] }),
  });
}

export function useWithdrawPersonnelCommand(personnelCommandId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (reason: string) => withdrawPersonnelCommand(personnelCommandId, reason),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['workforce'] }),
  });
}

export function useEmployeeChoices() {
  return useQuery({ queryKey: ['workforce', 'employee-choices'], queryFn: fetchEmployeeChoices });
}

export function usePositionChoices() {
  return useQuery({ queryKey: ['workforce', 'position-choices'], queryFn: fetchPositionChoices });
}

export function useLegalEntityChoices() {
  return useQuery({ queryKey: ['workforce', 'legal-entity-choices'], queryFn: fetchLegalEntityChoices });
}
