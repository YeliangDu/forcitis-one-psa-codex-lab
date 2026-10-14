import { lazy } from 'react';

export const WorkCalendarPage = lazy(() => import('./pages/work-calendar-page').then((m) => ({ default: m.WorkCalendarPage })));
