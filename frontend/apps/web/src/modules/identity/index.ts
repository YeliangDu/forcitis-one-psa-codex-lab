import { lazy } from 'react';

export const AuthorizationRequestsPage = lazy(() => import('./pages/authorization-requests-page').then((m) => ({ default: m.AuthorizationRequestsPage })));
export const AccountGrantsPage = lazy(() => import('./pages/account-grants-page').then((m) => ({ default: m.AccountGrantsPage })));
export const PermissionAdjustmentListPage = lazy(() => import('./pages/permission-adjustment-list-page').then((m) => ({ default: m.PermissionAdjustmentListPage })));
export const PermissionAdjustmentDetailPage = lazy(() => import('./pages/permission-adjustment-detail-page').then((m) => ({ default: m.PermissionAdjustmentDetailPage })));
