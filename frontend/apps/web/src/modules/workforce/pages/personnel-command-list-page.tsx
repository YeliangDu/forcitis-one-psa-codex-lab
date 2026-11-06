import { badgeOf, formatDateTime } from '@forcitis/core';

import { Button, PageTitle, SelectField, StatusBadge, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { optionsOf } from '../../../shared/options';
import { useNavigate, useSearchParams } from 'react-router';
import { RouterLink } from '../../../shared/router-link';
import { PagedTable } from '../../../shared/paged-table';
import { useEmployeeChoices, usePersonnelCommands } from '../hooks/use-workforce';
import { COMMAND_KIND_LABELS, COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, type PersonnelCommand, newPersonnelCommandPath, personnelCommandPath } from '../model/personnel-command';

const STATUS_OPTIONS = optionsOf(Object.entries(COMMAND_STATUS_LABELS), ([value]) => value, ([, label]) => label, '全部');
const KIND_OPTIONS = optionsOf(Object.entries(COMMAND_KIND_LABELS), ([value]) => value, ([, label]) => label, '全部');

export function PersonnelCommandListPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const employeeId = params.get('employeeId') ?? '';
  const [status, setStatus] = useState('');
  const [commandKind, setCommandKind] = useState('');
  const employees = useEmployeeChoices();
  const list = usePersonnelCommands({ status, commandKind, employeeId });

  const columns: DataColumn<PersonnelCommand>[] = [
    {
      key: 'employee',
      title: '员工',
      render: (command) => <RouterLink to={personnelCommandPath(command.personnelCommandId)}>{command.displayName}</RouterLink>,
    },
    { key: 'code', title: '员工编码', render: (command) => command.employeeCode },
    { key: 'kind', title: '命令类型', render: (command) => COMMAND_KIND_LABELS[command.commandKind] ?? command.commandKind },
    { key: 'date', title: '生效日期', render: (command) => command.effectiveDate },
    { key: 'status', title: '状态', render: (command) => {
      const badge = badgeOf(COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, command.status);
      return <StatusBadge tone={badge.tone}>{badge.label}</StatusBadge>;
    } },
    { key: 'created', title: '创建时间', render: (command) => formatDateTime(command.createdAt) },
  ];

  return (
    <>
      <PageTitle text="人事命令" actions={<Button onClick={() => void navigate(newPersonnelCommandPath(employeeId === '' ? undefined : employeeId))}>发起人事命令</Button>} />
      <SelectField label="状态" value={status} options={STATUS_OPTIONS} onChange={setStatus} />
      <SelectField label="命令类型" value={commandKind} options={KIND_OPTIONS} onChange={setCommandKind} />
      {employees.isSuccess ? (
        <SelectField
          label="员工"
          value={employeeId}
          options={optionsOf(employees.data, (employee) => employee.employeeId, (employee) => `${employee.displayName}（${employee.employeeCode}）`, '全部')}
          onChange={(value) => setParams(value === '' ? {} : { employeeId: value })}
        />
      ) : null}
      <PagedTable query={list} rows={list.commands} columns={columns} rowKey={(command) => command.personnelCommandId} emptyText="暂无人事命令" errorTitle="人事命令加载失败" />
    </>
  );
}
