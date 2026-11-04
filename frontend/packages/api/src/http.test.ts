import { afterEach, describe, expect, it, vi } from 'vitest';
import { forgetReportedViolations, setContractViolationSink } from './contract';
import { ApiError, MissingVersionError, createHttpClient, versionOf } from './http';

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

  it('sends a Blob body as it is with its type and a percent-encoded Content-Disposition name, and still asks for an Idempotency-Key', async () => {
    const { client, calls } = clientWith({ status: 201, body: {} }, { newKey: () => 'key-1' });
    const file = new Blob(['%PDF-1.7'], { type: 'application/pdf' });
    await client.request('post', '/files', { body: file, fileName: "报告 (1)'.pdf" }, {}, undefined, { idempotent: true });
    const sent = calls[0];
    expect(sent?.init.body).toBe(file);
    expect(headersOf(sent).get('Content-Type')).toBe('application/pdf');
    expect(headersOf(sent).get('Content-Disposition')).toBe("attachment; filename*=UTF-8''%E6%8A%A5%E5%91%8A%20%281%29%27.pdf");
    expect(headersOf(sent).get('Idempotency-Key')).toBe('key-1');
  });

  it('sends an untyped Blob as application/octet-stream and refuses an upload without a file name', async () => {
    const { client, calls } = clientWith({ status: 201, body: {} });
    await client.request('post', '/files', { body: new Blob(['x']), fileName: 'a.bin' });
    expect(headersOf(calls[0]).get('Content-Type')).toBe('application/octet-stream');
    await expect(client.request('post', '/files', { body: new Blob(['x']) })).rejects.toThrow('needs fileName');
    expect(calls).toHaveLength(1);
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

  it('keepBusinessForbidden: a 403 with a problem code reaches the caller, a bare 403 and a 401 still hit the guards', async () => {
    const onUnauthenticated = vi.fn();
    const onForbidden = vi.fn();
    const handlers = { onUnauthenticated, onForbidden };
    const business = clientWith({ status: 403, body: problem(403, 'PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED') }, { handlers });
    await expect(business.client.request('post', '/things', { body: {} }, { keepBusinessForbidden: true })).rejects.toMatchObject({ status: 403, problem: { errorCode: 'PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED' } });
    expect(onForbidden).not.toHaveBeenCalled();

    const bare = clientWith({ status: 403 }, { handlers });
    await expect(bare.client.request('post', '/things', { body: {} }, { keepBusinessForbidden: true })).rejects.toMatchObject({ status: 403 });
    expect(onForbidden).toHaveBeenCalledTimes(1);

    const unauthenticated = clientWith({ status: 401, body: problem(401, 'PLATFORM_WEB_UNAUTHENTICATED') }, { handlers });
    await expect(unauthenticated.client.request('post', '/things', { body: {} }, { keepBusinessForbidden: true })).rejects.toMatchObject({ status: 401 });
    expect(onUnauthenticated).toHaveBeenCalledTimes(1);
  });

  it('without the option a 403 with a problem code still redirects (default unchanged)', async () => {
    const onForbidden = vi.fn();
    const { client } = clientWith({ status: 403, body: problem(403, 'PLATFORM_IDENTITY_AUTHORIZATION_CONFIRMER_NOT_ALLOWED') }, { handlers: { onForbidden } });
    await expect(client.request('post', '/things', { body: {} })).rejects.toMatchObject({ status: 403 });
    expect(onForbidden).toHaveBeenCalledTimes(1);
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
    const { client } = clientWith({ status: 422, body: { ...problem(422, 'KERNEL_REASON_REQUIRED'), detail: 'reason is required', extra: null } });
    const failure = await client.request('post', '/things', { body: {} }).catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).problem).toMatchObject({ errorCode: 'KERNEL_REASON_REQUIRED', status: 422, correlationId: 'c-1' });
  });

  it('an error code this client does not know keeps the whole body (legacyLoginUrl included), has no known code, and is reported once', async () => {
    forgetReportedViolations();
    const reports: string[] = [];
    setContractViolationSink((message) => reports.push(message));
    for (let attempt = 0; attempt < 2; attempt += 1) {
      const { client } = clientWith({ status: 503, body: { ...problem(503, 'PLATFORM_FUTURE_CODE'), legacyLoginUrl: 'https://old.example.com/login' } });
      const failure = (await client.request('post', '/login', { body: {} }).catch((error: unknown) => error)) as ApiError;
      expect(failure.status).toBe(503);
      expect(failure.code).toBeUndefined();
      expect(failure.problem).toMatchObject({ errorCode: 'PLATFORM_FUTURE_CODE', legacyLoginUrl: 'https://old.example.com/login' });
    }
    expect(reports).toHaveLength(1);
    expect(reports[0]).toContain('PLATFORM_FUTURE_CODE');
    setContractViolationSink((message) => console.error(message));
  });

  it('an error body that is not a ProblemDetail leaves only the status to trust', async () => {
    for (const body of [{ errorCode: 'PLATFORM_WEB_UNEXPECTED' }, { outcome: 'NOT_HELD' }, '<html>bad gateway</html>']) {
      const { client } = clientWith({ status: 500, body });
      const failure = (await client.request('get', '/things').catch((error: unknown) => error)) as ApiError;
      expect(failure.status).toBe(500);
      expect(failure.code).toBeUndefined();
      expect(failure.problem).toBeUndefined();
    }
    const empty = (await clientWith({ status: 502 }).client.request('get', '/things').catch((error: unknown) => error)) as ApiError;
    expect(empty.problem).toBeUndefined();
  });

  describe('If-Match', () => {
    const versioned = { versioned: true } as const;

    it('the version travels with the data that was read, and the caller hands it back explicitly', async () => {
      const { client, calls } = clientWith(() => ({ status: 200, body: {}, headers: { ETag: '"7"' } }));
      const read = await client.request('get', '/tasks/{id}', { path: { id: 5 } });
      expect(versionOf(read)).toBe('"7"');
      await client.request('post', '/tasks/{id}:decide', { path: { id: 5 }, body: {} }, { ifMatch: versionOf(read) }, undefined, versioned);
      expect(headersOf(calls[1]).get('If-Match')).toBe('"7"');
    });

    it('an answer without an ETag carries no version, and a later answer never inherits an earlier one', async () => {
      let etag: string | undefined = '"1"';
      const { client } = clientWith(() => ({ status: 200, body: {}, ...(etag === undefined ? {} : { headers: { ETag: etag } }) }));
      const first = await client.request('get', '/tasks/{id}', { path: { id: 5 } });
      etag = undefined;
      const second = await client.request('get', '/tasks/{id}', { path: { id: 5 } });
      expect(versionOf(first)).toBe('"1"');
      expect(versionOf(second)).toBeUndefined();
    });

    it('refuses a versioned update that has no version, and sends nothing', async () => {
      const { client, calls } = clientWith({ status: 200, body: {} });
      await expect(client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, {}, undefined, versioned)).rejects.toBeInstanceOf(MissingVersionError);
      expect(calls).toHaveLength(0);
    });

    it('creating a resource that does not exist yet sends If-Match: "0" (B3), never If-None-Match', async () => {
      const { client, calls } = clientWith({ status: 200, body: {} });
      await client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, { createOnly: true }, undefined, versioned);
      expect(headersOf(calls[0]).get('If-Match')).toBe('"0"');
      expect(headersOf(calls[0]).has('If-None-Match')).toBe(false);
    });

    it('a version that is given wins over createOnly', async () => {
      const { client, calls } = clientWith({ status: 200, body: {} });
      await client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, { createOnly: true, ifMatch: '"2"' }, undefined, versioned);
      expect(headersOf(calls[0]).get('If-Match')).toBe('"2"');
    });

    it('a 412 is a plain error with its code; nothing is remembered that a retry could reuse', async () => {
      const { client, calls } = clientWith({ status: 412, body: problem(412, 'PLATFORM_WEB_VERSION_MISMATCH') });
      await expect(client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, { ifMatch: '"3"' }, undefined, versioned)).rejects.toMatchObject({ status: 412, code: 'PLATFORM_WEB_VERSION_MISMATCH' });
      await expect(client.request('put', '/tasks/{id}', { path: { id: 5 }, body: {} }, {}, undefined, versioned)).rejects.toBeInstanceOf(MissingVersionError);
      expect(calls).toHaveLength(1);
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
