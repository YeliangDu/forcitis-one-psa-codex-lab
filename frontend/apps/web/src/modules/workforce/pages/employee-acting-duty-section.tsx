import { describeFailure, useCurrentUser } from '@forcitis/core';
import { LoadingState, Notice } from '@forcitis/ui';
import { useId } from 'react';
import { QueryBoundary } from '../../../shared/query-boundary';
import { RouterLink } from '../../../shared/router-link';
import { useActingDuties, useCoveredActingDuties, useEmployeeChoices, useOrgUnits, usePositionChoices } from '../hooks/use-workforce';
import { ACTING_END_CAUSE_LABELS, ACTING_STATE_LABELS, PERSONNEL_COMMAND_CREATE, actingCommandPath, type ActingDuty } from '../model/acting-duty';

export function EmployeeActingDutySection({ employeeId }: { employeeId: string }) {
  const headingId = useId();
  const duties = useActingDuties(employeeId);
  const session = useCurrentUser();
  const mayCreate = session.can(PERSONNEL_COMMAND_CREATE);

  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId}>代理职务</h2>
      {mayCreate ? <RouterLink to={actingCommandPath(employeeId, 'ACTING_START')}>开始代理</RouterLink> : null}
      <QueryBoundary query={duties} errorTitle="代理职务加载失败">
        {(data) => session.status === 'loading' ? <LoadingState /> : (
          <ActingDutyTable headingId={headingId} employeeId={employeeId} duties={data.items} mayCreate={mayCreate} />
        )}
      </QueryBoundary>
      <CoveredActingDuties employeeId={employeeId} />
    </section>
  );
}

function ActingDutyTable({ headingId, employeeId, duties, mayCreate }: { headingId: string; employeeId: string; duties: ActingDuty[]; mayCreate: boolean }) {
  const rows = duties.filter((duty) => duty.state === 'ACTIVE' || duty.state === 'ENDED');
  const units = useOrgUnits(true, rows.length > 0);
  const positions = usePositionChoices(rows.some((duty) => duty.coveredPositionId != null));
  const names = [units, ...(rows.some((duty) => duty.coveredPositionId != null) ? [positions] : [])];
  if (rows.length === 0) return <p>暂无代理职务</p>;
  if (names.some((query) => query.isPending)) return <LoadingState />;

  return (
    <>
      {names.filter((query) => query.isError).map((query, index) => (
        <Notice key={index} tone="error" title="被代理对象加载失败">{describeFailure(query.error)}</Notice>
      ))}
      <table aria-labelledby={headingId}>
        <thead>
          <tr>
            <th scope="col">被代理组织单元</th><th scope="col">被代理岗位</th><th scope="col">被代理员工</th>
            <th scope="col">生效日</th><th scope="col">最后一天</th><th scope="col">状态</th>
            <th scope="col">结束原因</th><th scope="col">原因</th><th scope="col">操作</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((duty) => (
            <tr key={duty.actingDutyId}>
              <td>{units.data?.find((unit) => unit.orgUnitId === duty.coveredOrgUnitId)?.name ?? '—'}</td>
              <td>{positions.data?.find((position) => position.positionId === duty.coveredPositionId)?.name ?? '—'}</td>
              {/* A redacted name must stay redacted even if another employee list is cached. */}
              <td>{duty.coveredEmployeeName ?? '—'}</td>
              <td>{duty.firstDay}</td><td>{duty.lastDay ?? '—'}</td>
              <td>{ACTING_STATE_LABELS[duty.state] ?? '—'}</td>
              <td>{duty.endCause == null ? '—' : (ACTING_END_CAUSE_LABELS[duty.endCause] ?? '—')}</td>
              <td>{duty.reason}</td>
              <td>{mayCreate && duty.state === 'ACTIVE' ? (
                <>
                  <RouterLink to={actingCommandPath(employeeId, 'ACTING_END', duty.actingDutyId)}>提前结束</RouterLink>
                  {' '}
                  <RouterLink to={actingCommandPath(employeeId, 'ACTING_EXTEND', duty.actingDutyId)}>延期</RouterLink>
                </>
              ) : '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function CoveredActingDuties({ employeeId }: { employeeId: string }) {
  const coverage = useCoveredActingDuties(employeeId);
  const active = coverage.data?.items.filter((duty) => duty.state === 'ACTIVE') ?? [];
  const employees = useEmployeeChoices(active.length > 0);

  return (
    <QueryBoundary query={coverage} errorTitle="被代理职务加载失败">
      {() => {
        if (active.length === 0) return null;
        if (employees.isPending) return <LoadingState />;
        return (
          <>
            {employees.isError ? <Notice tone="error" title="员工加载失败">{describeFailure(employees.error)}</Notice> : null}
            {active.map((duty) => (
              <p key={duty.actingDutyId}>当前由{employees.data?.find((employee) => employee.employeeId === duty.employeeId)?.displayName ?? '—'}代理</p>
            ))}
          </>
        );
      }}
    </QueryBoundary>
  );
}
