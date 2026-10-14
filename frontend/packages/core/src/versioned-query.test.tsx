import { versionOf } from '@forcitis/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchTask } from './approval/api';
import { taskDetailBody } from './testing';
import { useVersionedQuery } from './versioned-query';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('useVersionedQuery', () => {
  it('两次应答内容相同、ETag 不同：取到的是新版本', async () => {
    let reads = 0;
    vi.stubGlobal('fetch', async () => {
      reads += 1;
      return new Response(JSON.stringify(taskDetailBody()), { status: 200, headers: { ETag: `"${reads}"` } });
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
    const view = renderHook(() => useVersionedQuery({ queryKey: ['task'], queryFn: () => fetchTask('t-1') }), { wrapper });

    await waitFor(() => expect(versionOf(view.result.current.data)).toBe('"1"'));
    await act(async () => { await client.invalidateQueries({ queryKey: ['task'] }); });

    await waitFor(() => expect(versionOf(view.result.current.data)).toBe('"2"'));
  });

  it('select 不能用：它会交出另一个对象，版本就丢了', () => {
    const options = { queryKey: ['task'], queryFn: () => fetchTask('t-1') };
    // @ts-expect-error useVersionedQuery takes no select
    const rejected: Parameters<typeof useVersionedQuery>[0] = { ...options, select: (data: unknown) => data };
    expect(rejected).toBeDefined();
  });
});
