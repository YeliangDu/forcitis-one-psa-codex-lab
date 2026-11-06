import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { PagedTable } from './paged-table';

interface Row { id: string; name: string }

const columns = [{ key: 'name', title: '姓名', render: (row: Row) => row.name }];
const idle = { isPending: false, isError: false, error: null as unknown, hasNextPage: false, isFetchingNextPage: false, fetchNextPage: () => undefined };

function table(query: Partial<typeof idle>, rows: Row[] | undefined) {
  return <PagedTable query={{ ...idle, ...query }} rows={rows} columns={columns} rowKey={(row) => row.id} emptyText="暂无数据" errorTitle="加载失败" />;
}

describe('PagedTable', () => {
  it('shows only the loading state while the first page is pending', () => {
    render(table({ isPending: true }, undefined));
    expect(screen.getByRole('status', { name: '加载中' })).toBeTruthy();
    expect(screen.queryByText('姓名')).toBeNull();
    expect(screen.queryByText('暂无数据')).toBeNull();
    expect(screen.queryByText('加载失败')).toBeNull();
  });

  it('shows the error title and no table when the first page failed', () => {
    render(table({ isError: true, error: new Error('boom') }, undefined));
    expect(screen.getByText('加载失败')).toBeTruthy();
    expect(screen.queryByText('姓名')).toBeNull();
  });

  it('shows the rows, or the empty text when there are none', () => {
    const { rerender } = render(table({}, [{ id: '1', name: '张三' }]));
    expect(screen.getByText('张三')).toBeTruthy();
    expect(screen.queryByText('显示更多')).toBeNull();
    rerender(table({}, []));
    expect(screen.getByText('暂无数据')).toBeTruthy();
  });

  it('offers 显示更多 only while a next page exists and asks for it on click', () => {
    const fetchNextPage = vi.fn();
    render(table({ hasNextPage: true, fetchNextPage }, [{ id: '1', name: '张三' }]));
    fireEvent.click(screen.getByText('显示更多'));
    expect(fetchNextPage).toHaveBeenCalledTimes(1);
  });
});
