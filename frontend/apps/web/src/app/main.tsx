import { DesignSystemProvider } from '@forcitis/design-system';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AppRoutes } from './app-routes';

const container = document.getElementById('root');
if (container === null) throw new Error('index.html must contain <div id="root">.');

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });

createRoot(container).render(
  <StrictMode>
    <DesignSystemProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </QueryClientProvider>
    </DesignSystemProvider>
  </StrictMode>,
);
