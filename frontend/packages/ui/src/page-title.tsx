import type { ReactNode } from 'react';
import './page-title.css';

export function PageTitle({ text, actions }: { text: string; actions?: ReactNode }) {
  return (
    <div className="page-title">
      <h1 className="page-title__text">{text}</h1>
      {actions === undefined ? null : <div className="page-title__actions">{actions}</div>}
    </div>
  );
}
