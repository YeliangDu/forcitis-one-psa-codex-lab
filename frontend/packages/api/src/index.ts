import * as operations from './generated/operations';
import type { components } from './generated/schema';

export type Schema<Name extends keyof components['schemas']> = components['schemas'][Name];
export { ApiError, createHttpClient } from './http';
export type { ApiHandlers, RequestOptions } from './http';
export { operations };
export { setApiHandlers } from './runtime';
