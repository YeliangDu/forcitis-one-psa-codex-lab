import { lazy } from 'react';

export const OrgUnitsPage = lazy(() => import('./pages/org-units-page').then((m) => ({ default: m.OrgUnitsPage })));
export const EmployeeRosterPage = lazy(() => import('./pages/employee-roster-page').then((m) => ({ default: m.EmployeeRosterPage })));
export const EmployeeDetailPage = lazy(() => import('./pages/employee-detail-page').then((m) => ({ default: m.EmployeeDetailPage })));
export const PersonnelCommandListPage = lazy(() => import('./pages/personnel-command-list-page').then((m) => ({ default: m.PersonnelCommandListPage })));
export const PersonnelCommandDetailPage = lazy(() => import('./pages/personnel-command-detail-page').then((m) => ({ default: m.PersonnelCommandDetailPage })));
export const PersonnelCommandFormPage = lazy(() => import('./pages/personnel-command-form-page').then((m) => ({ default: m.PersonnelCommandFormPage })));
