import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchTimesheet, submitTimesheet, withdrawTimesheet } from '../api/timesheet-api';
import type { SubmitBody } from '../model/timesheet';

const STATUS_POLL_MS = 5_000;
const key = (id: number) => ['timesheet', id] as const;

/** The approval starts after the submit has returned, so a submitted timesheet is read again until it leaves 审批中. */
export function useTimesheet(id: number) {
  return useQuery({
    queryKey: key(id),
    queryFn: () => fetchTimesheet(id),
    enabled: Number.isSafeInteger(id) && id > 0,
    refetchInterval: (query) => (query.state.data?.status === 'SUBMITTED' ? STATUS_POLL_MS : false),
    gcTime: 0,
  });
}

export function useTimesheetActions(id: number) {
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: key(id) });
  const submit = useMutation({ mutationFn: (body: SubmitBody) => submitTimesheet(id, body), onSuccess: refresh });
  const withdraw = useMutation({ mutationFn: (reason: string) => withdrawTimesheet(id, reason), onSuccess: refresh });
  return { submit, withdraw };
}
