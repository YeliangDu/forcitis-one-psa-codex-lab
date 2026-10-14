import { operations } from '@forcitis/api';
import type { DayEntry } from '../model/work-calendar';

export function fetchMaintainedYears() {
  return operations.getOrganizationWorkCalendars();
}

export function fetchWorkCalendarYear(calendarYear: number) {
  return operations.getOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear } });
}

/** `version` is versionOf(the year as read); a year nobody maintained yet has none and is created instead. */
export function saveWorkCalendarYear(calendarYear: number, days: DayEntry[], lockedDaysConfirmed: boolean, version: string | undefined) {
  return operations.putOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear }, body: { days, lockedDaysConfirmed } }, { ifMatch: version, createOnly: version === undefined });
}
