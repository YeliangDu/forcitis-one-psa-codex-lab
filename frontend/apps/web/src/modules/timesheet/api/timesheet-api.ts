import { operations } from '@forcitis/api';
import type { SubmitBody } from '../model/timesheet';

export function fetchTimesheet(id: number) {
  return operations.getCostTimesheetsById({ path: { id } });
}

export async function submitTimesheet(id: number, body: SubmitBody): Promise<void> {
  await operations.postCostTimesheetsByIdSubmit({ path: { id }, body });
}

export async function withdrawTimesheet(id: number, reason: string): Promise<void> {
  await operations.postCostTimesheetsByIdWithdraw({ path: { id }, body: { reason } });
}
