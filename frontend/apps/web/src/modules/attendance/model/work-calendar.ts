import type { Schema } from '@forcitis/api';

export const WORK_CALENDAR_UPDATE_PERMISSION = 'organization.attendance:work-calendar:update';

export type DayKind = Schema<'DayKind'>;
export type CalendarDay = Schema<'CalendarDayResponse'>;
export type WorkCalendarYear = Schema<'WorkCalendarYearResponse'>;
export type DayEntry = Required<Schema<'CalendarDayRequest'>>;

/** What a date is for the business: the two exceptions of the calendar, otherwise Monday to Friday work and the weekend rests. */
export type EffectiveKind = DayKind | 'WORKDAY' | 'REST_DAY';

export const EFFECTIVE_KIND_LABELS: Readonly<Record<EffectiveKind, string>> = {
  HOLIDAY: '节假日',
  MAKE_UP_WORKDAY: '调休上班日',
  REST_DAY: '休息日',
  WORKDAY: '工作日',
};

export const WEEKDAY_HEADERS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const;

const BUSINESS_ZONE = 'Asia/Shanghai';

/** Exceptions of the year by ISO date. */
export type Exceptions = ReadonlyMap<string, DayKind>;

export function exceptionsOf(year: WorkCalendarYear | null): Map<string, DayKind> {
  return new Map((year?.days ?? []).map((day) => [day.calendarDate, day.dayKind]));
}

function isoDate(date: Date): string {
  return date.toISOString().slice(0, 10);
}

/** 0 = Monday ... 6 = Sunday. */
function mondayBasedWeekday(date: Date): number {
  return (date.getUTCDay() + 6) % 7;
}

export function isWeekend(date: string): boolean {
  return mondayBasedWeekday(new Date(`${date}T00:00:00Z`)) >= 5;
}

export function effectiveKind(date: string, exceptions: Exceptions): EffectiveKind {
  return exceptions.get(date) ?? (isWeekend(date) ? 'REST_DAY' : 'WORKDAY');
}

export interface MonthGrid {
  month: string;
  leadingBlanks: number;
  dates: string[];
}

export function monthsOf(year: number): MonthGrid[] {
  return Array.from({ length: 12 }, (_, index) => {
    const first = new Date(Date.UTC(year, index, 1));
    const dates: string[] = [];
    for (let day = new Date(first); day.getUTCMonth() === index; day.setUTCDate(day.getUTCDate() + 1)) dates.push(isoDate(day));
    return { month: `${year}-${String(index + 1).padStart(2, '0')}`, leadingBlanks: mondayBasedWeekday(first), dates };
  });
}

export function businessToday(now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: BUSINESS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: string) => parts.find((candidate) => candidate.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** A date before today is locked: the organization data administrator may still change it, but only after confirming (T2). */
export function isLocked(date: string, today: string): boolean {
  return date < today;
}

/** The kinds a date can be set to: the default for its weekday, a holiday, or (on a weekend only) a make-up workday. */
export function kindChoices(date: string): { value: DayKind | ''; label: string }[] {
  return [
    { value: '', label: EFFECTIVE_KIND_LABELS[isWeekend(date) ? 'REST_DAY' : 'WORKDAY'] },
    { value: 'HOLIDAY', label: EFFECTIVE_KIND_LABELS.HOLIDAY },
    ...(isWeekend(date) ? [{ value: 'MAKE_UP_WORKDAY' as const, label: EFFECTIVE_KIND_LABELS.MAKE_UP_WORKDAY }] : []),
  ];
}

export function withKind(exceptions: Exceptions, date: string, kind: DayKind | ''): Map<string, DayKind> {
  const next = new Map(exceptions);
  if (kind === '') next.delete(date);
  else next.set(date, kind);
  return next;
}

/** Dates whose exception differs between the saved calendar and the draft, in date order. */
export function changedDates(saved: Exceptions, draft: Exceptions): string[] {
  const dates = new Set([...saved.keys(), ...draft.keys()]);
  return [...dates].filter((date) => saved.get(date) !== draft.get(date)).sort();
}

export function entriesOf(exceptions: Exceptions): DayEntry[] {
  return [...exceptions.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([calendarDate, dayKind]) => ({ calendarDate, dayKind }));
}

export function yearChoices(maintained: readonly number[], today: string): number[] {
  const next = Number(today.slice(0, 4)) + 1;
  return [...new Set([...maintained, next])].sort((a, b) => a - b);
}
