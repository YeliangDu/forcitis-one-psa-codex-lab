import type { StatusTone } from '@forcitis/design-system';
import type { ReactNode } from 'react';
import './status-badge.css';

export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return <span className={`status-badge status-badge--${tone}`}>{children}</span>;
}
