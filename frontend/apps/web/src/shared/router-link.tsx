import { TextLink } from '@forcitis/ui';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router';

interface RouterLinkProps {
  /** An in-app path. */
  to: string;
  children: ReactNode;
}

/** A text link that moves inside the app without a page load; a modified or non-primary click still opens it as a plain link. */
export function RouterLink({ to, children }: RouterLinkProps) {
  const navigate = useNavigate();
  return <TextLink href={to} onNavigate={() => void navigate(to)}>{children}</TextLink>;
}
