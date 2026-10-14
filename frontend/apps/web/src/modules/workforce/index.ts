import { lazy } from 'react';

export const OrgUnitsPage = lazy(() => import('./pages/org-units-page').then((m) => ({ default: m.OrgUnitsPage })));
export const SyncRunsPage = lazy(() => import('./pages/sync-runs-page').then((m) => ({ default: m.SyncRunsPage })));
