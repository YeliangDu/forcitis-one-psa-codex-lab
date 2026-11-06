import { useQuery } from '@tanstack/react-query';
import { fetchAccountGrants, fetchAccounts } from '../api/account-grants-api';

export function useAccounts() {
  return useQuery({ queryKey: ['identity', 'accounts'], queryFn: fetchAccounts });
}

/** Read only once an account is chosen. */
export function useAccountGrants(userId: string) {
  return useQuery({ queryKey: ['identity', 'account-grants', userId], queryFn: () => fetchAccountGrants(userId), enabled: userId !== '' });
}
