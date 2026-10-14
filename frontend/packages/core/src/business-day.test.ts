import { describe, expect, it } from 'vitest';
import { businessToday } from './business-day';

describe('businessToday', () => {
  it('按上海时间算今天，不受浏览器时区影响', () => {
    expect(businessToday(new Date('2026-10-14T17:00:00Z'))).toBe('2026-10-15');
    expect(businessToday(new Date('2026-10-14T15:59:59Z'))).toBe('2026-10-14');
    expect(businessToday(new Date('2026-12-31T16:00:00Z'))).toBe('2027-01-01');
  });
});
