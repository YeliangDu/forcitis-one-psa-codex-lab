import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { currentUserBody } from '@/shared/testing/fixtures';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { NumberingRulesPage } from './numbering-rules-page';

const READ = 'platform.numbering:numbering-rule:read';
const UPDATE = 'platform.numbering:numbering-rule:update';

const RULES = [
  { documentType: 'QUOTE', documentTypeCode: 'QT', pattern: '{TYPE}-{LE}-{yyyyMM}-{seq:4}', resetPolicy: 'MONTHLY', startValue: 1, capacity: 9999, confirmationStatus: 'PENDING_CONFIRMATION' },
  { documentType: 'TIMESHEET', documentTypeCode: null, pattern: 'TS{yyyyMMdd}{seq:4}', resetPolicy: 'DAILY', startValue: 1, capacity: 9999, confirmationStatus: 'CONFIRMED' },
];

function rowOf(text: string) {
  const row = screen.getByText(text).closest('tr');
  if (row === null) throw new Error(`no table row contains ${text}`);
  return within(row);
}

describe('编号规则页', () => {
  it('列出规则，并标出待确认和已确认', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET numbering-rules': { body: RULES } });
    renderWithProviders(<NumberingRulesPage />);

    expect(await screen.findByText('报价单')).toBeInTheDocument();
    expect(screen.getByText('工时单')).toBeInTheDocument();
    expect(screen.getByText('待确认')).toBeInTheDocument();
    expect(screen.getByText('已确认')).toBeInTheDocument();
    expect(screen.getByText('每月重置')).toBeInTheDocument();
  });

  it('没有修改权限时不显示编辑和确认', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET numbering-rules': { body: RULES } });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await waitFor(() => expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '确认' })).not.toBeInTheDocument();
  });

  it('有修改权限时每条规则可编辑，只有待确认的规则可确认', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, UPDATE]) }, 'GET numbering-rules': { body: RULES } });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await waitFor(() => expect(screen.getAllByRole('button', { name: '编辑' })).toHaveLength(2));
    expect(screen.getAllByRole('button', { name: '确认' })).toHaveLength(1);
    expect(rowOf('报价单').getByRole('button', { name: '确认' })).toBeInTheDocument();
  });

  it('编辑：提交新的模板、重置周期和起始流水号', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules': { body: RULES },
      'PUT numbering-rules/TIMESHEET': { status: 204 },
    });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('工时单');
    await userEvent.click(await within(screen.getByText('工时单').closest('tr') as HTMLElement).findByRole('button', { name: '编辑' }));
    const pattern = await screen.findByLabelText('编号模板');
    await userEvent.clear(pattern);
    await userEvent.type(pattern, 'TS{{yyyyMM}-{{seq:5}');
    await userEvent.selectOptions(screen.getByLabelText('流水号重置周期'), 'MONTHLY');
    await userEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true));
    expect(server.calls.find((call) => call.method === 'PUT')?.body).toEqual({ pattern: 'TS{yyyyMM}-{seq:5}', resetPolicy: 'MONTHLY', startValue: 1 });
  });

  it('确认：按当前规则提交，规则即变为已确认', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules': { body: RULES },
      'PUT numbering-rules/QUOTE': { status: 204 },
    });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await userEvent.click(await rowOf('报价单').findByRole('button', { name: '确认' }));
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '确认' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true));
    expect(server.calls.find((call) => call.method === 'PUT')?.body).toEqual({ pattern: '{TYPE}-{LE}-{yyyyMM}-{seq:4}', resetPolicy: 'MONTHLY', startValue: 1 });
  });

  it('服务端认为模板不合法（422）时，在对话框里显示原因', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules': { body: RULES },
      'PUT numbering-rules/TIMESHEET': { status: 422, body: 'pattern must contain {seq}' },
    });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('工时单');
    await userEvent.click(await within(screen.getByText('工时单').closest('tr') as HTMLElement).findByRole('button', { name: '编辑' }));
    await userEvent.click(await screen.findByRole('button', { name: '保存' }));

    expect(await screen.findByText(/编号模板或起始流水号不符合规则/)).toBeInTheDocument();
  });
});
