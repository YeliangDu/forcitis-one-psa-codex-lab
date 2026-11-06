import { describeFailure } from '@forcitis/core';
import { LoadingState, Notice } from '@forcitis/ui';
import type { ReactNode } from 'react';

interface QueryState<T> {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  data: T | undefined;
}

interface QueryBoundaryProps<T> {
  query: QueryState<T>;
  errorTitle: string;
  children: (data: T) => ReactNode;
}

/** Loading state, then the failure notice, then the page content once the data is there. */
export function QueryBoundary<T>({ query, errorTitle, children }: QueryBoundaryProps<T>) {
  if (query.isPending) return <LoadingState />;
  if (query.isError) return <Notice tone="error" title={errorTitle}>{describeFailure(query.error)}</Notice>;
  if (query.data === undefined) return <LoadingState />;
  return <>{children(query.data)}</>;
}

interface MutationNoticeProps {
  mutation: { isError: boolean; error: unknown };
  title: string;
}

/** The failure notice of one action; nothing while the action has not failed. */
export function MutationNotice({ mutation, title }: MutationNoticeProps) {
  return mutation.isError ? <Notice tone="error" title={title}>{describeFailure(mutation.error)}</Notice> : null;
}
