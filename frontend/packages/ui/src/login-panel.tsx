import type { ReactNode } from 'react';
import { Button } from './button';
import './login-panel.css';

export interface LoginEntry {
  key: string;
  label: string;
  description: string;
  onSelect: () => void;
  loading?: boolean;
  unavailableText?: string;
}

interface LoginPanelProps {
  title: string;
  subtitle?: string;
  entries: readonly LoginEntry[];
  notice?: ReactNode;
  footer?: ReactNode;
}

export function LoginPanel({ title, subtitle, entries, notice, footer }: LoginPanelProps) {
  return (
    <main className="login-panel">
      <div className="login-panel__card">
        <h1 className="login-panel__title">{title}</h1>
        {subtitle === undefined ? null : <p className="login-panel__subtitle">{subtitle}</p>}
        {notice === undefined ? null : <div className="login-panel__notice">{notice}</div>}
        <ul className="login-panel__entries">
          {entries.map((entry) => (
            <li key={entry.key} className="login-panel__entry">
              <Button block loading={entry.loading ?? false} disabled={entry.unavailableText !== undefined} onClick={entry.onSelect}>{entry.label}</Button>
              <span className="login-panel__description">{entry.unavailableText ?? entry.description}</span>
            </li>
          ))}
        </ul>
        {footer === undefined ? null : <div className="login-panel__footer">{footer}</div>}
      </div>
    </main>
  );
}
