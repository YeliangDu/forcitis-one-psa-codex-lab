import { operations } from '@forcitis/api';
import type { DayEntry } from '../model/work-calendar';

export function fetchMaintainedYears() {
  return operations.getOrganizationWorkCalendars();
}

export function fetchWorkCalendarYear(calendarYear: number) {
  return operations.getOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear } });
}

export function saveWorkCalendarYear(calendarYear: number, days: DayEntry[], lockedDaysConfirmed: boolean, firstMaintenance: boolean) {
  return operations.putOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear }, body: { days, lockedDaysConfirmed } }, { createOnly: firstMaintenance });
}
