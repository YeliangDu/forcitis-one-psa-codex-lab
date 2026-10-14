import { ProjectProgressPage } from '@/modules/delivery';
import { TimesheetPage } from '@/modules/timesheet';

export const routes = [
  { path: '/timesheet', element: <TimesheetPage /> },
  { path: '/delivery/progress', element: <ProjectProgressPage /> },
];
