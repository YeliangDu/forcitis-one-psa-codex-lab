import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, MissingVersionError, createHttpClient } from './http';

interface Recorded {
  url: string;
  init: RequestInit;
}

type Reply = { status: number; body?: unknown; headers?: Record<string, string> };

function clientWith(reply: Reply | (() => Reply), options: { cookie?: string; handlers?: { onUnauthenticated?: () => void; onForbidden?: () => void }; newKey?: () => string } = {}) {
  const calls: Recorded[] = [];
  const client = createHttpClient({
    baseUrl: 'https://one.example',
    fetchImpl: async (url, init) => {
      calls.push({ url, init });
      const answer = typeof reply === 'function' ? reply() : reply;
      return new Response(answer.body === undefined ? null : JSON.stringify(answer.body), { status: answer.status, ...(answer.headers === undefined ? {} : { headers: answer.headers }) });
    },
    readCookie: (name) => (name === 'XSRF-TOKEN' ? options.cookie : undefined),
    handlers: () => options.handlers ?? {},
    ...(options.newKey === undefined ? {} : { newKey: options.newKey }),
  });
  return { client, calls };
}

function problem(status: number, errorCode: string) {
  return { status, errorCode, title: 'x', correlationId: 'c-1' };
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
    const { client } = clientWith({ status: 401, body: problem(401, 'PLATFORM_WEB_UNAUTHENTICATED') }, { handlers: { onUnauthenticated, onForbidden } });
    await expect(client.request('get', '/things')).rejects.toMatchObject({ status: 401, code: 'PLATFORM_WEB_UNAUTHENTICATED' });
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
    const { client } = clientWith({ status: 409, body: problem(409, 'PLATFORM_APPROVAL_TASK_ALREADY_CLAIMED') }, { handlers: { onUnauthenticated, onForbidden } });
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

  it('keeps the whole ProblemDetail of an error answer, with its correlation id', async () => {
    const { client } = clientWith({ status: 422, body: { ...problem(422, 'PLATFORM_APPROVAL_REASON_REQUIRED'), detail: 'reason is required', extra: null } });
    const failure = await client.request('post', '/things', { body: {} }).catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).problem).toMatchObject({ errorCode: 'PLATFORM_APPROVAL_REASON_REQUIRED', status: 422, correlationId: 'c-1' });
    expect((failure as ApiError).malformedProblem).toBe(false);
  });

  it('flags an error body that is not a ProblemDetail instead of trusting its fields', async () => {
    for (const body of [{ errorCode: 'PLATFORM_WEB_UNEXPECTED' }, { errorCode: 'NOT_A_CODE', status: 500 }, { outcome: 'NOT_HELD' }]) {
      const { client } = clientWith({ status: 500, body });
      const failure = (await client.request('get', '/things').catch((error: unknown) => error)) as ApiError;
      expect(failure.status).toBe(500);
      expect(failure.code).toBeUndefined();
      expect(failure.malformedProblem).toBe(true);
    }
    const empty = (await clientWith({ status: 502 }).client.request('get', '/things').catch((error: unknown) => error)) as ApiError;
    expect(empty.malformedProblem).toBe(false);
    expect(empty.code).toBeUndefined();
  });

  describe('If-Match', () => {
    const versioned = { versioned: true } as const;

    it('sends the ETag of the last read of the resource, also for an :action on it', async () => {
      const { client, calls } = clientWith(() => ({ status: 200, body: {}, headers: { ETag: '"7"' } }));
      await client.request('get', '/tasks/{id}', { path: { id: 5 } });
      await client.request('post', '/tasks/{id}:decide', { path: { id: 5 }, body: {} }, {}, undefined, versioned);
      expect(headersOf(calls[1]).get('If-Match')).toBe('"7"');
    });

    it('refuses to send a versioned update for a resource it never read, and sends nothing', async () => {
      const { client, calls } = clientWith({ status: 200, body: {} });
      await expect(client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, {}, undefined, versioned)).rejects.toBeInstanceOf(MissingVersionError);
      expect(calls).toHaveLength(0);
    });

    it('does not reuse a version for another resource', async () => {
      const { client } = clientWith({ status: 200, body: {}, headers: { ETag: '"1"' } });
      await client.request('get', '/tasks/{id}', { path: { id: 5 } });
      await expect(client.request('put', '/tasks/{id}', { path: { id: 6 }, body: {} }, {}, undefined, versioned)).rejects.toBeInstanceOf(MissingVersionError);
    });

    it('forgets the version after a 412 and after a change that brought no new ETag, so a stale one is never sent twice', async () => {
      let status = 200;
      const { client, calls } = clientWith(() => ({ status, body: status === 200 ? {} : problem(412, 'PLATFORM_WEB_VERSION_MISMATCH'), headers: { ETag: '"3"' } }));
      await client.request('get', '/tasks/{id}', { path: { id: 5 } });
      status = 412;
      await expect(client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, {}, undefined, versioned)).rejects.toMatchObject({ status: 412, code: 'PLATFORM_WEB_VERSION_MISMATCH' });
      await expect(client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, {}, undefined, versioned)).rejects.toBeInstanceOf(MissingVersionError);
      expect(calls).toHaveLength(2);
    });
  });

  describe('Idempotency-Key', () => {
    const idempotent = { idempotent: true } as const;

    it('gives every side-effecting call its own key and lets the caller repeat one with the same key', async () => {
      let counter = 0;
      const { client, calls } = clientWith({ status: 200, body: {} }, { newKey: () => `key-${(counter += 1)}` });
      await client.request('post', '/things', { body: {} }, {}, undefined, idempotent);
      await client.request('post', '/things', { body: {} }, {}, undefined, idempotent);
      await client.request('post', '/things', { body: {} }, { idempotencyKey: 'again' }, undefined, idempotent);
      await client.request('post', '/things', { body: {} }, {}, undefined, {});
      expect(calls.map((call) => headersOf(call).get('Idempotency-Key'))).toEqual(['key-1', 'key-2', 'again', null]);
    });

    it('makes a well-formed version 4 UUID when none is injected', async () => {
      const calls: Recorded[] = [];
      const plain = createHttpClient({ fetchImpl: async (url, init) => { calls.push({ url, init }); return new Response('{}'); } });
      await plain.request('post', '/things', { body: {} }, {}, undefined, idempotent);
      expect(headersOf(calls[0]).get('Idempotency-Key')).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    });
  });
});
