import { describeFailure } from '@forcitis/core';
import { LoadingState, Notice, StatusBadge } from '@forcitis/ui';
import { useId } from 'react';
import { usePrimaryAssignmentHistory } from '../hooks/use-workforce';

/** Read-only: the primary assignments of the person, newest start first, as the server orders them; the failure of this section does not hide the rest of the page. */
export function PrimaryAssignmentHistorySection({ employeeId }: { employeeId: string }) {
  const headingId = useId();
  const history = usePrimaryAssignmentHistory(employeeId);

  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId}>主岗历史</h2>
      <PrimaryAssignmentHistoryBody headingId={headingId} history={history} />
    </section>
  );
}

function PrimaryAssignmentHistoryBody({ headingId, history }: { headingId: string; history: ReturnType<typeof usePrimaryAssignmentHistory> }) {
  if (history.isPending) return <LoadingState />;
  if (history.isError) return <Notice tone="error" title="主岗历史加载失败">{describeFailure(history.error)}</Notice>;
  if (history.data.items.length === 0) return <p>暂无主岗历史</p>;

  return (
    <table aria-labelledby={headingId}>
      <thead>
        <tr>
          <th scope="col">组织单元</th>
          <th scope="col">岗位</th>
          <th scope="col">生效日</th>
          <th scope="col">失效日</th>
          <th scope="col">状态</th>
        </tr>
      </thead>
      <tbody>
        {history.data.items.map((item) => (
          <tr key={`${item.effectiveFrom}:${item.orgUnitId}:${item.positionId ?? ''}`}>
            <td>{item.orgUnitName}</td>
            <td>{item.positionName ?? '—'}</td>
            <td>{item.effectiveFrom}</td>
            <td>{item.effectiveTo ?? '至今'}</td>
            <td>{item.current ? <StatusBadge tone="posted">当前</StatusBadge> : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
