import { type ApiHandlers, type RequestOptions, createHttpClient } from './http';

let handlers: ApiHandlers = {};

export function setApiHandlers(next: ApiHandlers): void {
  handlers = next;
}

export const { request } = createHttpClient({ handlers: () => handlers });

type PathArgs<Op> = Op extends { parameters: { path: infer P } } ? { path: P } : unknown;
type QueryArgs<Op> = Op extends { parameters: { query?: infer Q } } ? ([NonNullable<Q>] extends [never] ? unknown : { query?: NonNullable<Q> }) : unknown;
type BodyArgs<Op> = Op extends { requestBody: { content: { 'application/octet-stream': unknown } } }
  ? { body: Blob; fileName: string }
  : Op extends { requestBody: { content: { 'application/json': infer B } } }
  ? { body: B }
  : Op extends { requestBody?: { content: { 'application/json': infer B } } }
    ? { body?: B }
    : unknown;

export type Args<Op> = PathArgs<Op> & QueryArgs<Op> & BodyArgs<Op>;

export type Call<Op> = Record<never, never> extends Args<Op> ? [args?: Args<Op>, options?: RequestOptions] : [args: Args<Op>, options?: RequestOptions];

export type Result<Op> = Op extends { responses: { 200: { content: infer C } } } ? C[keyof C] : undefined;
