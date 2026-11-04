import { describeFailure, useCurrentUser } from '@forcitis/core';
import { LoginPanel, Notice } from '@forcitis/ui';
import { useState } from 'react';
import { Navigate } from 'react-router';
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
      ]}
    />
  );
}
