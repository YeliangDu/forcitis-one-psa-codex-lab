import { describe, expect, it } from 'vitest';
import { taskPath } from './paths';

describe('taskPath', () => {
  it('任务编号经过编码，斜杠和空格不会改变路径', () => {
    expect(taskPath('t-1')).toBe('/approval/tasks/t-1');
    expect(taskPath('a/b c')).toBe('/approval/tasks/a%2Fb%20c');
  });
});
