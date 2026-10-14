// Design-system status chip: colours come only from the --status-* semantic tokens (status-badge.css).
import type { ReactNode } from 'react';
import type { StatusTone } from '@/shared/design-system';
import './status-badge.css';

export function StatusBadge({ tone, children }: { tone: StatusTone; children: ReactNode }) {
  return <span className={`status-badge status-badge--${tone}`}>{children}</span>;
}
