// Applies the FORCITIS one theme to every antd component below it. app/ mounts it once at the root.
import { ConfigProvider } from 'antd';
import type { ReactNode } from 'react';
import { antdTheme } from './theme';

export function DesignSystemProvider({ children }: { children: ReactNode }) {
  return <ConfigProvider theme={antdTheme}>{children}</ConfigProvider>;
}
