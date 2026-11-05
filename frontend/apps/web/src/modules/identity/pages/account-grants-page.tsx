import { describeFailure, formatDateTime } from '@forcitis/core';
import { DataTable, LoadingState, Notice, PageTitle, SelectField, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useAccountGrants, useAccounts } from '../hooks/use-account-grants';
import { type AccountGrant, accountLabel, sourceLabel } from '../model/account-grant';

const NONE = { value: '', label: '请选择' };

const COLUMNS: DataColumn<AccountGrant>[] = [
  { key: 'role', title: '角色', render: (grant) => grant.roleCode },
  { key: 'source', title: '来源', render: sourceLabel },
  { key: 'from', title: '生效时间', render: (grant) => formatDateTime(grant.effectiveFrom) },
  { key: 'to', title: '失效时间', render: (grant) => (grant.effectiveTo === undefined || grant.effectiveTo === null ? '—' : formatDateTime(grant.effectiveTo)) },
];

export function AccountGrantsPage() {
  const [userId, setUserId] = useState('');
  const accounts = useAccounts();
  const grants = useAccountGrants(userId);

  if (accounts.isPending) return <LoadingState />;
  if (accounts.isError) return <Notice tone="error" title="账号加载失败">{describeFailure(accounts.error)}</Notice>;

  const options = [NONE, ...accounts.data.map((account) => ({ value: account.userId, label: accountLabel(account) }))];

  return (
    <>
      <PageTitle text="账号授权记录" />
      <SelectField label="账号" value={userId} options={options} onChange={setUserId} />
      {userId === '' ? null : grants.isError ? (
        <Notice tone="error" title="授权记录加载失败">{describeFailure(grants.error)}</Notice>
      ) : grants.isPending ? (
        <LoadingState />
      ) : (
        <DataTable columns={COLUMNS} rows={grants.data} rowKey={(grant) => `${grant.roleCode}|${grant.effectiveFrom}`} emptyText="这个账号没有授权记录" />
      )}
    </>
  );
}
