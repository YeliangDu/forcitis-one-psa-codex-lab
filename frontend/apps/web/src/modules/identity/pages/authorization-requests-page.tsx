import { describeFailure, formatDateTime } from '@forcitis/core';
import { Button, DataTable, Dialog, LoadingState, Notice, PageTitle, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useAuthorizationRequests, useDecideAuthorizationRequest } from '../hooks/use-authorization-requests';
import type { AuthorizationRequest, Deciding } from '../model/authorization-request';

export function AuthorizationRequestsPage() {
  const requests = useAuthorizationRequests();
  const decide = useDecideAuthorizationRequest();
  const [deciding, setDeciding] = useState<Deciding>();

  if (requests.isPending) return <LoadingState />;
  if (requests.isError) return <Notice tone="error" title="授权申请加载失败">{describeFailure(requests.error)}</Notice>;

  const close = () => {
    setDeciding(undefined);
    decide.reset();
  };
  const submit = () => {
    if (deciding === undefined) return;
    decide.mutate({ requestId: deciding.request.requestId, decision: deciding.decision }, { onSuccess: close });
  };

  const columns: DataColumn<AuthorizationRequest>[] = [
    { key: 'role', title: '角色', render: (request) => request.roleName ?? (request.roleCodes.join('、') || '—') },
    { key: 'permissions', title: '权限', render: (request) => (request.permissionCodes.length === 0 ? '—' : request.permissionCodes.map((code) => <code key={code}>{code} </code>)) },
    { key: 'subject', title: '被授权用户', render: (request) => request.subjectUserId ?? '—' },
    { key: 'by', title: '申请人', render: (request) => request.requestedBy },
    { key: 'at', title: '申请时间', render: (request) => formatDateTime(request.requestedAt) },
    {
      key: 'actions',
      title: '操作',
      render: (request) => (
        <>
          <Button onClick={() => setDeciding({ request, decision: 'confirm' })}>确认</Button>
          <Button variant="default" onClick={() => setDeciding({ request, decision: 'reject' })}>驳回</Button>
        </>
      ),
    },
  ];

  const request = deciding?.request;
  const confirming = deciding?.decision === 'confirm';

  return (
    <>
      <PageTitle text="授权申请" />
      <DataTable columns={columns} rows={requests.data} rowKey={(row) => row.requestId} emptyText="暂无待确认的授权申请" />
      <Dialog
        open={deciding !== undefined}
        title={confirming ? '确认授权申请' : '驳回授权申请'}
        confirmText={confirming ? '确认' : '驳回'}
        confirmLoading={decide.isPending}
        onConfirm={submit}
        onClose={close}
      >
        {decide.isError ? <Notice tone="error" title={confirming ? '确认失败' : '驳回失败'}>{describeFailure(decide.error)}</Notice> : null}
        {request === undefined ? null : (
          <ul>
            <li>角色：{request.roleName ?? (request.roleCodes.join('、') || '—')}</li>
            <li>权限：{request.permissionCodes.length === 0 ? '—' : request.permissionCodes.join('、')}</li>
            <li>被授权用户：{request.subjectUserId ?? '—'}</li>
            <li>申请人：{request.requestedBy}</li>
            <li>申请时间：{formatDateTime(request.requestedAt)}</li>
          </ul>
        )}
      </Dialog>
    </>
  );
}
