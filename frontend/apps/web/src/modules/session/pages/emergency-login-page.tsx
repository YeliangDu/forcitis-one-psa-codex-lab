import { CURRENT_USER_KEY, describeFailure } from '@forcitis/core';
import { Button, Notice, TextField } from '@forcitis/ui';
import { useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router';
import { loginAsEmergencyAdmin } from '../api/session-api';
import './emergency-login-page.css';

export function EmergencyLoginPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [loginName, setLoginName] = useState('');
  const [password, setPassword] = useState('');
  const [totpCode, setTotpCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string>();

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setProblem(undefined);
    loginAsEmergencyAdmin(loginName, password, totpCode)
      .then(async () => {
        await queryClient.invalidateQueries({ queryKey: CURRENT_USER_KEY });
        void navigate('/', { replace: true });
      })
      .catch((error: unknown) => {
        setProblem(describeFailure(error));
        setBusy(false);
      });
  };

  return (
    <main className="emergency-login">
      <form className="emergency-login__card" onSubmit={submit}>
        <h1 className="emergency-login__title">应急管理员登录</h1>
        <p className="emergency-login__hint">仅在企业微信和单点登录都不可用时使用，只能在公司内网登录。</p>
        {problem === undefined ? null : <Notice tone="error" title={problem} />}
        <TextField label="登录名" value={loginName} onChange={setLoginName} autoComplete="username" />
        <TextField label="口令" type="password" value={password} onChange={setPassword} autoComplete="current-password" />
        <TextField label="动态口令" value={totpCode} onChange={setTotpCode} autoComplete="one-time-code" inputMode="numeric" />
        <Button htmlType="submit" loading={busy} disabled={loginName === '' || password === '' || totpCode === ''} block>登录</Button>
      </form>
    </main>
  );
}
