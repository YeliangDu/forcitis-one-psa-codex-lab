import { lazy } from 'react';

export const OrgUnitsPage = lazy(() => import('./pages/org-units-page').then((m) => ({ default: m.OrgUnitsPage })));
