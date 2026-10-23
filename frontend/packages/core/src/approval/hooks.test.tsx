import { versionOf } from '@forcitis/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { VERSION_HEADER, problemBody, stubServer, taskDetailBody } from '../testing';
import { fetchTask } from './api';
import { useInbox, useTaskActions } from './hooks';

function wrapper() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('办理动作的幂等键', () => {
  async function setup(decide: (attempt: number) => Response | Error) {
    const decides: Headers[] = [];
    vi.stubGlobal('fetch', async (input: string, init: RequestInit = {}) => {
      if (init.method === 'GET') return new Response(JSON.stringify(taskDetailBody()), { status: 200, headers: VERSION_HEADER });
      decides.push(new Headers(init.headers));
      const answer = decide(decides.length);
      if (answer instanceof Error) throw answer;
      return answer;
    });
    const task = await fetchTask('t-1');
    const view = renderHook(() => useTaskActions('t-1', task, () => undefined), { wrapper: wrapper() });
    return { decides, view };
  }

  const ok = () => new Response(JSON.stringify({ instanceId: 'i-1', status: 'APPROVED' }), { status: 200 });

  it('断网后重试同一次办理：两次请求用同一个键，版本也相同', async () => {
    const { decides, view } = await setup((attempt) => (attempt === 1 ? new TypeError('Failed to fetch') : ok()));

    await act(async () => { await view.result.current.approve.mutateAsync('工时相符').catch(() => undefined); });
    await act(async () => { await view.result.current.approve.mutateAsync('工时相符'); });

    expect(decides).toHaveLength(2);
    expect(decides[0]?.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(decides[1]?.get('Idempotency-Key')).toBe(decides[0]?.get('Idempotency-Key'));
    expect(decides[1]?.get('If-Match')).toBe('"1"');
  });

  it('办理成功后，下一次办理换新键；用户改了内容，也换新键', async () => {
    const { decides, view } = await setup((attempt) => (attempt === 2 ? new TypeError('Failed to fetch') : ok()));

    await act(async () => { await view.result.current.approve.mutateAsync('甲'); });
    await act(async () => { await view.result.current.approve.mutateAsync('甲').catch(() => undefined); });
    await act(async () => { await view.result.current.approve.mutateAsync('乙'); });

    const keys = decides.map((headers) => headers.get('Idempotency-Key'));
    expect(new Set(keys).size).toBe(3);
  });

  it('版本被拒绝（412）后再办理是新的一次意图：换新键，并重新读取任务', async () => {
    let reads = 0;
    const decides: Headers[] = [];
    vi.stubGlobal('fetch', async (input: string, init: RequestInit = {}) => {
      if (init.method === 'GET') {
        reads += 1;
        return new Response(JSON.stringify(taskDetailBody()), { status: 200, headers: { ETag: `"${reads}"` } });
      }
      decides.push(new Headers(init.headers));
      return decides.length === 1
        ? new Response(JSON.stringify(problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH')), { status: 412 })
        : new Response(JSON.stringify({ instanceId: 'i-1', status: 'APPROVED' }), { status: 200 });
    });
    const task = await fetchTask('t-1');
    expect(versionOf(task)).toBe('"1"');
    const view = renderHook(() => useTaskActions('t-1', task, () => undefined), { wrapper: wrapper() });

    await act(async () => { await view.result.current.approve.mutateAsync('甲').catch(() => undefined); });
    await act(async () => { await view.result.current.approve.mutateAsync('甲'); });

    expect(decides[1]?.get('Idempotency-Key')).not.toBe(decides[0]?.get('Idempotency-Key'));
  });
});

describe('待办的刷新', () => {
  beforeEach(() => vi.useFakeTimers({ shouldAdvanceTime: true }));
  afterEach(() => vi.useRealTimers());

  const page = (items: string[], nextCursor?: string) => ({
    items: items.map((id) => ({ category: 'TODO', overdue: false, claimed: false, taskId: id, title: id, processKey: 'cost_timesheet_approval', documentId: id })),
    newCount: 3, noticeCount: 0, totalCount: 3,
    ...(nextCursor === undefined ? {} : { nextCursor }),
  });

  it('每 60 秒只重新读第一页，已经翻开的后面几页收回', async () => {
    const server = stubServer({
      'GET approval-tasks': (request) => ({ body: request.url.includes('cursor=c2') ? page(['c']) : page(['a', 'b'], 'c2') }),
    });
    const view = renderHook(() => useInbox(), { wrapper: wrapper() });
    await waitFor(() => expect(view.result.current.inbox?.items).toHaveLength(2));
    await act(async () => { await view.result.current.fetchNextPage(); });
    await waitFor(() => expect(view.result.current.inbox?.items).toHaveLength(3));
    const before = server.calls.length;

    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });

    await waitFor(() => expect(server.calls.length).toBe(before + 1));
    expect(server.calls[before]?.url).not.toContain('cursor=');
    await waitFor(() => expect(view.result.current.inbox?.items).toHaveLength(2));
  });

  it('窗口重新获得焦点时刷新', async () => {
    const server = stubServer({ 'GET approval-tasks': { body: page(['a']) } });
    const view = renderHook(() => useInbox(), { wrapper: wrapper() });
    await waitFor(() => expect(view.result.current.inbox?.items).toHaveLength(1));
    const before = server.calls.length;

    act(() => { window.dispatchEvent(new Event('focus')); });

    await waitFor(() => expect(server.calls.length).toBe(before + 1));
  });
});
