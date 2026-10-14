import { describeFailure, logout, useConfirmIdentity } from '@forcitis/core';
import { useQueryClient } from '@tanstack/react-query';
import { ActionBar, ActionButton } from '@/shared/ui/actions';
import { Notice } from '@/shared/ui/feedback';
import { Screen, ScreenTitle } from '@/shared/ui/screen';

/** Shown instead of every page until the person confirms who they are (C-M7). */
export function IdentityConfirmation({ displayName }: { displayName: string }) {
  const confirmation = useConfirmIdentity();
  const queryClient = useQueryClient();
  const leave = () => {
    void logout().finally(() => void queryClient.resetQueries());
  };
  return (
    <Screen>
      <ScreenTitle text={`将以${displayName}身份登录`} />
      <Notice tone="warning" title="请确认你的身份" />
      {confirmation.isError ? <Notice tone="error" title="确认失败">{describeFailure(confirmation.error)}</Notice> : null}
      <ActionBar>
        <ActionButton loading={confirmation.isPending} onClick={() => confirmation.mutate()}>确认身份</ActionButton>
        <ActionButton variant="default" onClick={leave}>退出登录</ActionButton>
      </ActionBar>
    </Screen>
  );
}
