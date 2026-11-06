import { badgeOf, formatDateTime, useCurrentUser, versionOf } from '@forcitis/core';
import { Button, DescriptionList, Dialog, PageTitle, ReasonDialog, SelectField, StatusBadge, TextField, TextLink } from '@forcitis/ui';
import { MutationNotice, QueryBoundary } from '../../../shared/query-boundary';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useMarkAdjusted, usePermissionAdjustment, useUpholdAdjustment } from '../hooks/use-permission-adjustments';
import { ROLE_READ, accountPath } from '../model/account';
import { KIND_LABELS, ORIGIN_LABELS, REFERENCE_LABELS, STATUS_LABELS, TRIGGER_FIELD_LABELS, aboutLabel, nameOrId, STATUS_TONES, type PermissionAdjustment, type ReferenceKind } from '../model/permission-adjustment';

type Asking = 'uphold' | 'adjusted' | undefined;

const REFERENCE_OPTIONS = (Object.keys(REFERENCE_LABELS) as ReferenceKind[]).map((value) => ({ value, label: REFERENCE_LABELS[value] }));

/** The trigger the item carries: a personnel command (with the changed field), an org-structure command, or none (an expiry successor). */
function triggerItems(item: PermissionAdjustment) {
  return [
    ...(item.triggerPersonnelCommandId === undefined ? [] : [{ label: '人事命令', value: item.triggerPersonnelCommandId }]),
    ...(item.triggerField === undefined ? [] : [{ label: '触发字段', value: TRIGGER_FIELD_LABELS[item.triggerField] ?? item.triggerField }]),
    ...(item.triggerOrgStructureCommandId === undefined ? [] : [{ label: '组织结构命令', value: item.triggerOrgStructureCommandId }]),
  ];
}

export function PermissionAdjustmentDetailPage() {
  const { permissionAdjustmentId = '' } = useParams();
  const navigate = useNavigate();
  const session = useCurrentUser();
  const item = usePermissionAdjustment(permissionAdjustmentId);
  const uphold = useUpholdAdjustment(permissionAdjustmentId);
  const adjust = useMarkAdjusted(permissionAdjustmentId);
  const [asking, setAsking] = useState<Asking>();
  const [owner, setOwner] = useState('');
  const [reference, setReference] = useState<ReferenceKind>('authorizationRequestId');
  const [referenceId, setReferenceId] = useState('');
  const needsOwner = item.data?.kind === 'SENSITIVE_ROLE_REVIEW';

  return (
    <QueryBoundary query={item} errorTitle="待调整权限加载失败">
      {(detail) => {
        const close = () => {
          setAsking(undefined);
          setOwner('');
          setReferenceId('');
          uphold.reset();
          adjust.reset();
        };
        const version = versionOf(detail);
        const userId = detail.userId;
        const confirmUphold = (reason: string) => uphold.mutate({ version, reason, owner: owner.trim() }, { onSuccess: close });
        const confirmAdjusted = () => adjust.mutate({ version, reference, referenceId: referenceId.trim() }, { onSuccess: close });

        return (
          <>
            <PageTitle
              text={`${KIND_LABELS[detail.kind] ?? detail.kind} · ${aboutLabel(detail)}`}
              actions={
                <>
                  <StatusBadge tone={badgeOf(STATUS_LABELS, STATUS_TONES, detail.status).tone}>{badgeOf(STATUS_LABELS, STATUS_TONES, detail.status).label}</StatusBadge>
                  {detail.status === 'OPEN' ? (
                    <>
                      <Button onClick={() => setAsking('uphold')}>维持授权</Button>
                      <Button variant="default" onClick={() => setAsking('adjusted')}>标记已调整</Button>
                    </>
                  ) : null}
                </>
              }
            />
            <DescriptionList
              items={[
                { label: '类型', value: KIND_LABELS[detail.kind] ?? detail.kind },
                { label: '来源', value: ORIGIN_LABELS[detail.originKind] ?? detail.originKind },
                ...(userId === undefined ? [] : [{ label: '人员', value: nameOrId(detail.userName, userId) }, ...(session.can(ROLE_READ) ? [{ label: '账号', value: <TextLink href={accountPath(userId)} onNavigate={() => void navigate(accountPath(userId))}>查看账号并调整角色</TextLink> }] : [])]),
                ...(detail.roleId === undefined ? [] : [{ label: '角色', value: nameOrId(detail.roleName, detail.roleId) }]),
                ...(detail.orgUnitId === undefined ? [] : [{ label: '组织单元', value: nameOrId(detail.orgUnitName, detail.orgUnitId) }]),
                ...triggerItems(detail),
                ...(detail.businessOwnerEmployeeId === undefined ? [] : [{ label: '业务负责人', value: nameOrId(detail.businessOwnerName, detail.businessOwnerEmployeeId) }]),
                ...(detail.upholdExpiresAt === undefined ? [] : [{ label: '维持到期时间', value: formatDateTime(detail.upholdExpiresAt) }]),
              ]}
            />
            <ReasonDialog open={asking === 'uphold'} title="维持授权" confirmText="维持授权" label="理由（选填）" required={false} loading={uphold.isPending} confirmDisabled={needsOwner && owner.trim() === ''} error={<MutationNotice mutation={uphold} title="维持授权失败" />} onConfirm={confirmUphold} onClose={close}>
              {needsOwner ? <TextField label="业务负责人编号" value={owner} onChange={setOwner} /> : null}
            </ReasonDialog>
            <Dialog open={asking === 'adjusted'} title="标记已调整" confirmText="标记已调整" confirmLoading={adjust.isPending} confirmDisabled={referenceId.trim() === ''} onConfirm={confirmAdjusted} onClose={close}>
              <SelectField label="编号类型" value={reference} options={REFERENCE_OPTIONS} onChange={(value) => setReference(value as ReferenceKind)} />
              <TextField label={REFERENCE_LABELS[reference]} value={referenceId} onChange={setReferenceId} />
              <MutationNotice mutation={adjust} title="标记已调整失败" />
            </Dialog>
          </>
        );
      }}
    </QueryBoundary>
  );
}
