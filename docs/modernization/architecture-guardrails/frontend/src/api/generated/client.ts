// Generated typed client (openapi-fetch). Do not edit by hand.
import type { TaskPackageResponse, TimesheetResponse } from './schema';

export const apiClient = {
  getTimesheet: async (id: number): Promise<TimesheetResponse> =>
    (await fetch(`/api/v1/timesheet/timesheets/${id}`)).json() as Promise<TimesheetResponse>,
  getTaskPackage: async (id: number): Promise<TaskPackageResponse> =>
    (await fetch(`/api/v1/delivery/task-packages/${id}`)).json() as Promise<TaskPackageResponse>,
};
