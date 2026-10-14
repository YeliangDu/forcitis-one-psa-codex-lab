import { lazy } from 'react';

export { useApiGuards } from './hooks/use-api-guards';
export { useCurrentUser } from './hooks/use-current-user';
export { useLogout } from './hooks/use-logout';
export const EmergencyLoginPage = lazy(() => import('./pages/emergency-login-page').then((m) => ({ default: m.EmergencyLoginPage })));
export const LoginCallbackPage = lazy(() => import('./pages/login-callback-page').then((m) => ({ default: m.LoginCallbackPage })));
export const LoginPage = lazy(() => import('./pages/login-page').then((m) => ({ default: m.LoginPage })));
export const ForbiddenPage = lazy(() => import('./pages/notice-pages').then((m) => ({ default: m.ForbiddenPage })));
export const SessionExpiredPage = lazy(() => import('./pages/notice-pages').then((m) => ({ default: m.SessionExpiredPage })));
