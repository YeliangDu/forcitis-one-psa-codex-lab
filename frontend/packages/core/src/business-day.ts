const BUSINESS_ZONE = 'Asia/Shanghai';

/** Dates of the business follow Shanghai time, whatever zone the browser is in. */
export function businessToday(now: Date): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: BUSINESS_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: string) => parts.find((candidate) => candidate.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}
