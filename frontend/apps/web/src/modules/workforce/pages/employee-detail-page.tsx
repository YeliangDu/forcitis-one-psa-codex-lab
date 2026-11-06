import { DataTable, DescriptionList, PageTitle, StatusBadge, TextLink, type DataColumn } from '@forcitis/ui';
import { useNavigate, useParams } from 'react-router';
import { QueryBoundary } from '../../../shared/query-boundary';
import { useEmployee } from '../hooks/use-workforce';
import { ASSIGNMENT_KIND_LABELS, employmentStatusLabel, type EmployeeDetail } from '../model/employee';
import { PrimaryAssignmentHistorySection } from './primary-assignment-history-section';
import { employeeCommandsPath, newPersonnelCommandPath } from '../model/personnel-command';

type Assignment = EmployeeDetail['assignments'][number];

const ASSIGNMENT_COLUMNS: DataColumn<Assignment>[] = [
  { key: 'kind', title: '任职类型', render: (assignment) => ASSIGNMENT_KIND_LABELS[assignment.assignmentKind] ?? assignment.assignmentKind },
  { key: 'unit', title: '组织单元', render: (assignment) => assignment.orgUnitName },
  { key: 'position', title: '岗位', render: (assignment) => assignment.positionName ?? '—' },
  { key: 'reportsTo', title: '直属上级', render: (assignment) => assignment.reportsToDisplayName ?? '—' },
];

export function EmployeeDetailPage() {
  const { employeeId = '' } = useParams();
  const navigate = useNavigate();
  const employee = useEmployee(employeeId);

  return (
    <QueryBoundary query={employee} errorTitle="员工加载失败">
      {(detail) => {
        return (
          <>
            <PageTitle text={detail.displayName} actions={<StatusBadge tone={detail.employmentStatus === 'EMPLOYED' && !detail.onUnpaidLeave ? 'posted' : 'neutral'}>{employmentStatusLabel(detail.employmentStatus, detail.onUnpaidLeave)}</StatusBadge>} />
            <DescriptionList
              items={[
                { label: '员工编码', value: detail.employeeCode },
                { label: '姓名', value: detail.displayName },
                { label: '入职日期', value: detail.hireDate ?? '—' },
              ]}
            />
            <p>
              <TextLink href={employeeCommandsPath(employeeId)} onNavigate={() => void navigate(employeeCommandsPath(employeeId))}>人事命令</TextLink>
              {' '}
              <TextLink href={newPersonnelCommandPath(employeeId)} onNavigate={() => void navigate(newPersonnelCommandPath(employeeId))}>发起人事命令</TextLink>
            </p>
            <DataTable columns={ASSIGNMENT_COLUMNS} rows={detail.assignments} rowKey={(assignment) => `${assignment.assignmentKind}:${assignment.orgUnitId}:${assignment.positionId ?? ''}`} emptyText="暂无任职" />
            <PrimaryAssignmentHistorySection employeeId={employeeId} />
          </>
        );
      }}
    </QueryBoundary>
  );
}
