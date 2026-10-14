import { failureOf, isVersionConflict, useVersionedQuery } from '@forcitis/core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchMaintainedYears, fetchWorkCalendarYear, saveWorkCalendarYear } from '../api/attendance-api';
import type { DayEntry } from '../model/work-calendar';

const YEARS_KEY = ['attendance', 'years'] as const;
const yearKey = (calendarYear: number) => ['attendance', 'year', calendarYear] as const;

export function useMaintainedYears() {
  return useQuery({ queryKey: YEARS_KEY, queryFn: fetchMaintainedYears });
}

/** A year nobody has maintained yet answers 404; that is a blank calendar to start from, not a failure. */
export function useWorkCalendarYear(calendarYear: number) {
  return useVersionedQuery({
    queryKey: yearKey(calendarYear),
    gcTime: 0,
    queryFn: async () => {
      try {
        return await fetchWorkCalendarYear(calendarYear);
      } catch (error: unknown) {
        if (failureOf(error).code === 'ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND') return null;
        throw error;
      }
    },
  });
}

export function useSaveWorkCalendar(calendarYear: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ days, lockedDaysConfirmed, version, firstMaintenance }: { days: DayEntry[]; lockedDaysConfirmed: boolean; version: string | undefined; firstMaintenance: boolean }) => saveWorkCalendarYear(calendarYear, days, lockedDaysConfirmed, version, firstMaintenance),
    onError: (error) => {
      if (isVersionConflict(error)) void queryClient.invalidateQueries({ queryKey: yearKey(calendarYear) });
    },
    onSuccess: async () => {
      await Promise.all([queryClient.invalidateQueries({ queryKey: YEARS_KEY }), queryClient.invalidateQueries({ queryKey: yearKey(calendarYear) })]);
    },
  });
}
