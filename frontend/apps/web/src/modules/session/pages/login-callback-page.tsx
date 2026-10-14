import { Button, LoadingState, ResultPage, TextLink } from '@forcitis/ui';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { describeFailure, failureOf } from '@/shared/lib/failure';
import { safeExternalUrl } from '@/shared/lib/safe-url';
import { takeReturnPath } from '../api/return-path-storage';
import { loginWithSso, loginWithWecom } from '../api/session-api';
import { CURRENT_USER_KEY } from '../hooks/use-current-user';
import { LOGIN_ERROR_MESSAGES } from '../model/login-errors';

interface Problem {
  message: string;
  legacyLoginUrl: string | undefined;
}

function legacyLoginUrlOf(error: unknown): string | undefined {
  const { body } = failureOf(error);
  if (typeof body !== 'object' || body === null) return undefined;
  const value = (body as Record<string, unknown>)['legacyLoginUrl'];
  return safeExternalUrl(typeof value === 'string' ? value : undefined);
}

/** Landing page of the WeCom redirect (code + state) and of the portal redirect (ticket). The one-time values leave the address bar at once. */
export function LoginCallbackPage({ kind }: { kind: 'wecom' | 'sso' }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const started = useRef(false);
  const [problem, setProblem] = useState<Problem>();

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    const params = new URLSearchParams(window.location.search);
    window.history.replaceState(null, '', window.location.pathname);
    const code = params.get('code');
    const state = params.get('state');
    const ticket = params.get('ticket');

    const attempt = kind === 'wecom'
      ? (code !== null && state !== null ? loginWithWecom(code, state) : undefined)
      : (ticket === null ? undefined : loginWithSso(ticket));
    if (attempt === undefined) {
      setProblem({ message: '登录信息不完整，请重新登录', legacyLoginUrl: undefined });
      return;
    }
    attempt
      .then(async () => {
        await queryClient.invalidateQueries({ queryKey: CURRENT_USER_KEY });
        void navigate(takeReturnPath(), { replace: true });
      })
      .catch((error: unknown) => setProblem({ message: describeFailure(error, LOGIN_ERROR_MESSAGES), legacyLoginUrl: legacyLoginUrlOf(error) }));
  }, [kind, navigate, queryClient]);

  if (problem === undefined) return <LoadingState label="正在登录" />;
  return (
    <ResultPage
      kind="error"
      title="登录失败"
      description={problem.message}
      actions={(
        <>
          <Button onClick={() => void navigate('/login', { replace: true })}>返回登录页</Button>
          {problem.legacyLoginUrl === undefined ? null : <TextLink href={problem.legacyLoginUrl} external>回到老系统登录</TextLink>}
        </>
      )}
    />
  );
}
