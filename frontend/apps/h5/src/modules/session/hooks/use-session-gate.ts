import {
  CURRENT_USER_KEY,
  describeFailure,
  failureOf,
  installApiGuards,
  loginWithWecom,
  requestLoginState,
  useCurrentUserQuery,
} from '@forcitis/core';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { currentUrl, leaveTo, replaceUrl } from '@/shared/platform/browser';
import {
  NOT_CONFIGURED_MESSAGE,
  SESSION_NOT_KEPT_MESSAGE,
  type LoginConfig,
  authorizationReplyOf,
  authorizeUrl,
  withoutAuthorizationReply,
} from '../model/wecom-login';

const ENTRY = 'WORKBENCH' as const;

export type GatePhase = 'checking' | 'ready' | 'failed';

export interface SessionGate {
  phase: GatePhase;
  problem: string | undefined;
  retry: () => void;
}

/**
 * Opens the app inside the WeCom workbench without a login page: no session -> silent authorisation (snsapi_base) -> the
 * code comes back in the address bar -> it is traded for a session and leaves the address bar before anything else happens.
 */
export function useSessionGate(config: LoginConfig): SessionGate {
  const queryClient = useQueryClient();
  const user = useCurrentUserQuery();
  const [problem, setProblem] = useState<string>();
  const busy = useRef(false);

  const signIn = useCallback(async () => {
    busy.current = true;
    try {
      const reply = authorizationReplyOf(currentUrl());
      if (reply !== undefined) {
        replaceUrl(withoutAuthorizationReply(currentUrl()));
        await loginWithWecom(reply.code, reply.state, ENTRY);
        await queryClient.invalidateQueries({ queryKey: CURRENT_USER_KEY });
        if (queryClient.getQueryState(CURRENT_USER_KEY)?.status === 'error') setProblem(SESSION_NOT_KEPT_MESSAGE);
        return;
      }
      if (config.corpId === '') {
        setProblem(NOT_CONFIGURED_MESSAGE);
        return;
      }
      leaveTo(authorizeUrl(config, await requestLoginState(ENTRY), currentUrl()));
    } catch (error: unknown) {
      setProblem(describeFailure(error));
    } finally {
      busy.current = false;
    }
  }, [config, queryClient]);

  useEffect(() => {
    if (!user.isError || problem !== undefined || busy.current) return;
    if (failureOf(user.error).status === 401) void signIn();
    else setProblem(describeFailure(user.error));
  }, [user.isError, user.error, problem, signIn]);

  useEffect(() => {
    return installApiGuards({ onUnauthenticated: () => void queryClient.resetQueries({ queryKey: CURRENT_USER_KEY }) });
  }, [queryClient]);

  const retry = useCallback(() => {
    setProblem(undefined);
    void queryClient.resetQueries({ queryKey: CURRENT_USER_KEY });
  }, [queryClient]);

  if (user.isSuccess) return { phase: 'ready', problem: undefined, retry };
  return { phase: problem === undefined ? 'checking' : 'failed', problem, retry };
}
