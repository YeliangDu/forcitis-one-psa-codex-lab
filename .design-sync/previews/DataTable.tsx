import { Button, DataTable, StatusBadge, type DataColumn } from '@forcitis/ui';

interface Rule { type: string; code: string; pattern: string; reset: string; start: number; enabled: boolean }
const rules: Rule[] = [
  { type: '报价单', code: 'QT', pattern: 'QT-{法人}-{yyyyMM}-{seq4}', reset: '按月', start: 1, enabled: true },
  { type: '合同', code: 'CT', pattern: 'CT-{法人}-{yyyy}-{seq4}', reset: '按年', start: 1, enabled: true },
  { type: '项目', code: 'PRJ', pattern: 'PRJ-{yyyy}-{seq3}', reset: '按年', start: 1, enabled: false },
];
const columns: DataColumn<Rule>[] = [
  { key: 'type', title: '单据类型', render: (r) => r.type },
  { key: 'code', title: '单据类型代码', render: (r) => r.code },
  { key: 'pattern', title: '编号模板', render: (r) => <code>{r.pattern}</code> },
  { key: 'reset', title: '流水号重置周期', render: (r) => r.reset },
  { key: 'start', title: '起始流水号', render: (r) => r.start },
  { key: 'status', title: '状态', render: (r) => <StatusBadge tone={r.enabled ? 'posted' : 'neutral'}>{r.enabled ? '启用' : '停用'}</StatusBadge> },
  { key: 'ops', title: '操作', render: () => <Button variant="default">编辑</Button> },
];

export const NumberingRules = () => <DataTable columns={columns} rows={rules} rowKey={(r) => r.code} />;

export const Empty = () => <DataTable columns={columns} rows={[]} rowKey={(r) => r.code} emptyText="暂无编号规则" />;

export const Loading = () => <DataTable columns={columns} rows={rules} rowKey={(r) => r.code} loading />;
