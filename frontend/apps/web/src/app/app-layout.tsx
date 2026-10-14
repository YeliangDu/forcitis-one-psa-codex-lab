import { AppShell, LoadingState, ResultPage } from '@forcitis/ui';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { useCurrentUser, useLogout } from '@/modules/session';
import { toNavEntries } from './navigation';

export function AppLayout() {
  const session = useCurrentUser();
  const logout = useLogout();
  const location = useLocation();
  const navigate = useNavigate();

  if (session.status === 'loading') return <LoadingState />;
  if (session.status === 'signed-out') return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  if (session.user === undefined) return <ResultPage kind="error" title="无法获取登录信息" description="请刷新页面重试，仍然失败请联系管理员。" />;

  return (
    <AppShell
      brand="FORCITIS one"
      navigation={toNavEntries(session.user.navigation)}
      activePath={location.pathname}
      onNavigate={(path) => void navigate(path)}
      userName={session.user.displayName}
      {...(session.user.orgUnitName === null || session.user.orgUnitName === undefined ? {} : { userDetail: session.user.orgUnitName })}
      logoutText="退出登录"
      onLogout={logout}
    >
      <Outlet />
    </AppShell>
  );
}
