import type { Schema } from '@forcitis/api';
import { newPersonnelCommandPath } from './personnel-command';

export type ActingDuty = Schema<'ActingDutyResponse'>;
export type ActingCommandKind = 'ACTING_START' | 'ACTING_END' | 'ACTING_EXTEND';

export const PERSONNEL_COMMAND_CREATE = 'organization.workforce:personnel-command:create';
export const ACTING_STATE_LABELS: Readonly<Record<string, string>> = { ACTIVE: '有效', ENDED: '已结束' };
export const ACTING_END_CAUSE_LABELS: Readonly<Record<string, string>> = {
  EXPIRED: '已到期', ENDED_EARLY: '提前结束', SEPARATION: '代理人离职',
};

export function actingCommandPath(employeeId: string, kind: ActingCommandKind, dutyId?: string): string {
  const params = new URLSearchParams({ commandKind: kind });
  if (dutyId !== undefined) params.set('targetActingDutyId', dutyId);
  return `${newPersonnelCommandPath(employeeId)}&${params.toString()}`;
}
