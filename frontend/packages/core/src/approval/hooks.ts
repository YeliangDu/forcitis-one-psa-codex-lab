import { nextCursorOf, versionOf } from '@forcitis/api';
import { type InfiniteData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { isVersionConflict } from '../failure';
import { useIntentKeys } from '../intent-keys';
import { claimTask, decideTask, fetchInboxPage, fetchTask, fetchTaskRecordPage, returnTask } from './api';
import { type InboxResponse, type TaskDetail, inboxOf } from './model';

const INBOX_KEY = ['approval', 'inbox'] as const;
const TASK_RECORDS_KEY = ['approval', 'task-records'] as const;
const taskKey = (taskId: string) => ['approval', 'task', taskId] as const;

/** D-1a-130: the inbox is read again when the window gets the focus back and every 60 seconds; there is no server push. */
const INBOX_REFRESH_MS = 60_000;

/** A refresh starts again from the first page: the pages loaded further down are not read again behind the user's back. */
export function useInbox() {
  const queryClient = useQueryClient();
  const query = useInfiniteQuery({
    queryKey: INBOX_KEY,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchInboxPage(pageParam),
    getNextPageParam: nextCursorOf,
    refetchOnWindowFocus: false,
  });
  const { refetch } = query;

  useEffect(() => {
    const refresh = () => {
      queryClient.setQueryData<InfiniteData<InboxResponse, string | undefined>>(INBOX_KEY, (data) => data && { pages: data.pages.slice(0, 1), pageParams: data.pageParams.slice(0, 1) });
      void refetch();
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    const timer = setInterval(refresh, INBOX_REFRESH_MS);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [queryClient, refetch]);

  return { ...query, inbox: query.data === undefined ? undefined : inboxOf(query.data.pages) };
}

export function useTaskRecords(enabled: boolean) {
  const query = useInfiniteQuery({
    queryKey: TASK_RECORDS_KEY,
    enabled,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchTaskRecordPage(pageParam),
    getNextPageParam: nextCursorOf,
  });
  return { ...query, records: query.data?.pages.flatMap((page) => page.items) };
}

/** Not shared between renders: the version belongs to this very object, so a task read again is a new object with its own version. */
export function useTask(taskId: string) {
  return useQuery({ queryKey: taskKey(taskId), queryFn: () => fetchTask(taskId), enabled: taskId !== '', structuralSharing: false });
}

/** `task` is the task as read: its version goes with every action, and a refused version (412) reads the task again. */
export function useTaskActions(taskId: string, task: TaskDetail | undefined, onFinished: () => void) {
  const queryClient = useQueryClient();
  const keys = useIntentKeys();
  const version = versionOf(task);
  const attempt = (fingerprint: string) => ({ version, idempotencyKey: keys.keyFor(fingerprint) });
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: INBOX_KEY }),
    queryClient.invalidateQueries({ queryKey: TASK_RECORDS_KEY }),
    queryClient.invalidateQueries({ queryKey: taskKey(taskId) }),
  ]);
  const succeeded = async () => {
    keys.done();
    await refresh();
  };
  const finished = async () => {
    await succeeded();
    onFinished();
  };
  const refused = (error: unknown) => {
    if (!isVersionConflict(error)) return;
    keys.done();
    void queryClient.invalidateQueries({ queryKey: taskKey(taskId) });
  };
  const claim = useMutation({ mutationFn: () => claimTask(taskId, attempt('claim')), onSuccess: succeeded, onError: refused });
  const approve = useMutation({ mutationFn: (reason: string) => decideTask(taskId, 'APPROVE', reason, attempt(`approve:${reason}`)), onSuccess: finished, onError: refused });
  const reject = useMutation({ mutationFn: (reason: string) => decideTask(taskId, 'REJECT', reason, attempt(`reject:${reason}`)), onSuccess: finished, onError: refused });
  const sendBack = useMutation({ mutationFn: (reason: string) => returnTask(taskId, reason, attempt(`return:${reason}`)), onSuccess: finished, onError: refused });
  return { claim, approve, reject, sendBack };
}
