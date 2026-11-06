import { describeFailure } from '@forcitis/core';
import { Button, DataTable, LoadingState, Notice, PageTitle, SelectField, StatusBadge, TextField, TextLink, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useAccounts, useRoles } from '../hooks/use-accounts';
import { ACCOUNT_STATUS_LABELS, accountLabel, accountPath, accountStatusTone, matchesSearch, roleName, type AccountListItem } from '../model/account';

const STATUS_OPTIONS = [{ value: '', label: '全部' }, ...Object.entries(ACCOUNT_STATUS_LABELS).map(([value, label]) => ({ value, label }))];

export function AccountListPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const list = useAccounts(status);
  const roles = useRoles();

  const columns: DataColumn<AccountListItem>[] = [
    {
      key: 'account',
      title: '账号',
      render: (item) => <TextLink href={accountPath(item.userId)} onNavigate={() => void navigate(accountPath(item.userId))}>{accountLabel(item)}</TextLink>,
    },
    { key: 'code', title: '员工编号', render: (item) => item.employeeCode ?? '—' },
    { key: 'status', title: '状态', render: (item) => <StatusBadge tone={accountStatusTone(item.status)}>{ACCOUNT_STATUS_LABELS[item.status] ?? item.status}</StatusBadge> },
    { key: 'roles', title: '角色', render: (item) => (item.roleCodes.length === 0 ? '—' : item.roleCodes.map((code) => roleName(roles.data ?? [], code)).join('、')) },
  ];

  return (
    <>
      <PageTitle text="账号" />
      <TextField label="搜索（姓名、员工编号、账号编号）" value={search} onChange={setSearch} hint="仅搜索已载入的账号" />
      <SelectField label="状态" value={status} options={STATUS_OPTIONS} onChange={setStatus} />
      {list.isPending ? <LoadingState /> : null}
      {list.isError ? <Notice tone="error" title="账号加载失败">{describeFailure(list.error)}</Notice> : null}
      {list.items === undefined ? null : <DataTable columns={columns} rows={list.items.filter((item) => matchesSearch(item, search))} rowKey={(item) => item.userId} emptyText="暂无账号" />}
      {list.hasNextPage ? <Button variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</Button> : null}
    </>
  );
}
