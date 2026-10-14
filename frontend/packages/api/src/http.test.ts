import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createHttpClient } from './http';

interface Recorded {
  url: string;
  init: RequestInit;
}

function clientWith(reply: { status: number; body?: unknown }, options: { cookie?: string; handlers?: { onUnauthenticated?: () => void; onForbidden?: () => void } } = {}) {
  const calls: Recorded[] = [];
  const client = createHttpClient({
    baseUrl: 'https://one.example',
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), { status: reply.status });
    },
    readCookie: (name) => (name === 'XSRF-TOKEN' ? options.cookie : undefined),
    handlers: () => options.handlers ?? {},
  });
  return { client, calls };
}

function headersOf(recorded: Recorded | undefined): Headers {
  return new Headers(recorded?.init.headers);
}

describe('HTTP client', () => {
  afterEach(() => vi.restoreAllMocks());

  it('always sends the session cookie', async () => {
    const { client, calls } = clientWith({ status: 200, body: { ok: true } });
    await client.request('get', '/things');
    expect(calls[0]?.init.credentials).toBe('include');
  });

  it('writes the XSRF-TOKEN cookie back as the X-XSRF-TOKEN header on every unsafe method', async () => {
    const { client, calls } = clientWith({ status: 200, body: {} }, { cookie: 'token with/special=chars' });
    for (const method of ['post', 'put', 'patch', 'delete'] as const) {
      await client.request(method, '/things', { body: method === 'delete' ? undefined : { a: 1 } });
    }
    expect(calls.map((call) => headersOf(call).get('X-XSRF-TOKEN'))).toEqual(Array(4).fill('token with/special=chars'));
  });

  it('does not send the CSRF header on a read, and sends none when there is no cookie yet', async () => {
    const reading = clientWith({ status: 200, body: {} }, { cookie: 'abc' });
    await reading.client.request('get', '/things');
    expect(headersOf(reading.calls[0]).has('X-XSRF-TOKEN')).toBe(false);

    const noCookie = clientWith({ status: 200, body: {} });
    await noCookie.client.request('post', '/things', { body: {} });
    expect(headersOf(noCookie.calls[0]).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('puts the token in a header only, never in the URL', async () => {
    const { client, calls } = clientWith({ status: 200, body: {} }, { cookie: 'secret-token' });
    await client.request('post', '/things/{id}:act', { path: { id: 7 }, query: { page: 2 }, body: {} });
    expect(calls[0]?.url).toBe('https://one.example/things/7:act?page=2');
    expect(calls[0]?.url).not.toContain('secret-token');
  });

  it('encodes path parameters', async () => {
    const { client, calls } = clientWith({ status: 200, body: {} });
    await client.request('get', '/things/{id}', { path: { id: 'a/b c' } });
    expect(calls[0]?.url).toBe('https://one.example/things/a%2Fb%20c');
  });

  it('on 401 calls the unauthenticated handler (login redirect) and still rejects with the error', async () => {
    const onUnauthenticated = vi.fn();
    const onForbidden = vi.fn();
    const { client } = clientWith({ status: 401, body: { errorCode: 'X' } }, { handlers: { onUnauthenticated, onForbidden } });
    await expect(client.request('get', '/things')).rejects.toMatchObject({ status: 401, code: 'X' });
    expect(onUnauthenticated).toHaveBeenCalledTimes(1);
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it('on 403 calls the forbidden handler (no-permission page)', async () => {
    const onUnauthenticated = vi.fn();
    const onForbidden = vi.fn();
    const { client } = clientWith({ status: 403 }, { handlers: { onUnauthenticated, onForbidden } });
    await expect(client.request('post', '/things', { body: {} })).rejects.toBeInstanceOf(ApiError);
    expect(onForbidden).toHaveBeenCalledTimes(1);
    expect(onUnauthenticated).not.toHaveBeenCalled();
  });

  it('leaves 401 and 403 to the caller when the call opts out of the guards (login calls)', async () => {
    const onUnauthenticated = vi.fn();
    const onForbidden = vi.fn();
    const unauthorized = clientWith({ status: 401 }, { handlers: { onUnauthenticated, onForbidden } });
    await expect(unauthorized.client.request('post', '/login', { body: {} }, { guards: false })).rejects.toMatchObject({ status: 401 });
    const forbidden = clientWith({ status: 403 }, { handlers: { onUnauthenticated, onForbidden } });
    await expect(forbidden.client.request('post', '/login', { body: {} }, { guards: false })).rejects.toMatchObject({ status: 403 });
    expect(onUnauthenticated).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it('does not treat other failures as redirects and keeps the business error code', async () => {
    const onUnauthenticated = vi.fn();
    const onForbidden = vi.fn();
    const { client } = clientWith({ status: 409, body: { errorCode: 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED', message: 'x' } }, { handlers: { onUnauthenticated, onForbidden } });
    await expect(client.request('post', '/things', { body: {} })).rejects.toMatchObject({ status: 409, code: 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED' });
    expect(onUnauthenticated).not.toHaveBeenCalled();
    expect(onForbidden).not.toHaveBeenCalled();
  });

  it('returns undefined for an empty success body and sends JSON only when there is a body', async () => {
    const { client, calls } = clientWith({ status: 204 });
    await expect(client.request('post', '/things:claim')).resolves.toBeUndefined();
    expect(headersOf(calls[0]).has('Content-Type')).toBe(false);
    await client.request('post', '/things', { body: { a: 1 } });
    expect(headersOf(calls[1]).get('Content-Type')).toBe('application/json');
    expect(calls[1]?.init.body).toBe('{"a":1}');
  });

  it('reads the XSRF-TOKEN cookie of the document by default', async () => {
    document.cookie = 'XSRF-TOKEN=from-cookie; path=/';
    const calls: Recorded[] = [];
    const client = createHttpClient({ fetchImpl: async (url, init) => { calls.push({ url, init }); return new Response('{}'); } });
    await client.request('post', '/things', { body: {} });
    expect(headersOf(calls[0]).get('X-XSRF-TOKEN')).toBe('from-cookie');
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });
});
