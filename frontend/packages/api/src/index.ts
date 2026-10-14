import * as operations from './generated/operations';
import type { components } from './generated/schema';

export type Schema<Name extends keyof components['schemas']> = components['schemas'][Name];
export type { ErrorCode } from './http';
export { ApiError, MissingVersionError, ResponseShapeError, newIdempotencyKey, versionOf } from './http';
export { DEFAULT_PAGE_LIMIT, checkedPage, nextCursorOf } from './paging';
export type { ApiHandlers } from './http';
export { operations };
export { setApiHandlers } from './runtime';
