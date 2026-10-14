import { logout } from '@forcitis/core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';

export function useLogout(): () => void {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const mutation = useMutation({
    mutationFn: logout,
    onSettled: () => {
      queryClient.clear();
      void navigate('/login', { replace: true });
    },
  });
  return () => mutation.mutate();
}
