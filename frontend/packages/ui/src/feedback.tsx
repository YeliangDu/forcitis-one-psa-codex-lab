import { Alert, Empty, Modal, Spin } from 'antd';
import type { ReactNode } from 'react';
import './feedback.css';

export function Notice({ tone, title, children }: { tone: 'error' | 'warning' | 'info' | 'success'; title: string; children?: ReactNode }) {
  return <Alert type={tone} showIcon message={title} {...(children === undefined ? {} : { description: children })} />;
}

export function LoadingState({ label = '加载中' }: { label?: string }) {
  return <Spin tip={label}><div className="loading-state" role="status" aria-label={label} /></Spin>;
}

export function EmptyState({ text }: { text: string }) {
  return <Empty description={text} />;
}

interface DialogProps {
  open: boolean;
  title: string;
  confirmText: string;
  cancelText?: string;
  danger?: boolean;
  confirmLoading?: boolean;
  confirmDisabled?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: ReactNode;
}

export function Dialog({ open, title, confirmText, cancelText = '取消', danger = false, confirmLoading = false, confirmDisabled = false, onConfirm, onClose, children }: DialogProps) {
  return (
    <Modal
      open={open}
      title={title}
      okText={confirmText}
      cancelText={cancelText}
      okButtonProps={{ danger, disabled: confirmDisabled, autoInsertSpace: false }}
      cancelButtonProps={{ autoInsertSpace: false }}
      confirmLoading={confirmLoading}
      onOk={onConfirm}
      onCancel={onClose}
      destroyOnHidden
    >
      {children}
    </Modal>
  );
}
