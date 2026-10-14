import { vi } from 'vitest';

export interface StubRequest {
  method: string;
  url: string;
  headers: Headers;
  body: unknown;
}

export type StubReply = { status?: number; body?: unknown; headers?: Record<string, string> };
type StubHandler = StubReply | ((request: StubRequest) => StubReply);

/** Replaces fetch for one test. A table key is "METHOD path-tail": it answers a request of that method whose path ends with the tail at a segment boundary ("approval-tasks" is not "approval-tasks/t-1"); anything else is 404 and listed in `unmatched`. */
export function stubServer(table: Record<string, StubHandler>) {
  const calls: StubRequest[] = [];
  const unmatched: string[] = [];
  const entries = Object.entries(table).map(([key, handler]) => {
    const [method = 'GET', ...rest] = key.split(' ');
    return { method, fragment: rest.join(' '), handler };
  });

  vi.stubGlobal('fetch', async (input: string, init: RequestInit = {}) => {
    const method = (init.method ?? 'GET').toUpperCase();
    const request: StubRequest = {
      method,
      url: input,
      headers: new Headers(init.headers),
      body: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
    };
    calls.push(request);
    const path = new URL(input, 'http://localhost').pathname;
    const entry = entries.find((candidate) => candidate.method === method && path.endsWith(`/${candidate.fragment}`));
    if (entry === undefined) {
      unmatched.push(`${method} ${input}`);
      return new Response(null, { status: 404 });
    }
    const reply = typeof entry.handler === 'function' ? entry.handler(request) : entry.handler;
    const status = reply.status ?? 200;
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), { status, headers: { 'Content-Type': 'application/json', ...reply.headers } });
  });

  return { calls, unmatched };
}

export function problemBody(status: number, errorCode: string, extra: Record<string, unknown> = {}) {
  return { status, errorCode, title: 'x', ...extra };
}
