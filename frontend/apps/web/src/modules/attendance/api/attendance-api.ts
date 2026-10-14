import { DEFAULT_PAGE_LIMIT, collectPages, operations } from '@forcitis/api';
import type { DayEntry } from '../model/work-calendar';

export async function fetchMaintainedYears(): Promise<number[]> {
  const years = await collectPages((cursor) => operations.getOrganizationWorkCalendars({ query: { limit: DEFAULT_PAGE_LIMIT, ...(cursor === undefined ? {} : { cursor }) } }));
  return years.map((year) => year.calendarYear);
}

export function fetchWorkCalendarYear(calendarYear: number) {
  return operations.getOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear } });
}

/** `version` is versionOf(the year as read). A year that exists but came without a version is refused; only a year nobody maintained yet (`firstMaintenance`) is created, with If-Match "0" (B3). */
export function saveWorkCalendarYear(calendarYear: number, days: DayEntry[], lockedDaysConfirmed: boolean, version: string | undefined, firstMaintenance: boolean) {
  return operations.putOrganizationWorkCalendarsByCalendarYear({ path: { calendarYear }, body: { days, lockedDaysConfirmed } }, { ifMatch: version, createOnly: firstMaintenance });
}
