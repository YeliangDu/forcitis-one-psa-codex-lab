import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { currentUserBody, stubServer, taskDetailBody } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { registerTaskDocument } from '../components/task-documents';
import { TaskDetailPage } from './task-detail-page';

function render() {
  return renderWithProviders(<TaskDetailPage />, { route: '/approval/tasks/t-1', path: '/approval/tasks/:taskId' });
}

function serve(documentType: string) {
  stubServer({
    'GET sessions:current': { body: currentUserBody(['platform.approval:task:read']) },
    'GET approval-tasks/t-1': { body: { ...taskDetailBody(), documentType, documentId: 'doc-7' } },
  });
}

describe('审批任务页的单据槽', () => {
  it('登记过的单据类型在摘要与办理按钮之间显示登记的组件，并带着单据号', async () => {
    registerTaskDocument('SLOT_REGISTERED', ({ documentId }) => <p>登记组件 {documentId}</p>);
    serve('SLOT_REGISTERED');
    render();

    expect(await screen.findByText('登记组件 doc-7')).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '通过' })).toBeInTheDocument();
  });

  it('没登记的单据类型只显示原来的摘要，不多出别的内容', async () => {
    serve('SLOT_UNREGISTERED');
    render();

    expect(await screen.findByText('部门负责人审核')).toBeInTheDocument();
    expect(screen.queryByText(/登记组件/)).not.toBeInTheDocument();
  });

  it('同一单据类型登记两次是错误，不静默覆盖', () => {
    registerTaskDocument('SLOT_TWICE', () => null);
    expect(() => registerTaskDocument('SLOT_TWICE', () => null)).toThrow(/already registered/);
  });
});
