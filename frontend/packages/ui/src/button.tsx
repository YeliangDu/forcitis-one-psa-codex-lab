import { Button as AntButton } from 'antd';
import type { ReactNode } from 'react';

export interface ButtonProps {
  children: ReactNode;
  variant?: 'primary' | 'default' | 'danger';
  htmlType?: 'button' | 'submit';
  loading?: boolean;
  disabled?: boolean;
  block?: boolean;
  onClick?: () => void;
}

export function Button({ children, variant = 'primary', htmlType = 'button', loading = false, disabled = false, block = false, onClick }: ButtonProps) {
  return (
    <AntButton
      type={variant === 'primary' ? 'primary' : 'default'}
      danger={variant === 'danger'}
      htmlType={htmlType}
      loading={loading}
      disabled={disabled}
      block={block}
      autoInsertSpace={false}
      onClick={onClick}
    >
      {children}
    </AntButton>
  );
}
