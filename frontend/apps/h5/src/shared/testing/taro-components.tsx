// Stand-in for @tarojs/components in unit tests: the same props, rendered as plain DOM elements.
import type { ReactNode } from 'react';

interface Props {
  children?: ReactNode;
  className?: string;
  onClick?: () => void;
}

export function View({ children, className, onClick }: Props) {
  return <div className={className} onClick={onClick}>{children}</div>;
}

export function Text({ children, className }: Props) {
  return <span className={className}>{children}</span>;
}

export function Button({ children, className, onClick, disabled = false, loading = false }: Props & { disabled?: boolean; loading?: boolean }) {
  return <button type="button" className={className} disabled={disabled || loading} onClick={onClick}>{children}</button>;
}

export function Textarea({ className, value, placeholder, onInput }: { className?: string; value?: string; placeholder?: string; maxlength?: number; onInput?: (event: { detail: { value: string } }) => void }) {
  return <textarea className={className} value={value} placeholder={placeholder} aria-label={placeholder} onChange={(event) => onInput?.({ detail: { value: event.target.value } })} />;
}
