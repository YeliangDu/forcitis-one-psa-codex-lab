export type HttpMethod = 'get' | 'post' | 'put' | 'patch' | 'delete';

export interface RequestArgs {
  path?: Record<string, string | number | boolean>;
  query?: Record<string, string | number | boolean | undefined>;
  body?: unknown;
}

export interface RequestOptions {
  /** false: a 401 / 403 answer is returned to the caller as an ApiError and does not trigger the redirect handlers (login calls). */
  guards?: boolean;
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
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string | undefined;
  readonly body: unknown;

  constructor(status: number, code: string | undefined, body: unknown) {
    super(code ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

/** The OpenAPI document marks a property optional when it may be absent; the server writes such a property as null. Treat both as absent. */
function dropNulls(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(dropNulls);
  if (typeof value !== 'object' || value === null) return value;
  return Object.fromEntries(Object.entries(value).filter(([, member]) => member !== null).map(([key, member]) => [key, dropNulls(member)]));
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

function errorCodeOf(body: unknown): string | undefined {
  if (typeof body !== 'object' || body === null) return undefined;
  const record = body as Record<string, unknown>;
  const code = record['errorCode'] ?? record['outcome'];
  return typeof code === 'string' ? code : undefined;
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

export function createHttpClient(config: HttpClientConfig = {}) {
  const baseUrl = config.baseUrl ?? '';
  const readCookie = config.readCookie ?? readBrowserCookie;
  const send = config.fetchImpl ?? ((input: string, init: RequestInit) => globalThis.fetch(input, init));

  async function request<T>(method: HttpMethod, template: string, args: RequestArgs = {}, options: RequestOptions = {}, schema?: ResponseSchema): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (args.body !== undefined) headers['Content-Type'] = 'application/json';
    if (!SAFE_METHODS.has(method)) {
      const token = readCookie(CSRF_COOKIE);
      if (token !== undefined) headers[CSRF_HEADER] = token;
    }
    const init: RequestInit = { method: method.toUpperCase(), headers, credentials: 'include' };
    if (args.body !== undefined) init.body = JSON.stringify(args.body);

    const response = await send(buildUrl(baseUrl, template, args), init);
    const body = await readBody(response);
    if (response.ok) {
      if (schema === undefined) return body as T;
      const checked = schema.safeParse(dropNulls(body));
      if (!checked.success) {
        throw new ResponseShapeError(`${method.toUpperCase()} ${template}`, checked.error.issues.map((issue) => `${issue.path.join('.') || '(root)'}: ${issue.message}`));
      }
      return checked.data as T;
    }

    if (options.guards !== false) {
      if (response.status === 401) config.handlers?.().onUnauthenticated?.();
      if (response.status === 403) config.handlers?.().onForbidden?.();
    }
    throw new ApiError(response.status, errorCodeOf(body), body);
  }

  return { request };
}
