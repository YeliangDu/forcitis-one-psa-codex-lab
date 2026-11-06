import { describeFailure, useCurrentUser } from '@forcitis/core';
import { LoginPanel, Notice } from '@forcitis/ui';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Navigate } from 'react-router';
import { fetchLocalAccounts, loginAsLocalAccount } from '../api/local-login-api';
import { rememberReturnPath } from '../api/return-path-storage';
import { requestWecomLoginState } from '../api/session-api';
import { useReturnPath } from '../hooks/use-return-path';
import { readLoginConfig, wecomAuthorizeUrl, wecomConfigured } from '../model/login-config';

export function LoginPage() {
  const session = useCurrentUser();
  const returnPath = useReturnPath();
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string>();
  const config = readLoginConfig(import.meta.env);
  // Shown only when the backend itself says it has a local test login (its profile local): no setting of the client can switch it on.
  const localAccounts = useQuery({ queryKey: ['local-login-accounts'], queryFn: fetchLocalAccounts, staleTime: Infinity, retry: false });

  if (session.status === 'signed-in') return <Navigate to={returnPath} replace />;

  const startWecom = () => {
    setBusy(true);
    setProblem(undefined);
    rememberReturnPath(returnPath);
    requestWecomLoginState()
      .then((state) => window.location.assign(wecomAuthorizeUrl(config, state, window.location.origin)))
      .catch((error: unknown) => {
        setProblem(describeFailure(error));
        setBusy(false);
      });
  };

  const startLocal = (userId: string) => {
    setBusy(true);
    setProblem(undefined);
    loginAsLocalAccount(userId)
      .then(() => window.location.assign(returnPath))
      .catch((error: unknown) => {
        setProblem(describeFailure(error));
        setBusy(false);
      });
  };

  return (
    <LoginPanel
      title="FORCITIS one"
      subtitle="请选择登录方式"
      notice={problem === undefined ? undefined : <Notice tone="error" title={problem} />}
      entries={[
        {
          key: 'wecom',
          label: '企业微信扫码登录',
          description: '使用企业微信扫描二维码登录',
          onSelect: startWecom,
          loading: busy,
          ...(wecomConfigured(config) ? {} : { unavailableText: '尚未配置企业微信登录' }),
        },
        ...(localAccounts.data ?? []).map((account) => ({
          key: `local-${account.userId}`,
          label: `本机登录：${account.name}`,
          description: account.roleCodes.length === 0 ? '没有角色' : `角色：${account.roleCodes.join('、')}`,
          onSelect: () => startLocal(account.userId),
          loading: busy,
        })),
      ]}
    />
  );
}
