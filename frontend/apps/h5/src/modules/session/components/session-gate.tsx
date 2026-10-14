import { useCurrentUser } from '@forcitis/core';
import type { ReactNode } from 'react';
import { ActionBar, ActionButton } from '@/shared/ui/actions';
import { LoadingHint, Notice } from '@/shared/ui/feedback';
import { Screen } from '@/shared/ui/screen';
import { useSessionGate } from '../hooks/use-session-gate';
import { IdentityConfirmation } from './identity-confirmation';
import type { LoginConfig } from '../model/wecom-login';

export function SessionGate({ config, children }: { config: LoginConfig; children: ReactNode }) {
  const gate = useSessionGate(config);
  const session = useCurrentUser();
  if (gate.phase === 'ready' && session.identityPending && session.user !== undefined) return <IdentityConfirmation displayName={session.user.displayName} />;
  if (gate.phase === 'ready') return <>{children}</>;
  if (gate.phase === 'checking') return <Screen><LoadingHint label="正在登录" /></Screen>;
  return (
    <Screen>
      <Notice tone="error" title="登录失败">{gate.problem}</Notice>
      <ActionBar>
        <ActionButton onClick={gate.retry}>重新登录</ActionButton>
      </ActionBar>
    </Screen>
  );
}
