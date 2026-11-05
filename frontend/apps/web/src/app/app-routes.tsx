import { Button, ResultPage } from '@forcitis/ui';
import { Suspense } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router';
import { WorkCalendarPage } from '@/modules/attendance';
import { InboxPage, TaskDetailPage } from '@/modules/approval';
import { AccountDetailPage, AccountListPage, AuthorizationRequestsPage, PermissionAdjustmentDetailPage, PermissionAdjustmentListPage } from '@/modules/identity';
import { NumberingRulesPage } from '@/modules/numbering';
import { EmergencyLoginPage, ForbiddenPage, LoginCallbackPage, LoginPage, SessionExpiredPage, useApiGuards } from '@/modules/session';
import { EmployeeDetailPage, EmployeeRosterPage, OrgUnitsPage, PersonnelCommandDetailPage, PersonnelCommandFormPage, PersonnelCommandListPage } from '@/modules/workforce';
import { AppLayout } from './app-layout';

function NotMigratedPage() {
  const navigate = useNavigate();
  return (
    <ResultPage
      kind="not-found"
      title="页面不存在"
      description="这个页面还没有迁移到新系统，或者地址不正确。"
      actions={<Button onClick={() => void navigate('/inbox')}>回到待办</Button>}
    />
  );
}

export function AppRoutes() {
  useApiGuards();
  return (
    <Suspense fallback={null}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/login/wecom" element={<LoginCallbackPage kind="wecom" />} />
        <Route path="/login/ticket" element={<LoginCallbackPage kind="ticket" />} />
        <Route path="/emergency" element={<EmergencyLoginPage />} />
        <Route path="/session-expired" element={<SessionExpiredPage />} />
        <Route path="/forbidden" element={<ForbiddenPage />} />
        <Route element={<AppLayout />}>
          <Route index element={<Navigate to="/inbox" replace />} />
          <Route path="/inbox" element={<InboxPage />} />
          <Route path="/approval/tasks/:taskId" element={<TaskDetailPage />} />
          <Route path="/admin/accounts" element={<AccountListPage />} />
          <Route path="/admin/accounts/:userId" element={<AccountDetailPage />} />
          <Route path="/admin/authorization-requests" element={<AuthorizationRequestsPage />} />
          <Route path="/admin/permission-adjustments" element={<PermissionAdjustmentListPage />} />
          <Route path="/admin/permission-adjustments/:permissionAdjustmentId" element={<PermissionAdjustmentDetailPage />} />
          <Route path="/admin/numbering-rules" element={<NumberingRulesPage />} />
          <Route path="/admin/work-calendars" element={<WorkCalendarPage />} />
          <Route path="/admin/org-units" element={<OrgUnitsPage />} />
          <Route path="/admin/employees" element={<EmployeeRosterPage />} />
          <Route path="/admin/employees/:employeeId" element={<EmployeeDetailPage />} />
          <Route path="/admin/personnel-commands" element={<PersonnelCommandListPage />} />
          <Route path="/admin/personnel-commands/new" element={<PersonnelCommandFormPage />} />
          <Route path="/admin/personnel-commands/:personnelCommandId" element={<PersonnelCommandDetailPage />} />
          <Route path="*" element={<NotMigratedPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
