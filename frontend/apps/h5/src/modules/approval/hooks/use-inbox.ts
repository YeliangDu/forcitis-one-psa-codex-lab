import { useQuery } from '@tanstack/react-query';
import { fetchInbox } from '../api/approval-api';

export const INBOX_KEY = ['approval', 'inbox'] as const;

export function useInbox() {
  return useQuery({ queryKey: INBOX_KEY, queryFn: fetchInbox });
}
