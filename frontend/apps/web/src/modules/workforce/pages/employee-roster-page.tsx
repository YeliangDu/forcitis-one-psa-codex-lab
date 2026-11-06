import { PageTitle, SelectField, StatusBadge, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { optionsOf } from '../../../shared/options';
import { RouterLink } from '../../../shared/router-link';
import { PagedTable } from '../../../shared/paged-table';
import { useEmployees, useOrgUnits } from '../hooks/use-workforce';
import { EMPLOYMENT_FILTERS, EMPLOYMENT_STATUS_LABELS, type EmployeeRoster, type EmploymentFilter, employeePath, employmentFilterOf } from '../model/employee';

export function EmployeeRosterPage() {
  const [status, setStatus] = useState<EmploymentFilter>('EMPLOYED');
  const [orgUnitId, setOrgUnitId] = useState('');
  const units = useOrgUnits(false);
  const list = useEmployees(status, orgUnitId === '' ? undefined : orgUnitId);

  const columns: DataColumn<EmployeeRoster>[] = [
    { key: 'code', title: '员工编码', render: (employee) => employee.employeeCode },
    {
      key: 'name',
      title: '姓名',
      render: (employee) => <RouterLink to={employeePath(employee.employeeId)}>{employee.displayName}</RouterLink>,
    },
    { key: 'unit', title: '组织单元', render: (employee) => employee.assignments.map((assignment) => assignment.orgUnitName).join('、') || '—' },
    { key: 'position', title: '岗位', render: (employee) => employee.assignments.map((assignment) => assignment.positionName).filter((name) => name != null).join('、') || '—' },
    { key: 'hire', title: '入职日期', render: (employee) => employee.hireDate ?? '—' },
    {
      key: 'status',
      title: '在职状态',
      render: (employee) => <StatusBadge tone={employee.employmentStatus === 'EMPLOYED' ? 'posted' : 'neutral'}>{EMPLOYMENT_STATUS_LABELS[employee.employmentStatus] ?? employee.employmentStatus}</StatusBadge>,
    },
  ];

  return (
    <>
      <PageTitle text="员工名册" />
      <SelectField label="在职状态" value={status} options={EMPLOYMENT_FILTERS} onChange={(value) => setStatus(employmentFilterOf(value))} />
      {units.isSuccess ? (
        <SelectField label="组织单元" value={orgUnitId} options={optionsOf(units.data, (unit) => unit.orgUnitId, (unit) => unit.name, '全部')} onChange={setOrgUnitId} />
      ) : null}
      <PagedTable query={list} rows={list.employees} columns={columns} rowKey={(employee) => employee.employeeId} emptyText="暂无员工" errorTitle="员工名册加载失败" />
    </>
  );
}
