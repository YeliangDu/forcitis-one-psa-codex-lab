import type { ReactNode } from 'react';
import { H5App } from './app/h5-app';

export default function App({ children }: { children?: ReactNode }) {
  return <H5App>{children}</H5App>;
}
