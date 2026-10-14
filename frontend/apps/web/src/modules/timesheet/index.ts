import { lazy } from 'react';

export const TimesheetPage = lazy(() => import('./pages/timesheet-page').then((m) => ({ default: m.TimesheetPage })));
