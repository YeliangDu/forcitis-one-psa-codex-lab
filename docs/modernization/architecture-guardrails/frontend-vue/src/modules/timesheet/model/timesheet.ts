import type { TimesheetStatus } from './timesheet-status';

export interface Timesheet {
  id: string;
  workDate: string;
  hours: number;
  costAmount: number;
  status: TimesheetStatus;
}
