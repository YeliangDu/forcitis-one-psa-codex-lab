import { operations } from '@forcitis/api';
import type { DayEntry } from '../model/work-calendar';

export function fetchMaintainedYears() {
  return operations.getOrganizationWorkCalendars();
}

export function fetchWorkCalendarYear(calendarYear: number) {
  return operations.getOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear } });
}

/** `version` is versionOf(the year as read). A year that exists but came without a version is refused; only a year nobody maintained yet (`firstMaintenance`) is created. */
export function saveWorkCalendarYear(calendarYear: number, days: DayEntry[], lockedDaysConfirmed: boolean, version: string | undefined, firstMaintenance: boolean) {
  return operations.putOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear }, body: { days, lockedDaysConfirmed } }, { ifMatch: version, createOnly: firstMaintenance });
}
