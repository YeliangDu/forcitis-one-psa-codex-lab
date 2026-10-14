// Design-system wrapper: business modules use <Button>, never antd directly.
import { Button as AntButton } from 'antd';
import type { ReactNode } from 'react';

export function Button({ children, variant = 'primary', onClick }: { children: ReactNode; variant?: 'primary' | 'default' | 'danger'; onClick?: () => void }) {
  return (
    <AntButton type={variant === 'primary' ? 'primary' : 'default'} danger={variant === 'danger'} onClick={onClick}>
      {children}
    </AntButton>
  );
}
