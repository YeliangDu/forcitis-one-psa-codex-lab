import { type QueryKey, type UseQueryOptions, useQuery } from '@tanstack/react-query';

/** No `select` (it would hand out a different object than the one that carries the version) and no structural sharing (see below). */
export type VersionedQueryOptions<Answer> = Omit<UseQueryOptions<Answer, Error, Answer, QueryKey>, 'select' | 'structuralSharing'>;

/**
 * The query for a GET whose answer is later sent back with If-Match. The version (ETag) belongs to the very object the client returned;
 * TanStack Query's structural sharing keeps the previous object when a new answer has the same content, which would keep the previous version
 * while the server has already moved on. scripts/versioned-queries.test.mjs fails when such a GET is read with a plain useQuery.
 */
export function useVersionedQuery<Answer>(options: VersionedQueryOptions<Answer>) {
  return useQuery({ ...options, structuralSharing: false });
}
