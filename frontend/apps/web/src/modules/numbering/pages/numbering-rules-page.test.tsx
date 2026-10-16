import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { VERSION_HEADER, currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { NumberingRulesPage } from './numbering-rules-page';

const READ = 'platform.numbering:numbering-rule:read';
const UPDATE = 'platform.numbering:numbering-rule:update';
const APPROVE = 'platform.numbering:numbering-rule:approve';

const PENDING = { authorId: '019a0000-0000-7000-8000-000000002002', authoredAt: '2026-10-10T01:00:00Z', capacity: 9999, effectiveFrom: '2999-01-01', pattern: '{TYPE}-{LE}-{yyyyMM}-{seq:5}', resetPolicy: 'MONTHLY', startValue: 1, templateVersion: 3 };

const RULE = { capacity: 9999, effectiveFrom: '2026-01-01', requiresFinanceConfirmation: false, templateVersion: 2, version: 4 };
const RULES = [
  { ...RULE, documentType: 'QUOTE', documentTypeCode: 'QT', pattern: '{TYPE}-{LE}-{yyyyMM}-{seq:4}', resetPolicy: 'MONTHLY', startValue: 1, confirmationStatus: 'PENDING_CONFIRMATION', pending: PENDING },
  { ...RULE, documentType: 'TIMESHEET', documentTypeCode: 'TS', pattern: '{TYPE}-{yyyyMMdd}-{seq:4}', resetPolicy: 'DAILY', startValue: 1, confirmationStatus: 'CONFIRMED' },
];
const VOUCHER = {
  ...RULE,
  documentType: 'JOURNAL_VOUCHER',
  documentTypeCode: 'JV',
  pattern: '{TYPE}-{yyyyMM}-{seq:6}',
  resetPolicy: 'MONTHLY',
  startValue: 1,
  requiresFinanceConfirmation: true,
  confirmationStatus: 'PENDING_CONFIRMATION',
  pending: { ...PENDING, confirmedBy: '019a0000-0000-7000-8000-000000003003', templateVersion: 1 },
};

const listed = (items: object[]) => ({ items });

function rowOf(text: string) {
  const row = screen.getByText(text).closest('tr');
  if (row === null) throw new Error(`no table row contains ${text}`);
  return within(row);
}

async function openEditor(label: string) {
  await userEvent.click(await within((await screen.findByText(label)).closest('tr') as HTMLElement).findByRole('button', { name: '编辑' }));
}

describe('编号规则页', () => {
  it('列出规则，并标出待确认和已确认；待确认版本显示作者、确认人、生效日', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET numbering-rules': { body: listed([...RULES, VOUCHER]) } });
    renderWithProviders(<NumberingRulesPage />);

    expect(await screen.findByText('报价单')).toBeInTheDocument();
    expect(screen.getByText('工时单')).toBeInTheDocument();
    expect(screen.getAllByText('待确认')).toHaveLength(2);
    expect(screen.getByText('已确认')).toBeInTheDocument();
    expect(screen.getAllByText('每月重置', { selector: 'td' }).length).toBeGreaterThan(0);
    expect(rowOf('报价单').getByText('2999-01-01')).toBeInTheDocument();
    expect(rowOf('报价单').getByText('019a0000-0000-7000-8000-000000002002')).toBeInTheDocument();
    expect(rowOf('会计凭证').getByText('019a0000-0000-7000-8000-000000003003')).toBeInTheDocument();
  });

  it('服务端新加了单据类型：显示破折号，不显示 undefined', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET numbering-rules': { body: listed([{ ...RULES[1], documentType: 'CONTRACT' }]) } });
    renderWithProviders(<NumberingRulesPage />);

    expect(await screen.findAllByText('—')).not.toHaveLength(0);
    expect(screen.queryByText(/undefined/)).not.toBeInTheDocument();
  });

  it('规则分页时读完所有页', async () => {
    const server = stubServer({
      'GET numbering-rules': (request) => (request.url.includes('cursor=c2') ? { body: listed([RULES[1] ?? {}]) } : { body: { items: [RULES[0]], nextCursor: 'c2' } }),
      'GET sessions:current': { body: currentUserBody([READ]) },
    });
    renderWithProviders(<NumberingRulesPage />);

    expect(await screen.findByText('工时单')).toBeInTheDocument();
    expect(screen.getByText('报价单')).toBeInTheDocument();
    expect(server.calls.filter((call) => call.url.includes('numbering-rules')).length).toBe(2);
  });

  it('没有修改和确认权限时不显示编辑和确认', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ]) }, 'GET numbering-rules': { body: listed(RULES) } });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await waitFor(() => expect(screen.queryByRole('button', { name: '编辑' })).not.toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '确认' })).not.toBeInTheDocument();
  });

  it('有权限时每条规则可编辑，只有别人编写的待确认版本可确认；自己编写的不能确认', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, UPDATE, APPROVE]) }, 'GET numbering-rules': { body: listed([...RULES, { ...RULES[0], documentType: 'JOURNAL_VOUCHER', pending: { ...PENDING, authorId: '019a0000-0000-7000-8000-000000001001' } }]) } });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await waitFor(() => expect(screen.getAllByRole('button', { name: '编辑' })).toHaveLength(3));
    expect(screen.getAllByRole('button', { name: '确认' })).toHaveLength(1);
    expect(rowOf('报价单').getByRole('button', { name: '确认' })).toBeInTheDocument();
  });

  it('凭证号模板等财务确认：显示为待确认，没有任何确认入口，1a 没有角色可以执行', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([READ, UPDATE, APPROVE, 'platform.numbering:voucher-numbering-rule:approve']) }, 'GET numbering-rules': { body: listed([VOUCHER]) } });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('会计凭证');
    expect(rowOf('会计凭证').getByText('待确认')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: '编辑' })).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: '确认' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /财务/ })).not.toBeInTheDocument();
  });

  it('编辑：提交新的模板、重置周期、起始流水号和生效日期，带着读到的版本', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules/TIMESHEET': { body: RULES[1], headers: { ETag: '"4"' } },
      'GET numbering-rules': { body: listed(RULES) },
      'PUT numbering-rules/TIMESHEET': { status: 204 },
    });
    renderWithProviders(<NumberingRulesPage />);

    await openEditor('工时单');
    const pattern = await screen.findByLabelText('编号模板');
    await userEvent.clear(pattern);
    await userEvent.type(pattern, 'TS{{yyyyMM}-{{seq:5}');
    await userEvent.selectOptions(screen.getByLabelText('流水号重置周期'), 'MONTHLY');
    fireEvent.change(screen.getByLabelText('生效日期'), { target: { value: '2999-06-01' } });
    await userEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(server.calls.some((call) => call.method === 'PUT')).toBe(true));
    const put = server.calls.find((call) => call.method === 'PUT');
    expect(put?.body).toEqual({ pattern: 'TS{yyyyMM}-{seq:5}', resetPolicy: 'MONTHLY', startValue: 1, effectiveFrom: '2999-06-01' });
    expect(put?.headers.get('If-Match')).toBe('"4"');
  });

  it('生效日期必填，不得早于今天', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules/TIMESHEET': { body: RULES[1], headers: VERSION_HEADER },
      'GET numbering-rules': { body: listed(RULES) },
      'PUT numbering-rules/TIMESHEET': { status: 204 },
    });
    renderWithProviders(<NumberingRulesPage />);

    await openEditor('工时单');
    await waitFor(() => expect(screen.getByRole('button', { name: '保存' })).toBeDisabled());
    expect(await screen.findByText('请填写生效日期')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('生效日期'), { target: { value: '2000-01-01' } });
    expect(await screen.findByText('生效日期不能早于今天')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled();
    expect(server.calls.some((call) => call.method === 'PUT')).toBe(false);
  });

  it('确认：显示待确认版本的内容，提交时带着模板版本号', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET numbering-rules': { body: listed(RULES) },
      'POST numbering-rules/QUOTE:confirm': { status: 204 },
    });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await userEvent.click(await rowOf('报价单').findByRole('button', { name: '确认' }));
    const dialog = (await screen.findByText('确认编号规则')).closest('[role="dialog"]') as HTMLElement;
    expect(within(dialog).getByText('{TYPE}-{LE}-{yyyyMM}-{seq:5}')).toBeInTheDocument();
    expect(within(dialog).getByText(/生效日：2999-01-01/)).toBeInTheDocument();
    expect(within(dialog).getByText(/作者：019a0000-0000-7000-8000-000000002002/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: '确认' }));

    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith(':confirm'))).toBe(true));
    const confirm = server.calls.find((call) => call.url.endsWith(':confirm'));
    expect(confirm?.body).toEqual({ templateVersion: 3 });
    expect(confirm?.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('确认时模板版本已变（409）：提示刷新，并重新读取规则', async () => {
    let reads = 0;
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, APPROVE]) },
      'GET numbering-rules': () => {
        reads += 1;
        return { body: listed(RULES) };
      },
      'POST numbering-rules/QUOTE:confirm': { status: 409, body: problemBody(409, 'PLATFORM_NUMBERING_VERSION_MISMATCH') },
    });
    renderWithProviders(<NumberingRulesPage />);

    await screen.findByText('报价单');
    await userEvent.click(await rowOf('报价单').findByRole('button', { name: '确认' }));
    const dialog = (await screen.findByText('确认编号规则')).closest('[role="dialog"]') as HTMLElement;
    await userEvent.click(within(dialog).getByRole('button', { name: '确认' }));

    expect(await screen.findByText('编号模板刚刚被修改，请刷新后重试')).toBeInTheDocument();
    await waitFor(() => expect(reads).toBe(2));
  });

  it('服务端认为模板不合法（422）时，在对话框里显示原因', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules/TIMESHEET': { body: RULES[1], headers: VERSION_HEADER },
      'GET numbering-rules': { body: listed(RULES) },
      'PUT numbering-rules/TIMESHEET': { status: 422, body: problemBody(422, 'PLATFORM_NUMBERING_RULE_INVALID') },
    });
    renderWithProviders(<NumberingRulesPage />);

    await openEditor('工时单');
    fireEvent.change(await screen.findByLabelText('生效日期'), { target: { value: '2999-06-01' } });
    const save = screen.getByRole('button', { name: '保存' });
    await waitFor(() => expect(save).toBeEnabled());
    await userEvent.click(save);

    expect(await screen.findByText(/编号模板或起始流水号不符合规则/)).toBeInTheDocument();
  });

  it('保存时规则已被别人修改（412），提示刷新', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([READ, UPDATE]) },
      'GET numbering-rules/TIMESHEET': { body: RULES[1], headers: VERSION_HEADER },
      'GET numbering-rules': { body: listed(RULES) },
      'PUT numbering-rules/TIMESHEET': { status: 412, body: problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH') },
    });
    renderWithProviders(<NumberingRulesPage />);

    await openEditor('工时单');
    fireEvent.change(await screen.findByLabelText('生效日期'), { target: { value: '2999-06-01' } });
    const save = screen.getByRole('button', { name: '保存' });
    await waitFor(() => expect(save).toBeEnabled());
    await userEvent.click(save);

    expect(await screen.findByText('内容刚刚被其他人修改，请刷新后重试')).toBeInTheDocument();
  });
});
