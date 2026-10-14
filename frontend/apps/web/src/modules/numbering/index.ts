import { lazy } from 'react';

export const NumberingRulesPage = lazy(() => import('./pages/numbering-rules-page').then((m) => ({ default: m.NumberingRulesPage })));
