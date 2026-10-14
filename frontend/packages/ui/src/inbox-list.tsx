import { Segmented } from 'antd';
import type { StatusTone } from '@forcitis/design-system';
import type { ReactNode } from 'react';
import { StatusBadge } from './status-badge';
import { TextLink } from './text-link';
import './inbox-list.css';

export interface InboxRow {
  id: string;
  title: string;
  subtitle: string;
  timeText: string;
  tag: { label: string; tone: StatusTone };
  state?: string;
  href?: string;
  external: boolean;
  onOpen?: () => void;
}

export interface InboxFilter {
  key: string;
  label: string;
  count: number;
}

interface InboxListProps {
  rows: readonly InboxRow[];
  filters: readonly InboxFilter[];
  activeFilter: string;
  onFilterChange: (key: string) => void;
  emptyText: string;
  notice?: ReactNode;
  rowActions?: (row: InboxRow) => ReactNode;
}

export function InboxList({ rows, filters, activeFilter, onFilterChange, emptyText, notice, rowActions }: InboxListProps) {
  return (
    <section className="inbox-list">
      <Segmented
        value={activeFilter}
        onChange={(key) => onFilterChange(String(key))}
        options={filters.map((filter) => ({ value: filter.key, label: `${filter.label} ${filter.count}` }))}
      />
      {notice === undefined ? null : <div className="inbox-list__notice">{notice}</div>}
      {rows.length === 0
        ? <p className="inbox-list__empty">{emptyText}</p>
        : (
          <ul className="inbox-list__rows">
            {rows.map((row) => (
              <li key={row.id} className="inbox-list__row">
                <StatusBadge tone={row.tag.tone}>{row.tag.label}</StatusBadge>
                <div className="inbox-list__main">
                  {row.href === undefined
                    ? <span>{row.title}</span>
                    : <TextLink href={row.href} external={row.external} {...(row.onOpen === undefined ? {} : { onNavigate: row.onOpen })}>{row.title}</TextLink>}
                  <span className="inbox-list__subtitle">{row.subtitle}</span>
                </div>
                {row.state === undefined ? null : <span className="inbox-list__state">{row.state}</span>}
                <time className="inbox-list__time">{row.timeText}</time>
                {rowActions === undefined ? null : <div className="inbox-list__actions">{rowActions(row)}</div>}
              </li>
            ))}
          </ul>
        )}
    </section>
  );
}
