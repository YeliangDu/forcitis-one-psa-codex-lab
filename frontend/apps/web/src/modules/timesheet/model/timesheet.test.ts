import { describe, expect, it } from 'vitest';
import { EMPTY_DRAFT, businessToday, draftProblems, isEditable, submitBodyOf } from './timesheet';

const VALID = { workDate: '2026-10-13', workPackageId: '12', hours: '7.5', projectManagerId: '1001' };

describe('businessToday', () => {
  it('按上海时间算今天，不受浏览器时区影响', () => {
    expect(businessToday(new Date('2026-10-14T17:00:00Z'))).toBe('2026-10-15');
    expect(businessToday(new Date('2026-10-14T15:59:59Z'))).toBe('2026-10-14');
    expect(businessToday(new Date('2026-12-31T16:00:00Z'))).toBe('2027-01-01');
  });
});

describe('draftProblems', () => {
  it('工作日期必填，不得晚于今天；今天本身可以', () => {
    expect(draftProblems({ ...VALID, workDate: '' }, '2026-10-14').workDate).toBe('请填写工作日期');
    expect(draftProblems({ ...VALID, workDate: '2026-10-15' }, '2026-10-14').workDate).toBe('工作日期不能晚于今天');
    expect(draftProblems({ ...VALID, workDate: '2026-10-14' }, '2026-10-14')).toEqual({});
  });

  it('工作包和项目经理要填正整数，工时要大于零', () => {
    expect(draftProblems(EMPTY_DRAFT, '2026-10-14')).toEqual({
      workDate: '请填写工作日期',
      workPackageId: '请填写工作包',
      hours: '请填写工时',
      projectManagerId: '请填写项目经理',
    });
    expect(draftProblems({ ...VALID, hours: '0' }, '2026-10-14').hours).toBe('请填写工时');
    expect(draftProblems({ ...VALID, hours: '-1' }, '2026-10-14').hours).toBe('请填写工时');
    expect(draftProblems({ ...VALID, workPackageId: '1.5' }, '2026-10-14').workPackageId).toBe('请填写工作包');
    expect(draftProblems({ ...VALID, projectManagerId: '0' }, '2026-10-14').projectManagerId).toBe('请填写项目经理');
  });
});

describe('submitBodyOf', () => {
  it('提交的内容包含用户填写的工作日期，数字按数字发送', () => {
    expect(submitBodyOf(VALID)).toEqual({ workDate: '2026-10-13', workPackageId: 12, hours: 7.5, projectManagerId: 1001 });
  });
});

describe('isEditable', () => {
  it('只有草稿和已退回的工时单可以修改后提交', () => {
    expect(['DRAFT', 'RETURNED', 'SUBMITTED', 'APPROVED', 'REJECTED'].map((status) => isEditable(status as 'DRAFT'))).toEqual([true, true, false, false, false]);
  });
});
