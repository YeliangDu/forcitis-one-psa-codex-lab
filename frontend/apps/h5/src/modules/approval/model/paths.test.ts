import { describe, expect, it } from 'vitest';
import { taskPath } from './paths';

describe('taskPath', () => {
  it('任务编号经过编码，空格和斜杠不会改变页面地址', () => {
    expect(taskPath('t-1')).toBe('/app/pages/task?taskId=t-1');
    expect(taskPath('a/b c')).toBe('/app/pages/task?taskId=a%2Fb%20c');
  });
});
