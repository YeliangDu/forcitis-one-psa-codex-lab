import { badgeOf } from '@forcitis/core';
import { describe, expect, it } from 'vitest';
import { EMPLOYMENT_STATUS_LABELS, EMPLOYMENT_STATUS_TONES } from './employee';
import { ORG_UNIT_STATUS_LABELS, ORG_UNIT_STATUS_TONES } from './org-unit';
import { COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES } from './personnel-command';

describe('status badges of the workforce pages', () => {
  it('shows an employed person, an effective command and an active unit as posted, everything else as neutral', () => {
    expect(badgeOf(EMPLOYMENT_STATUS_LABELS, EMPLOYMENT_STATUS_TONES, 'EMPLOYED').tone).toBe('posted');
    expect(badgeOf(EMPLOYMENT_STATUS_LABELS, EMPLOYMENT_STATUS_TONES, 'SEPARATED').tone).toBe('neutral');
    expect(badgeOf(COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, 'EFFECTIVE').tone).toBe('posted');
    expect(badgeOf(COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, 'PENDING').tone).toBe('neutral');
    expect(badgeOf(ORG_UNIT_STATUS_LABELS, ORG_UNIT_STATUS_TONES, 'ACTIVE').tone).toBe('posted');
    expect(badgeOf(ORG_UNIT_STATUS_LABELS, ORG_UNIT_STATUS_TONES, 'DISABLED').tone).toBe('neutral');
  });
});
