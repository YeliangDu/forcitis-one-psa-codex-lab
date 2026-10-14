import type { MouseEvent, ReactNode } from 'react';
import './text-link.css';

interface TextLinkProps {
  href: string;
  children: ReactNode;
  external?: boolean;
  onNavigate?: () => void;
}

export function TextLink({ href, children, external = false, onNavigate }: TextLinkProps) {
  const handleClick = (event: MouseEvent<HTMLAnchorElement>) => {
    if (onNavigate === undefined || event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) return;
    event.preventDefault();
    onNavigate();
  };
  return external
    ? <a className="text-link" href={href} target="_blank" rel="noopener noreferrer">{children}</a>
    : <a className="text-link" href={href} onClick={handleClick}>{children}</a>;
}
