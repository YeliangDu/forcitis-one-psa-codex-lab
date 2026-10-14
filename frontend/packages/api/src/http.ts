import type { components } from './generated/schema';
import { ProblemDetailSchema } from './generated/validators';

export type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete';
export type ErrorCode = components['schemas']['ErrorCode'];
export type ProblemDetail = components['schemas']['ProblemDetail'];

export interface RequestArgs {
  path?: Record<string, string | number | boolean>;
  query?: Record<string, string | number | boolean | undefined>;
  body?: unknown;
}

export interface RequestOptions {
  /** false: a 401 / 403 answer is returned to the caller as an ApiError and does not trigger the redirect handlers (login calls). */
  guards?: boolean;
  /** Reuse the key of an earlier attempt to repeat a side-effecting POST safely; without it every call gets a fresh key. */
  idempotencyKey?: string;
  /** The versioned update creates a resource that does not exist yet: with no version read, send If-None-Match: * instead of refusing. */
  createOnly?: boolean;
}

/** Declared by the operation in openapi.json: `versioned` = requires If-Match, `idempotent` = accepts Idempotency-Key. */
export interface RequestTraits {
  versioned?: true;
  idempotent?: true;
}

export interface ApiHandlers {
  onUnauthenticated?: () => void;
  onForbidden?: () => void;
}

export interface ResponseSchema {
  safeParse(input: unknown): { success: true; data: unknown } | { success: false; error: { issues: readonly { path: PropertyKey[]; message: string }[] } };
}

export interface HttpClientConfig {
  baseUrl?: string;
  fetchImpl?: (input: string, init: RequestInit) => Promise<Response>;
  readCookie?: (name: string) => string | undefined;
  handlers?: () => ApiHandlers;
  newKey?: () => string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | undefined;
  readonly code: ErrorCode | undefined;
  readonly body: unknown;
  /** The answer had a body but it is not a ProblemDetail (a proxy page, an empty 401): only the status is trustworthy. */
  readonly malformedProblem: boolean;

  constructor(status: number, problem: ProblemDetail | undefined, body: unknown) {
    super(problem?.errorCode ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.code = problem?.errorCode;
    this.body = body;
    this.malformedProblem = problem === undefined && body !== undefined;
  }
}

export class ResponseShapeError extends Error {
  readonly operation: string;
  readonly problems: readonly string[];

  constructor(operation: string, problems: readonly string[]) {
    super(`The answer of ${operation} does not match openapi.json: ${problems.join('; ')}`);
    this.name = 'ResponseShapeError';
    this.operation = operation;
    this.problems = problems;
  }
}

/** A versioned update was attempted without having read the resource first, so there is no ETag to send. Never sent blind. */
export class MissingVersionError extends Error {
  readonly operation: string;

  constructor(operation: string) {
    super(`${operation} needs If-Match, but the resource was not read through this client first.`);
    this.name = 'MissingVersionError';
    this.operation = operation;
  }
}

/** The OpenAPI document marks a property optional when it may be absent; the server writes such a property as null. Treat both as absent. */
function dropNulls(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(dropNulls);
  if (typeof value !== 'object' || value === null) return value;
  return Object.fromEntries(Object.entries(value).filter(([, member]) => member !== null).map(([key, member]) => [key, dropNulls(member)]));
}

const SAFE_METHODS = new Set<HttpMethod>(['get']);
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function readBrowserCookie(name: string): string | undefined {
  for (const pair of document.cookie.split(';')) {
    const [key, ...rest] = pair.trim().split('=');
    if (key === name) return decodeURIComponent(rest.join('='));
  }
  return undefined;
}

/** RFC 4122 version 4; crypto.randomUUID needs a secure context and a recent browser, the embedded WeCom browsers may lack it. */
function randomKey(): string {
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] ?? 0) & 0x0f | 0x40;
  bytes[8] = (bytes[8] ?? 0) & 0x3f | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function buildUrl(baseUrl: string, template: string, args: RequestArgs): string {
  const path = template.replaceAll(/\{(\w+)\}/g, (_, name: string) => {
    const value = args.path?.[name];
    if (value === undefined) throw new Error(`Missing path parameter ${name} for ${template}`);
    return encodeURIComponent(String(value));
  });
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(args.query ?? {})) {
    if (value !== undefined) query.set(key, String(value));
  }
  const queryString = query.toString();
  return `${baseUrl}${path}${queryString === '' ? '' : `?${queryString}`}`;
}

/** The resource an action works on: no query string, no `:action` suffix. `/tasks/7:decide` and `/tasks/7` share one version. */
function resourceOf(url: string): string {
  const withoutQuery = url.split('?')[0] ?? url;
  return withoutQuery.replace(/:[a-z-]+$/, '');
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text();
  if (text === '') return undefined;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function problemOf(body: unknown): ProblemDetail | undefined {
  if (body === undefined) return undefined;
  const checked = ProblemDetailSchema.safeParse(dropNulls(body));
  return checked.success ? (checked.data as ProblemDetail) : undefined;
}

export function createHttpClient(config: HttpClientConfig = {}) {
  const baseUrl = config.baseUrl ?? '';
  const readCookie = config.readCookie ?? readBrowserCookie;
  const send = config.fetchImpl ?? ((input: string, init: RequestInit) => globalThis.fetch(input, init));
  const newKey = config.newKey ?? randomKey;
  const versions = new Map<string, string>();

  async function request<T>(method: HttpMethod, template: string, args: RequestArgs = {}, options: RequestOptions = {}, schema?: ResponseSchema, traits: RequestTraits = {}): Promise<T> {
    const operation = `${method.toUpperCase()} ${template}`;
    const url = buildUrl(baseUrl, template, args);
    const resource = resourceOf(url);
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (args.body !== undefined) headers['Content-Type'] = 'application/json';
    if (!SAFE_METHODS.has(method)) {
      const token = readCookie(CSRF_COOKIE);
      if (token !== undefined) headers[CSRF_HEADER] = token;
    }
    if (traits.versioned === true) {
      const version = versions.get(resource);
      if (version !== undefined) headers['If-Match'] = version;
      else if (options.createOnly === true) headers['If-None-Match'] = '*';
      else throw new MissingVersionError(operation);
    }
    if (traits.idempotent === true) headers['Idempotency-Key'] = options.idempotencyKey ?? newKey();
    const init: RequestInit = { method: method.toUpperCase(), headers, credentials: 'include' };
    if (args.body !== undefined) init.body = JSON.stringify(args.body);

    const response = await send(url, init);
    const body = await readBody(response);
    if (response.ok) {
      const etag = response.headers.get('ETag');
      if (etag !== null) versions.set(resource, etag);
      else if (method !== 'get') versions.delete(resource);
      if (schema === undefined) return body as T;
      const checked = schema.safeParse(dropNulls(body));
      if (!checked.success) {
        throw new ResponseShapeError(operation, checked.error.issues.map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`));
      }
      return checked.data as T;
    }

    if (response.status === 412 || response.status === 428) versions.delete(resource);
    if (options.guards !== false) {
      if (response.status === 401) config.handlers?.().onUnauthenticated?.();
      if (response.status === 403) config.handlers?.().onForbidden?.();
    }
    throw new ApiError(response.status, problemOf(body), body);
  }

  return { request, forgetVersions: () => versions.clear() };
}
