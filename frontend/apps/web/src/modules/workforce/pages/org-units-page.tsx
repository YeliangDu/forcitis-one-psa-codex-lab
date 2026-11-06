import { Button, DataTable, LoadingState, Notice, PageTitle, StatusBadge, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { badgeOf, describeFailure } from '@forcitis/core';
import { useOrgUnits } from '../hooks/use-workforce';
import { ORG_UNIT_STATUS_LABELS, ORG_UNIT_STATUS_TONES, type OrgUnitNode, buildOrgTree } from '../model/org-unit';

const COLUMNS: DataColumn<OrgUnitNode>[] = [
  { key: 'name', title: '组织单元', render: (unit) => unit.name },
  { key: 'short', title: '部门简称', render: (unit) => unit.shortName ?? '—' },
  { key: 'code', title: '编码', render: (unit) => unit.code ?? '—' },
  { key: 'delivery', title: '交付单元', render: (unit) => (unit.deliveryUnit === true ? '是' : '否') },
  {
    key: 'status',
    title: '状态',
    render: (unit) => {
      const badge = badgeOf(ORG_UNIT_STATUS_LABELS, ORG_UNIT_STATUS_TONES, unit.status);
      return <StatusBadge tone={badge.tone}>{badge.label}</StatusBadge>;
    },
  },
];

export function OrgUnitsPage() {
  const [includeRevoked, setIncludeRevoked] = useState(false);
  const units = useOrgUnits(includeRevoked);

  return (
    <>
      <PageTitle
        text="组织浏览"
        actions={<Button variant="default" onClick={() => setIncludeRevoked((current) => !current)}>{includeRevoked ? '隐藏已撤销' : '显示已撤销'}</Button>}
      />
      {units.isPending ? <LoadingState /> : null}
      {units.isError ? <Notice tone="error" title="组织加载失败">{describeFailure(units.error)}</Notice> : null}
      {units.isSuccess ? <DataTable columns={COLUMNS} rows={buildOrgTree(units.data)} rowKey={(unit) => unit.orgUnitId} defaultExpandAll emptyText="暂无组织单元" /> : null}
    </>
  );
}
