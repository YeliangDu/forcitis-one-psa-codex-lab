import { badgeOf, describeFailure } from '@forcitis/core';
import { Button, DataTable, LoadingState, Notice, PageTitle, SelectField, StatusBadge, TextLink, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useEmployees, useOrgUnits } from '../hooks/use-workforce';
import { EMPLOYMENT_FILTERS, EMPLOYMENT_STATUS_LABELS, EMPLOYMENT_STATUS_TONES, type EmployeeRoster, type EmploymentFilter, employeePath, employmentFilterOf } from '../model/employee';

export function EmployeeRosterPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState<EmploymentFilter>('EMPLOYED');
  const [orgUnitId, setOrgUnitId] = useState('');
  const units = useOrgUnits(false);
  const list = useEmployees(status, orgUnitId === '' ? undefined : orgUnitId);

  const columns: DataColumn<EmployeeRoster>[] = [
    { key: 'code', title: '员工编码', render: (employee) => employee.employeeCode },
    {
      key: 'name',
      title: '姓名',
      render: (employee) => <TextLink href={employeePath(employee.employeeId)} onNavigate={() => void navigate(employeePath(employee.employeeId))}>{employee.displayName}</TextLink>,
    },
    { key: 'unit', title: '组织单元', render: (employee) => employee.assignments.map((assignment) => assignment.orgUnitName).join('、') || '—' },
    { key: 'position', title: '岗位', render: (employee) => employee.assignments.map((assignment) => assignment.positionName).filter((name) => name != null).join('、') || '—' },
    { key: 'hire', title: '入职日期', render: (employee) => employee.hireDate ?? '—' },
    {
      key: 'status',
      title: '在职状态',
      render: (employee) => {
        const badge = badgeOf(EMPLOYMENT_STATUS_LABELS, EMPLOYMENT_STATUS_TONES, employee.employmentStatus);
        return <StatusBadge tone={badge.tone}>{badge.label}</StatusBadge>;
      },
    },
  ];

  return (
    <>
      <PageTitle text="员工名册" />
      <SelectField label="在职状态" value={status} options={EMPLOYMENT_FILTERS} onChange={(value) => setStatus(employmentFilterOf(value))} />
      {units.isSuccess ? (
        <SelectField label="组织单元" value={orgUnitId} options={[{ value: '', label: '全部' }, ...units.data.map((unit) => ({ value: unit.orgUnitId, label: unit.name }))]} onChange={setOrgUnitId} />
      ) : null}
      {list.isPending ? <LoadingState /> : null}
      {list.isError ? <Notice tone="error" title="员工名册加载失败">{describeFailure(list.error)}</Notice> : null}
      {list.employees === undefined ? null : <DataTable columns={columns} rows={list.employees} rowKey={(employee) => employee.employeeId} emptyText="暂无员工" />}
      {list.hasNextPage ? <Button variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</Button> : null}
    </>
  );
}
