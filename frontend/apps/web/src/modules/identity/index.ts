import { lazy } from 'react';

export const AuthorizationRequestsPage = lazy(() => import('./pages/authorization-requests-page').then((m) => ({ default: m.AuthorizationRequestsPage })));
export const PermissionAdjustmentListPage = lazy(() => import('./pages/permission-adjustment-list-page').then((m) => ({ default: m.PermissionAdjustmentListPage })));
export const PermissionAdjustmentDetailPage = lazy(() => import('./pages/permission-adjustment-detail-page').then((m) => ({ default: m.PermissionAdjustmentDetailPage })));
export const AccountListPage = lazy(() => import('./pages/account-list-page').then((m) => ({ default: m.AccountListPage })));
export const AccountDetailPage = lazy(() => import('./pages/account-detail-page').then((m) => ({ default: m.AccountDetailPage })));
