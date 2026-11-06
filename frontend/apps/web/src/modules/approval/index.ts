import { lazy } from 'react';

export const InboxPage = lazy(() => import('./pages/inbox-page').then((m) => ({ default: m.InboxPage })));
export const TaskDetailPage = lazy(() => import('./pages/task-detail-page').then((m) => ({ default: m.TaskDetailPage })));

export { registerTaskDocument, type TaskDocumentProps } from './components/task-documents';
