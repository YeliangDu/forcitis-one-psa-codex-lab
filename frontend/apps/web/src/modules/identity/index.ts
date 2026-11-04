import { lazy } from 'react';

export const AuthorizationRequestsPage = lazy(() => import('./pages/authorization-requests-page').then((m) => ({ default: m.AuthorizationRequestsPage })));
