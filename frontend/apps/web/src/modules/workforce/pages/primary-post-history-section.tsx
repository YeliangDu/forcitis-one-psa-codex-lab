import { describeFailure } from '@forcitis/core';
import { LoadingState, Notice, StatusBadge } from '@forcitis/ui';
import { useId } from 'react';
import { usePrimaryPostHistory } from '../hooks/use-workforce';

/** Read-only: the primary posts of the person, newest start first, as the server orders them; the failure of this section does not hide the rest of the page. */
export function PrimaryPostHistorySection({ employeeId }: { employeeId: string }) {
  const headingId = useId();
  const history = usePrimaryPostHistory(employeeId);

  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId}>主职历史</h2>
      <PrimaryPostHistoryBody headingId={headingId} history={history} />
    </section>
  );
}

function PrimaryPostHistoryBody({ headingId, history }: { headingId: string; history: ReturnType<typeof usePrimaryPostHistory> }) {
  if (history.isPending) return <LoadingState />;
  if (history.isError) return <Notice tone="error" title="主职历史加载失败">{describeFailure(history.error)}</Notice>;
  if (history.data.items.length === 0) return <p>暂无主职历史</p>;

  return (
    <table aria-labelledby={headingId}>
      <thead>
        <tr>
          <th scope="col">组织单元</th>
          <th scope="col">岗位</th>
          <th scope="col">生效日</th>
          <th scope="col">失效日（不含）</th>
          <th scope="col">状态</th>
        </tr>
      </thead>
      <tbody>
        {history.data.items.map((post) => (
          <tr key={`${post.effectiveFrom}:${post.orgUnitId}:${post.positionId ?? ''}`}>
            <td>{post.orgUnitName}</td>
            <td>{post.positionName ?? '—'}</td>
            <td>{post.effectiveFrom}</td>
            <td>{post.effectiveTo ?? '至今'}</td>
            <td>{post.current ? <StatusBadge tone="posted">当前</StatusBadge> : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
