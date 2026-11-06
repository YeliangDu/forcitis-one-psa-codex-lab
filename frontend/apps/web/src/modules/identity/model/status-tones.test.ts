import { badgeOf } from '@forcitis/core';
import { describe, expect, it } from 'vitest';
import { STATUS_LABELS, STATUS_TONES } from './permission-adjustment';

describe('status badge of a permission adjustment', () => {
  it('shows an open item as pending, a handled one as posted and anything else as neutral', () => {
    expect(badgeOf(STATUS_LABELS, STATUS_TONES, 'OPEN').tone).toBe('pending');
    expect(badgeOf(STATUS_LABELS, STATUS_TONES, 'UPHELD').tone).toBe('posted');
    expect(badgeOf(STATUS_LABELS, STATUS_TONES, 'ADJUSTED').tone).toBe('posted');
    expect(badgeOf(STATUS_LABELS, STATUS_TONES, 'SOMETHING_NEW').tone).toBe('neutral');
  });
});
