import { CURRENT_USER_KEY, describeFailure, failureOf, safeExternalUrl } from '@forcitis/core';
import { Button, LoadingState, ResultPage, TextLink } from '@forcitis/ui';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { takeReturnPath } from '../api/return-path-storage';
import { loginWithSignedTicket, loginWithWecomCode } from '../api/session-api';

interface Problem {
  message: string;
  legacyLoginUrl: string | undefined;
}

function legacyLoginUrlOf(error: unknown): string | undefined {
  return safeExternalUrl(failureOf(error).legacyLoginUrl);
}

/** Landing page of the WeCom redirect (code + state) and of an external system's signed ticket. The one-time values leave the address bar at once. */
export function LoginCallbackPage({ kind }: { kind: 'wecom' | 'ticket' }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const started = useRef(false);
  const [problem, setProblem] = useState<Problem>();

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    // WeCom sends its one-time values in the query; an external system's signed ticket travels in the fragment, which never reaches a server access log.
    const params = new URLSearchParams(kind === 'ticket' ? window.location.hash.slice(1) : window.location.search);
    window.history.replaceState(null, '', window.location.pathname);
    const code = params.get('code');
    const state = params.get('state');
    const ticket = params.get('ticket');

    const attempt = kind === 'wecom'
      ? (code !== null && state !== null ? loginWithWecomCode(code, state) : undefined)
      : (ticket === null ? undefined : loginWithSignedTicket(ticket));
    if (attempt === undefined) {
      setProblem({ message: '登录信息不完整，请重新登录', legacyLoginUrl: undefined });
      return;
    }
    attempt
      .then(async () => {
        await queryClient.invalidateQueries({ queryKey: CURRENT_USER_KEY });
        void navigate(takeReturnPath(), { replace: true });
      })
      .catch((error: unknown) => setProblem({ message: describeFailure(error), legacyLoginUrl: legacyLoginUrlOf(error) }));
  }, [kind, navigate, queryClient]);

  if (problem === undefined) return <LoadingState label="正在登录" />;
  return (
    <ResultPage
      kind="error"
      title="登录失败"
      description={problem.message}
      actions={(
        <>
          <Button onClick={() => void navigate('/login', { replace: true })}>返回登录页面</Button>
          {problem.legacyLoginUrl === undefined ? null : <TextLink href={problem.legacyLoginUrl} external>回到老系统登录</TextLink>}
        </>
      )}
    />
  );
}
