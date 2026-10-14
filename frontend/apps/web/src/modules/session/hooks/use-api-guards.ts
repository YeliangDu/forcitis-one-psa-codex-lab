import { CURRENT_USER_KEY, installApiGuards } from '@forcitis/core';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { isPublicPath } from '../model/current-user';

export function useApiGuards(): void {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const location = useLocation();
  const here = useRef(location);

  useEffect(() => {
    here.current = location;
  }, [location]);

  useEffect(() => installApiGuards({
    onUnauthenticated: () => {
      if (isPublicPath(here.current.pathname)) return;
      const hadSession = queryClient.getQueryData(CURRENT_USER_KEY) !== undefined;
      const from = `${here.current.pathname}${here.current.search}`;
      queryClient.clear();
      void navigate(hadSession ? '/session-expired' : '/login', { replace: true, state: { from } });
    },
    onForbidden: () => {
      void navigate('/forbidden');
    },
  }), [navigate, queryClient]);
}
