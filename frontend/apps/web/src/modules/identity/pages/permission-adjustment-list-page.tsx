import { describeFailure, formatDateTime } from '@forcitis/core';
import { Button, DataTable, LoadingState, Notice, PageTitle, SelectField, StatusBadge, TextLink, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { usePermissionAdjustments } from '../hooks/use-permission-adjustments';
import { KIND_LABELS, ORIGIN_LABELS, STATUS_LABELS, aboutLabel, permissionAdjustmentPath, statusTone, type PermissionAdjustment } from '../model/permission-adjustment';

const ALL = { value: '', label: '全部' };
const STATUS_OPTIONS = [ALL, ...Object.entries(STATUS_LABELS).map(([value, label]) => ({ value, label }))];
const KIND_OPTIONS = [ALL, ...Object.entries(KIND_LABELS).map(([value, label]) => ({ value, label }))];

export function PermissionAdjustmentListPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState('OPEN');
  const [kind, setKind] = useState('');
  const list = usePermissionAdjustments({ status, kind });

  const columns: DataColumn<PermissionAdjustment>[] = [
    {
      key: 'about',
      title: '对象',
      render: (item) => <TextLink href={permissionAdjustmentPath(item.permissionAdjustmentId)} onNavigate={() => void navigate(permissionAdjustmentPath(item.permissionAdjustmentId))}>{aboutLabel(item)}</TextLink>,
    },
    { key: 'kind', title: '类型', render: (item) => KIND_LABELS[item.kind] ?? item.kind },
    { key: 'origin', title: '来源', render: (item) => ORIGIN_LABELS[item.originKind] ?? item.originKind },
    { key: 'status', title: '状态', render: (item) => <StatusBadge tone={statusTone(item.status)}>{STATUS_LABELS[item.status] ?? item.status}</StatusBadge> },
    { key: 'expires', title: '维持到期时间', render: (item) => (item.upholdExpiresAt === undefined ? '—' : formatDateTime(item.upholdExpiresAt)) },
  ];

  return (
    <>
      <PageTitle text="待调整权限" />
      <SelectField label="状态" value={status} options={STATUS_OPTIONS} onChange={setStatus} />
      <SelectField label="类型" value={kind} options={KIND_OPTIONS} onChange={setKind} />
      {list.isPending ? <LoadingState /> : null}
      {list.isError ? <Notice tone="error" title="待调整权限加载失败">{describeFailure(list.error)}</Notice> : null}
      {list.items === undefined ? null : <DataTable columns={columns} rows={list.items} rowKey={(item) => item.permissionAdjustmentId} emptyText="暂无待调整权限" />}
      {list.hasNextPage ? <Button variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</Button> : null}
    </>
  );
}
