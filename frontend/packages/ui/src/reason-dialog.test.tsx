import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ReasonDialog } from './reason-dialog';

function dialog(props: Partial<React.ComponentProps<typeof ReasonDialog>> = {}) {
  const onConfirm = vi.fn();
  const onClose = vi.fn();
  const element = (open: boolean) => (
    <ReasonDialog open={open} title="撤回" confirmText="确认撤回" label="撤回原因" required onConfirm={onConfirm} onClose={onClose} {...props} />
  );
  return { onConfirm, onClose, element };
}

describe('ReasonDialog', () => {
  it('refuses confirm until the required reason has a non-blank character, then confirms with the trimmed reason', async () => {
    const { onConfirm, element } = dialog();
    render(element(true));
    const confirm = screen.getByRole('button', { name: '确认撤回' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText('撤回原因'), '   ');
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText('撤回原因'), ' 重复录入 ');
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);
    expect(onConfirm).toHaveBeenCalledWith('重复录入');
  });

  it('confirms an empty reason when the reason is optional', async () => {
    const { onConfirm, element } = dialog({ required: false, label: '理由（选填）' });
    render(element(true));
    await userEvent.click(screen.getByRole('button', { name: '确认撤回' }));
    expect(onConfirm).toHaveBeenCalledWith('');
  });

  it('keeps confirm off while another condition says so, and shows the error slot, the extra fields and the line under the reason', async () => {
    const { element } = dialog({
      required: false,
      confirmDisabled: true,
      error: <p>撤回失败</p>,
      children: <p>前置字段</p>,
      below: (reason) => <p>意见：{reason}</p>,
    });
    render(element(true));
    expect(screen.getByRole('button', { name: '确认撤回' })).toBeDisabled();
    expect(screen.getByText('撤回失败')).toBeInTheDocument();
    expect(screen.getByText('前置字段')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('撤回原因'), '好');
    expect(screen.getByText('意见：好')).toBeInTheDocument();
  });

  it('forgets the typed reason when it is closed and opened again', async () => {
    const { element } = dialog();
    const { rerender } = render(element(true));
    await userEvent.type(screen.getByLabelText('撤回原因'), '旧原因');
    rerender(element(false));
    rerender(element(true));
    expect(screen.getByLabelText('撤回原因')).toHaveValue('');
    expect(screen.getByRole('button', { name: '确认撤回' })).toBeDisabled();
  });
});
