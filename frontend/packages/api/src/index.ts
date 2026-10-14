import * as operations from './generated/operations';
import type { components } from './generated/schema';
import { ErrorCodeSchema } from './generated/validators';

export type Schema<Name extends keyof components['schemas']> = components['schemas'][Name];
export type { ErrorCode, ProblemDetail } from './http';
export { ApiError, MissingVersionError, ResponseShapeError, createHttpClient } from './http';
export type { ApiHandlers, RequestOptions } from './http';
export { DEFAULT_PAGE_LIMIT, MAX_PAGE_LIMIT, collectPages, nextCursorOf } from './paging';
export type { CursorPage } from './paging';
export { operations };
export const ERROR_CODES = ErrorCodeSchema.options;
export { forgetVersions, setApiHandlers } from './runtime';
