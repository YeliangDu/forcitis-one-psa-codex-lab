import { useEffect, useState, type ReactNode } from 'react';
import { Dialog } from './feedback';
import { TextAreaField } from './form-fields';

interface ReasonDialogProps {
  open: boolean;
  title: string;
  confirmText: string;
  /** The label of the reason field, spoken as given (it says whether the reason is required). */
  label: string;
  required: boolean;
  danger?: boolean;
  loading?: boolean;
  /** Another condition that keeps the confirm button off, beside the reason rule. */
  confirmDisabled?: boolean;
  /** Fields that come before the reason. */
  children?: ReactNode;
  /** What shows under the reason field, given the reason as typed. */
  below?: (reason: string) => ReactNode;
  /** The failure notice of the action the dialog confirms. */
  error?: ReactNode;
  /** Called with the trimmed reason. */
  onConfirm: (reason: string) => void;
  onClose: () => void;
}

/** A confirm dialog that asks for a reason; the reason is forgotten whenever the dialog closes. */
export function ReasonDialog({ open, title, confirmText, label, required, danger = false, loading = false, confirmDisabled = false, children, below, error, onConfirm, onClose }: ReasonDialogProps) {
  const [reason, setReason] = useState('');
  useEffect(() => {
    if (!open) setReason('');
  }, [open]);

  return (
    <Dialog
      open={open}
      title={title}
      confirmText={confirmText}
      danger={danger}
      confirmLoading={loading}
      confirmDisabled={confirmDisabled || (required && reason.trim() === '')}
      onConfirm={() => onConfirm(reason.trim())}
      onClose={onClose}
    >
      {children}
      <TextAreaField label={label} value={reason} onChange={setReason} />
      {below?.(reason)}
      {error}
    </Dialog>
  );
}
