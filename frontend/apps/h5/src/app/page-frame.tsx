import type { ReactNode } from 'react';
import { SessionGate, readLoginConfig } from '@/modules/session';

const loginConfig = readLoginConfig(process.env.TARO_APP_WECOM_CORP_ID);

export function PageFrame({ children }: { children: ReactNode }) {
  return <SessionGate config={loginConfig}>{children}</SessionGate>;
}
