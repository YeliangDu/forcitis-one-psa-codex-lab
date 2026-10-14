import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { DesignSystemProvider } from '@/shared/design-system';
import { routes } from './routes';

const container = document.getElementById('root');
if (container === null) throw new Error('index.html must contain <div id="root">.');

createRoot(container).render(
  <StrictMode>
    <DesignSystemProvider>{routes[0]?.element}</DesignSystemProvider>
  </StrictMode>,
);
