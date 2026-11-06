import { describe, expect, it } from 'vitest';
import { badgeOf } from './labels';

const labels = { OPEN: '待处理', DONE: '已完成' };
const tones = { DONE: 'posted' } as const;

describe('badgeOf', () => {
  it('gives the label and the tone of a value both tables know', () => {
    expect(badgeOf(labels, tones, 'DONE')).toEqual({ label: '已完成', tone: 'posted' });
  });

  it('gives a neutral tone to a value the tone table leaves out', () => {
    expect(badgeOf(labels, tones, 'OPEN')).toEqual({ label: '待处理', tone: 'neutral' });
  });

  it('shows a value of a newer server as itself, with a neutral tone', () => {
    expect(badgeOf(labels, tones, 'FUTURE')).toEqual({ label: 'FUTURE', tone: 'neutral' });
  });
});
