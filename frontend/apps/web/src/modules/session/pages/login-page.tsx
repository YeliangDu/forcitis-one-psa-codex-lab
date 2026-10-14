import { LoginPanel, Notice } from '@forcitis/ui';
import { useState } from 'react';
import { Navigate } from 'react-router';
import { describeFailure } from '@/shared/lib/failure';
import { rememberReturnPath } from '../api/return-path-storage';
import { requestWecomLoginState } from '../api/session-api';
import { useCurrentUser } from '../hooks/use-current-user';
import { useReturnPath } from '../hooks/use-return-path';
import { LOGIN_ERROR_MESSAGES } from '../model/login-errors';
import { readLoginConfig, wecomAuthorizeUrl, wecomConfigured } from '../model/login-config';

export function LoginPage() {
  const session = useCurrentUser();
  const returnPath = useReturnPath();
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string>();
  const config = readLoginConfig(import.meta.env);

  if (session.status === 'signed-in') return <Navigate to={returnPath} replace />;

  const startWecom = () => {
    setBusy(true);
    setProblem(undefined);
    rememberReturnPath(returnPath);
    requestWecomLoginState()
      .then((state) => window.location.assign(wecomAuthorizeUrl(config, state, window.location.origin)))
      .catch((error: unknown) => {
        setProblem(describeFailure(error, LOGIN_ERROR_MESSAGES));
        setBusy(false);
      });
  };

  const startSso = () => {
    rememberReturnPath(returnPath);
    window.location.assign(config.ssoPortalUrl);
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
        {
          key: 'sso',
          label: '致远单点登录',
          description: '从公司门户进入',
          onSelect: startSso,
          ...(config.ssoPortalUrl === '' ? { unavailableText: '尚未配置致远单点登录' } : {}),
        },
      ]}
    />
  );
}
