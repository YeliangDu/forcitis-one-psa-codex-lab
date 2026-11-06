import { describeFailure } from '@forcitis/core';
import { Button, DataTable, LoadingState, Notice, type DataColumn } from '@forcitis/ui';

interface PagedQuery {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  fetchNextPage: () => unknown;
}

interface PagedTableProps<T extends object> {
  query: PagedQuery;
  /** All rows loaded so far; undefined until the first page is there. */
  rows: T[] | undefined;
  columns: DataColumn<T>[];
  rowKey: (row: T) => string;
  emptyText: string;
  errorTitle: string;
}

/** A list read page by page: loading state, failure notice, the table, then "显示更多" while a next page exists. */
export function PagedTable<T extends object>({ query, rows, columns, rowKey, emptyText, errorTitle }: PagedTableProps<T>) {
  return (
    <>
      {query.isPending ? <LoadingState /> : null}
      {query.isError ? <Notice tone="error" title={errorTitle}>{describeFailure(query.error)}</Notice> : null}
      {rows === undefined ? null : <DataTable columns={columns} rows={rows} rowKey={rowKey} emptyText={emptyText} />}
      {query.hasNextPage ? <Button variant="default" loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>显示更多</Button> : null}
    </>
  );
}
