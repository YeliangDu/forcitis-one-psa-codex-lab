import { describeFailure, formatDateTime, useCurrentUser, versionOf } from '@forcitis/core';
import { Button, DataTable, DescriptionList, Dialog, LoadingState, Notice, PageTitle, SelectField, StatusBadge, TextLink, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useAccount, useAccountGrants, useReplaceRoles, useRoles } from '../hooks/use-accounts';
import { ACCOUNT_LIST_PATH, ACCOUNT_ROLE_UPDATE, ACCOUNT_STATUS_LABELS, AUTHORIZATION_REQUESTS_PATH, accountStatusTone, roleName, type AccountGrant, type ReplaceOutcome } from '../model/account';

export function AccountDetailPage() {
  const { userId = '' } = useParams();
  const navigate = useNavigate();
  const session = useCurrentUser();
  const account = useAccount(userId);
  const grants = useAccountGrants(userId);
  const roles = useRoles();
  const replace = useReplaceRoles(userId);
  const [changing, setChanging] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);
  const [outcome, setOutcome] = useState<ReplaceOutcome>();

  if (account.isPending) return <LoadingState />;
  if (account.isError) return <Notice tone="error" title="账号加载失败">{describeFailure(account.error)}</Notice>;

  const detail = account.data;
  const allRoles = roles.data ?? [];
  const choices = allRoles.filter((role) => role.enabled && !selected.includes(role.code)).map((role) => ({ value: role.code, label: role.name }));
  const open = () => {
    setSelected(detail.roleCodes);
    setOutcome(undefined);
    replace.reset();
    setChanging(true);
  };
  const close = () => {
    setChanging(false);
    replace.reset();
  };
  const confirm = () => replace.mutate({ roleCodes: selected, version: versionOf(detail) }, { onSuccess: (result) => { setOutcome(result); close(); } });

  const grantColumns: DataColumn<AccountGrant>[] = [
    { key: 'role', title: '角色', render: (grant) => roleName(allRoles, grant.roleCode) },
    { key: 'from', title: '生效时间', render: (grant) => formatDateTime(grant.effectiveFrom) },
    { key: 'to', title: '失效时间', render: (grant) => (grant.effectiveTo === undefined ? '—' : formatDateTime(grant.effectiveTo)) },
  ];

  return (
    <>
      <PageTitle
        text={`账号 ${detail.userId}`}
        actions={
          <>
            <StatusBadge tone={accountStatusTone(detail.status)}>{ACCOUNT_STATUS_LABELS[detail.status] ?? detail.status}</StatusBadge>
            {session.can(ACCOUNT_ROLE_UPDATE) ? <Button onClick={open}>调整角色</Button> : null}
          </>
        }
      />
      {outcome?.kind === 'done' ? <Notice tone="success" title="角色已调整">角色已经生效。</Notice> : null}
      {outcome?.kind === 'awaiting' ? (
        <Notice tone="info" title="等待另一位确认">
          授权申请编号 {outcome.requestId}，角色在确认后才能生效。{' '}
          <TextLink href={AUTHORIZATION_REQUESTS_PATH} onNavigate={() => void navigate(AUTHORIZATION_REQUESTS_PATH)}>前往授权申请页确认</TextLink>
        </Notice>
      ) : null}
      <DescriptionList items={[{ label: '状态', value: ACCOUNT_STATUS_LABELS[detail.status] ?? detail.status }, { label: '当前角色', value: detail.roleCodes.length === 0 ? '—' : detail.roleCodes.map((code) => roleName(allRoles, code)).join('、') }]} />
      {grants.isPending ? <LoadingState /> : null}
      {grants.isError ? <Notice tone="error" title="角色授予记录加载失败">{describeFailure(grants.error)}</Notice> : null}
      {grants.data === undefined ? null : <DataTable columns={grantColumns} rows={grants.data} rowKey={(grant) => `${grant.roleCode}:${grant.effectiveFrom}`} emptyText="暂无角色授予记录" />}
      <TextLink href={ACCOUNT_LIST_PATH} onNavigate={() => void navigate(ACCOUNT_LIST_PATH)}>返回账号列表</TextLink>
      <Dialog open={changing} title="调整角色" confirmText="提交" confirmLoading={replace.isPending} onConfirm={confirm} onClose={close}>
        <ul>
          {selected.length === 0 ? <li>未选择角色</li> : selected.map((code) => (
            <li key={code}>
              {roleName(allRoles, code)} <Button variant="default" onClick={() => setSelected(selected.filter((other) => other !== code))}>{`移除 ${roleName(allRoles, code)}`}</Button>
            </li>
          ))}
        </ul>
        <SelectField label="添加角色" value="" options={[{ value: '', label: '请选择' }, ...choices]} onChange={(code) => { if (code !== '') setSelected([...selected, code]); }} />
        {replace.isError ? <Notice tone="error" title="调整角色失败">{describeFailure(replace.error)}</Notice> : null}
      </Dialog>
    </>
  );
}
