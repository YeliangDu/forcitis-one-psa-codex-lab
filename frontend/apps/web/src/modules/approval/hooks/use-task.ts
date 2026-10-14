import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { claimTask, decideTask, fetchTask, returnTask } from '../api/approval-api';
import { INBOX_KEY } from './use-inbox';

export function useTask(taskId: string) {
  return useQuery({ queryKey: ['approval', 'task', taskId], queryFn: () => fetchTask(taskId) });
}

export function useTaskActions(taskId: string, onFinished: () => void) {
  const queryClient = useQueryClient();
  const refresh = () => Promise.all([
    queryClient.invalidateQueries({ queryKey: INBOX_KEY }),
    queryClient.invalidateQueries({ queryKey: ['approval', 'task', taskId] }),
  ]);
  const claim = useMutation({ mutationFn: () => claimTask(taskId), onSuccess: refresh });
  const approve = useMutation({ mutationFn: (reason: string) => decideTask(taskId, 'APPROVE', reason), onSuccess: async () => { await refresh(); onFinished(); } });
  const reject = useMutation({ mutationFn: (reason: string) => decideTask(taskId, 'REJECT', reason), onSuccess: async () => { await refresh(); onFinished(); } });
  const sendBack = useMutation({ mutationFn: (reason: string) => returnTask(taskId, reason), onSuccess: async () => { await refresh(); onFinished(); } });
  return { claim, approve, reject, sendBack };
}
