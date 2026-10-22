import { ErrorCodeValues, ProblemDetailSchema } from './generated/validators';
import type { components } from './generated/schema';

export type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete';
export type ErrorCode = components['schemas']['ErrorCode'];
/** errorCode is a plain string here: a code a newer server added must not make the error body unreadable. */
export type ProblemDetail = Omit<components['schemas']['ProblemDetail'], 'errorCode'> & { errorCode: string };

export interface RequestArgs {
  path?: Record<string, string | number | boolean>;
  query?: Record<string, string | number | boolean | undefined>;
  /** A Blob is sent as it is (a file upload, with fileName); anything else is sent as JSON. */
  body?: unknown;
  /** The name of an uploaded file; the server reads it from Content-Disposition. */
  fileName?: string;
}

export interface RequestOptions {
  /** false: a 401 / 403 answer is returned to the caller as an ApiError and does not trigger the redirect handlers (login calls). */
  guards?: boolean;
  /** One key per user intent: a retry of the same intent passes the key of the first attempt. Without it every call gets a fresh key. */
  idempotencyKey?: string;
  /** The version (ETag) of the data the update is based on: `versionOf(data)` of what was read. A versioned update without it is refused. */
  ifMatch?: string | undefined;
  /** The versioned update creates a resource that does not exist yet (B3): with no version, send If-Match: "0", the version of a resource that is not there, instead of refusing. */
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

const KNOWN_CODES: ReadonlySet<string> = new Set(ErrorCodeValues);

export class ApiError extends Error {
  readonly status: number;
  /** The whole error body when it is a ProblemDetail, also when its errorCode is one this client does not know. */
  readonly problem: ProblemDetail | undefined;
  /** The error code when it is one openapi.json lists. */
  readonly code: ErrorCode | undefined;
  readonly body: unknown;

  constructor(status: number, problem: ProblemDetail | undefined, body: unknown) {
    super(problem?.errorCode ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
    this.code = problem !== undefined && KNOWN_CODES.has(problem.errorCode) ? (problem.errorCode as ErrorCode) : undefined;
    this.body = body;
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

/** A versioned update was attempted without the version of the data it is based on. Never sent blind. */
export class MissingVersionError extends Error {
  readonly operation: string;

  constructor(operation: string) {
    super(`${operation} needs If-Match, but no version of the data was given.`);
    this.name = 'MissingVersionError';
    this.operation = operation;
  }
}

/** The version (ETag) an answer arrived with, kept with the very object that was returned, so it cannot outlive or be mixed up with other data. */
const versions = new WeakMap<object, string>();

export function versionOf(data: unknown): string | undefined {
  return typeof data === 'object' && data !== null ? versions.get(data) : undefined;
}

/** The OpenAPI document marks a property optional when it may be absent; the server writes such a property as null. Treat both as absent. */
function dropNulls(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(dropNulls);
  if (typeof value !== 'object' || value === null) return value;
  return Object.fromEntries(Object.entries(value).filter(([, member]) => member !== null).map(([key, member]) => [key, dropNulls(member)]));
}

const NO_VERSION = '"0"';
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
export function newIdempotencyKey(): string {
  const bytes = new Uint8Array(16);
  globalThis.crypto.getRandomValues(bytes);
  bytes[6] = (bytes[6] ?? 0) & 0x0f | 0x40;
  bytes[8] = (bytes[8] ?? 0) & 0x3f | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** RFC 8187 value: every byte that is not an unreserved character is percent-encoded (encodeURIComponent leaves ! ' ( ) * alone). */
function encodeFileName(name: string): string {
  return encodeURIComponent(name).replaceAll(/['()*!]/g, (character) => `%${character.charCodeAt(0).toString(16).toUpperCase()}`);
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
  if (!checked.success) return undefined;
  return checked.data as ProblemDetail;
}

export function createHttpClient(config: HttpClientConfig = {}) {
  const baseUrl = config.baseUrl ?? '';
  const readCookie = config.readCookie ?? readBrowserCookie;
  const send = config.fetchImpl ?? ((input: string, init: RequestInit) => globalThis.fetch(input, init));
  const newKey = config.newKey ?? newIdempotencyKey;

  async function request<T>(method: HttpMethod, template: string, args: RequestArgs = {}, options: RequestOptions = {}, schema?: ResponseSchema, traits: RequestTraits = {}): Promise<T> {
    const operation = `${method.toUpperCase()} ${template}`;
    const headers: Record<string, string> = { Accept: 'application/json' };
    const upload = args.body instanceof Blob ? args.body : undefined;
    if (upload !== undefined) {
      if (args.fileName === undefined || args.fileName === '') throw new Error(`An upload needs fileName for ${operation}`);
      headers['Content-Type'] = upload.type === '' ? 'application/octet-stream' : upload.type;
      headers['Content-Disposition'] = `attachment; filename*=UTF-8''${encodeFileName(args.fileName)}`;
    } else if (args.body !== undefined) headers['Content-Type'] = 'application/json';
    if (!SAFE_METHODS.has(method)) {
      const token = readCookie(CSRF_COOKIE);
      if (token !== undefined) headers[CSRF_HEADER] = token;
    }
    if (traits.versioned === true) {
      if (options.ifMatch !== undefined) headers['If-Match'] = options.ifMatch;
      else if (options.createOnly === true) headers['If-Match'] = NO_VERSION;
      else throw new MissingVersionError(operation);
    }
    if (traits.idempotent === true) headers['Idempotency-Key'] = options.idempotencyKey ?? newKey();
    const init: RequestInit = { method: method.toUpperCase(), headers, credentials: 'include' };
    if (args.body !== undefined) init.body = upload ?? JSON.stringify(args.body);

    const response = await send(buildUrl(baseUrl, template, args), init);
    const body = await readBody(response);
    if (response.ok) {
      let result: unknown = body;
      if (schema !== undefined) {
        const checked = schema.safeParse(dropNulls(body));
        if (!checked.success) {
          throw new ResponseShapeError(operation, checked.error.issues.map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`));
        }
        result = checked.data;
      }
      const etag = response.headers.get('ETag');
      if (etag !== null && typeof result === 'object' && result !== null) versions.set(result, etag);
      return result as T;
    }

    if (options.guards !== false) {
      if (response.status === 401) config.handlers?.().onUnauthenticated?.();
      if (response.status === 403) config.handlers?.().onForbidden?.();
    }
    throw new ApiError(response.status, problemOf(body), body);
  }

  return { request };
}
