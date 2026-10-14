import { describe, expect, it } from 'vitest';
import {
  businessToday,
  changedDates,
  effectiveKind,
  entriesOf,
  isLocked,
  isWeekend,
  kindChoices,
  monthsOf,
  withKind,
  yearChoices,
} from './work-calendar';

describe('日期的工作性质', () => {
  it('周一到周五是工作日，周末是休息日，例外按日历的设置', () => {
    const exceptions = new Map([['2020-10-01', 'HOLIDAY' as const], ['2020-10-10', 'MAKE_UP_WORKDAY' as const]]);
    expect(effectiveKind('2020-09-30', exceptions)).toBe('WORKDAY');
    expect(effectiveKind('2020-10-03', exceptions)).toBe('REST_DAY');
    expect(effectiveKind('2020-10-01', exceptions)).toBe('HOLIDAY');
    expect(effectiveKind('2020-10-10', exceptions)).toBe('MAKE_UP_WORKDAY');
    expect(isWeekend('2020-10-04')).toBe(true);
    expect(isWeekend('2020-10-05')).toBe(false);
  });

  it('调休上班日只能设在周末，节假日在任何一天都可以设', () => {
    expect(kindChoices('2020-10-03').map((choice) => choice.value)).toEqual(['', 'HOLIDAY', 'MAKE_UP_WORKDAY']);
    expect(kindChoices('2020-10-05').map((choice) => choice.value)).toEqual(['', 'HOLIDAY']);
  });
});

describe('一年的月历', () => {
  it('十二个月，每月的日数和第一天的位置正确（周一为一周的第一天）', () => {
    const months = monthsOf(2024);
    expect(months).toHaveLength(12);
    expect(months[1]?.dates).toHaveLength(29);
    expect(months[0]?.dates[0]).toBe('2024-01-01');
    expect(months[0]?.leadingBlanks).toBe(0);
    expect(months[8]?.leadingBlanks).toBe(6);
    expect(months.flatMap((month) => month.dates)).toHaveLength(366);
  });
});

describe('已锁定的日期', () => {
  it('今天之前的日期已锁定，今天和以后不锁定；今天按上海时间算', () => {
    expect(isLocked('2026-10-13', '2026-10-14')).toBe(true);
    expect(isLocked('2026-10-14', '2026-10-14')).toBe(false);
    expect(isLocked('2026-10-15', '2026-10-14')).toBe(false);
    expect(businessToday(new Date('2026-12-31T16:30:00Z'))).toBe('2027-01-01');
  });
});

describe('修改', () => {
  it('找出和已保存的日历不同的日期，改回原样就不算修改', () => {
    const saved = new Map([['2020-10-01', 'HOLIDAY' as const]]);
    const edited = withKind(withKind(saved, '2020-10-03', 'MAKE_UP_WORKDAY'), '2020-10-01', '');
    expect(changedDates(saved, edited)).toEqual(['2020-10-01', '2020-10-03']);
    expect(changedDates(saved, withKind(withKind(saved, '2020-10-02', 'HOLIDAY'), '2020-10-02', ''))).toEqual([]);
    expect(saved.size).toBe(1);
  });

  it('提交的日期按日期顺序排列', () => {
    expect(entriesOf(new Map([['2020-10-03', 'MAKE_UP_WORKDAY' as const], ['2020-10-01', 'HOLIDAY' as const]]))).toEqual([
      { calendarDate: '2020-10-01', dayKind: 'HOLIDAY' },
      { calendarDate: '2020-10-03', dayKind: 'MAKE_UP_WORKDAY' },
    ]);
  });

  it('年度可以选已维护的年份，另外总有下一年可以开始录入', () => {
    expect(yearChoices([2016, 2026], '2026-10-14')).toEqual([2016, 2026, 2027]);
    expect(yearChoices([2016, 2027], '2026-10-14')).toEqual([2016, 2027]);
  });
});
