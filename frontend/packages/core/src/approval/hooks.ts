import { nextCursorOf } from '@forcitis/api';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { claimTask, decideTask, fetchInboxPage, fetchTask, fetchTaskRecordPage, returnTask } from './api';
import { inboxOf } from './model';

export const INBOX_KEY = ['approval', 'inbox'] as const;
export const TASK_RECORDS_KEY = ['approval', 'task-records'] as const;
const taskKey = (taskId: string) => ['approval', 'task', taskId] as const;

/** D-1a-130: the inbox is read again when the window gets the focus back and every 60 seconds; there is no server push. */
const INBOX_REFRESH_MS = 60_000;

export function useInbox() {
  const query = useInfiniteQuery({
    queryKey: INBOX_KEY,
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) => fetchInboxPage(pageParam),
    getNextPageParam: nextCursorOf,
    refetchInterval: INBOX_REFRESH_MS,
    refetchOnWindowFocus: true,
  });
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

export function useTask(taskId: string) {
  return useQuery({ queryKey: taskKey(taskId), queryFn: () => fetchTask(taskId), enabled: taskId !== '' });
}

export function useTaskActions(taskId: string, onFinished: () => void) {
  const queryClient = useQueryClient();
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: INBOX_KEY }),
    queryClient.invalidateQueries({ queryKey: TASK_RECORDS_KEY }),
    queryClient.invalidateQueries({ queryKey: taskKey(taskId) }),
  ]);
  const finish = async () => {
    await refresh();
    onFinished();
  };
  const claim = useMutation({ mutationFn: () => claimTask(taskId), onSuccess: refresh });
  const approve = useMutation({ mutationFn: (reason: string) => decideTask(taskId, 'APPROVE', reason), onSuccess: finish });
  const reject = useMutation({ mutationFn: (reason: string) => decideTask(taskId, 'REJECT', reason), onSuccess: finish });
  const sendBack = useMutation({ mutationFn: (reason: string) => returnTask(taskId, reason), onSuccess: finish });
  return { claim, approve, reject, sendBack };
}
