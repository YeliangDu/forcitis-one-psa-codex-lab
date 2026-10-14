// Generated typed client. Do not edit by hand.
import { getJson } from '@/shared/api/http-client';
import { TaskPackageResponse, TimesheetResponse } from './schema';

export const apiClient = {
  getTimesheet: (id: string) => getJson(`/api/v1/timesheet/timesheets/${id}`, TimesheetResponse),
  getTaskPackage: (id: string) => getJson(`/api/v1/delivery/task-packages/${id}`, TaskPackageResponse),
};
