import '@forcitis/design-system/mobile';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import './h5-app.css';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });

/** Taro mounts the pages as the app's children and fails when they are missing, so the login gate sits inside each page (PageFrame), not here. */
export function H5App({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
