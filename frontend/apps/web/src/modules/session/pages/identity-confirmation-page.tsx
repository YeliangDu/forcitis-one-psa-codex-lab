import { describeFailure, useConfirmIdentity } from '@forcitis/core';
import { Button, Notice, ResultPage } from '@forcitis/ui';
import { useLogout } from '../hooks/use-logout';

/** Shown instead of every business page until the person confirms who they are (C-M7: only on a browser the server does not know). */
export function IdentityConfirmationPage({ displayName }: { displayName: string }) {
  const confirmation = useConfirmIdentity();
  const logout = useLogout();
  return (
    <ResultPage
      kind="info"
      title={`将以${displayName}身份登录`}
      description="请确认你的身份。"
      actions={(
        <>
          {confirmation.isError ? <Notice tone="error" title="确认失败">{describeFailure(confirmation.error)}</Notice> : null}
          <Button loading={confirmation.isPending} onClick={() => confirmation.mutate()}>确认身份</Button>
          <Button variant="default" onClick={logout}>退出登录</Button>
        </>
      )}
    />
  );
}
