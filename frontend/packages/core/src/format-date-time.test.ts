import { spawnSync } from 'node:child_process';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { formatDateTime } from './format-date-time';

const MODULE = join(process.cwd(), 'src', 'format-date-time.ts');

function displayedIn(zone: string, value: string): string {
  const script = `import { formatDateTime } from ${JSON.stringify(MODULE)}; process.stdout.write(formatDateTime(${JSON.stringify(value)}));`;
  const run = spawnSync(process.execPath, ['--input-type=module', '-e', script], { encoding: 'utf8', env: { ...process.env, TZ: zone } });
  if (run.status !== 0) throw new Error(run.stderr);
  return run.stdout;
}

describe('formatDateTime', () => {
  it('没有时间或时间无效时显示破折号', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime('not a date')).toBe('—');
  });

  it('按上海时间显示', () => {
    expect(formatDateTime('2026-10-14T01:00:00Z')).toBe('2026/10/14 09:00');
    expect(formatDateTime('2026-10-14T16:30:00Z')).toBe('2026/10/15 00:30');
  });

  it('浏览器在别的时区（洛杉矶、奥克兰、协调世界时）时，显示结果和上海一样', () => {
    for (const zone of ['America/Los_Angeles', 'Pacific/Auckland', 'UTC', 'Asia/Shanghai']) {
      expect(displayedIn(zone, '2026-10-14T01:00:00Z'), zone).toBe('2026/10/14 09:00');
      expect(displayedIn(zone, '2026-12-31T16:30:00Z'), zone).toBe('2027/01/01 00:30');
    }
  });
});
