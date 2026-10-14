import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { VERSION_HEADER, currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { TimesheetPage } from './timesheet-page';

const SUBMIT = 'cost.timesheet:timesheet:submit';

const sheet = (overrides: object = {}) => ({ timesheetId: '7', status: 'DRAFT', round: 1, workDate: '2026-10-12', canWithdraw: false, ...overrides });

function render() {
  return renderWithProviders(<TimesheetPage />, { route: '/cost/timesheets/7', path: '/cost/timesheets/:timesheetId' });
}

async function fill(values: { date?: string; workPackage?: string; hours?: string; manager?: string }) {
  if (values.date !== undefined) fireEvent.change(await screen.findByLabelText('工作日期'), { target: { value: values.date } });
  if (values.workPackage !== undefined) await userEvent.type(screen.getByLabelText('工作包'), values.workPackage);
  if (values.hours !== undefined) await userEvent.type(screen.getByLabelText('工时'), values.hours);
  if (values.manager !== undefined) await userEvent.type(screen.getByLabelText('项目经理'), values.manager);
}

describe('工时单提交', () => {
  it('草稿：显示状态，工作日期由用户填写，不填写不能提交', async () => {
    const server = stubServer({ 'GET sessions:current': { body: currentUserBody([SUBMIT]) }, 'GET timesheets/7': { body: sheet(), headers: VERSION_HEADER } });
    render();

    expect((await screen.findAllByText('草稿')).length).toBeGreaterThan(0);
    await userEvent.click(await screen.findByRole('button', { name: '提交' }));

    expect(await screen.findByText('请填写工作日期')).toBeInTheDocument();
    expect(screen.getByText('请填写工时')).toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('工作日期晚于今天时不能提交', async () => {
    const server = stubServer({ 'GET sessions:current': { body: currentUserBody([SUBMIT]) }, 'GET timesheets/7': { body: sheet(), headers: VERSION_HEADER } });
    render();

    await fill({ date: '2999-01-01', workPackage: '12', hours: '7.5', manager: '1001' });
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    expect(await screen.findByText('工作日期不能晚于今天')).toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('提交：发出用户填写的工作日期、版本和幂等键；提交不返回审批编号，提交后读取工时单，显示审批中', async () => {
    let submitted = false;
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([SUBMIT]) },
      'GET timesheets/7': () => ({ body: sheet(submitted ? { status: 'SUBMITTED', workDate: '2026-10-12', canWithdraw: true } : {}), headers: VERSION_HEADER }),
      'POST timesheets/7:submit': () => {
        submitted = true;
        return { status: 202 };
      },
    });
    render();

    await fill({ date: '2026-10-12', workPackage: '12', hours: '7.5', manager: '1001' });
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    await waitFor(() => expect(screen.queryByRole('button', { name: '提交' })).not.toBeInTheDocument());
    expect((await screen.findAllByText('审批中')).length).toBeGreaterThan(0);
    const post = server.calls.find((call) => call.method === 'POST');
    expect(post?.body).toEqual({ workDate: '2026-10-12', workPackageId: 12, hours: 7.5, projectManagerId: 1001 });
    expect(post?.headers.get('If-Match')).toBe('"1"');
    expect(post?.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    expect(server.calls.filter((call) => call.method === 'GET' && call.url.includes('timesheets/7')).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole('button', { name: '撤回' })).toBeInTheDocument();
  });

  it('服务端拒绝（工时超过剩余工时）时显示原因，留在本页', async () => {
    stubServer({
      'GET sessions:current': { body: currentUserBody([SUBMIT]) },
      'GET timesheets/7': { body: sheet(), headers: VERSION_HEADER },
      'POST timesheets/7:submit': { status: 422, body: problemBody(422, 'COST_TIMESHEET_EXCEEDS_REMAINING_HOURS') },
    });
    render();

    await fill({ date: '2026-10-12', workPackage: '12', hours: '99', manager: '1001' });
    await userEvent.click(screen.getByRole('button', { name: '提交' }));

    expect(await screen.findByText('填写的工时超过剩余工时')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /提交/ })).toBeInTheDocument();
  });

  it('已退回的工时单可以修改后重新提交', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([SUBMIT]) }, 'GET timesheets/7': { body: sheet({ status: 'RETURNED', round: 1 }), headers: VERSION_HEADER } });
    render();

    expect(await screen.findByRole('button', { name: '提交' })).toBeInTheDocument();
    expect(screen.getAllByText('已退回').length).toBeGreaterThan(0);
  });

  it('审批中、已通过、已驳回的工时单不能再提交', async () => {
    for (const status of ['SUBMITTED', 'APPROVED', 'REJECTED']) {
      stubServer({ 'GET sessions:current': { body: currentUserBody([SUBMIT]) }, 'GET timesheets/7': { body: sheet({ status }), headers: VERSION_HEADER } });
      const view = render();
      await screen.findByText('工时单');
      await waitFor(() => expect(screen.queryByLabelText('工作日期')).not.toBeInTheDocument());
      expect(screen.queryByRole('button', { name: '提交' })).not.toBeInTheDocument();
      view.unmount();
    }
  });

  it('没有提交权限时不显示表单', async () => {
    stubServer({ 'GET sessions:current': { body: currentUserBody([]) }, 'GET timesheets/7': { body: sheet(), headers: VERSION_HEADER } });
    render();

    await screen.findByText('工时单');
    await waitFor(() => expect(screen.queryByLabelText('工作日期')).not.toBeInTheDocument());
  });

  it('撤回必须填写理由', async () => {
    const server = stubServer({
      'GET sessions:current': { body: currentUserBody([SUBMIT]) },
      'GET timesheets/7': { body: sheet({ status: 'SUBMITTED', canWithdraw: true }), headers: VERSION_HEADER },
      'POST timesheets/7:withdraw': { status: 204 },
    });
    render();

    await userEvent.click(await screen.findByRole('button', { name: '撤回' }));
    const confirm = await screen.findByRole('button', { name: '确认撤回' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByLabelText('理由（必填）'), '填错了');
    await userEvent.click(confirm);

    await waitFor(() => expect(server.calls.some((call) => call.url.endsWith(':withdraw'))).toBe(true));
    expect(server.calls.find((call) => call.url.endsWith(':withdraw'))?.body).toEqual({ reason: '填错了' });
  });

  it('工时单编号不是数字时不发请求', async () => {
    const server = stubServer({ 'GET sessions:current': { body: currentUserBody([SUBMIT]) } });
    renderWithProviders(<TimesheetPage />, { route: '/cost/timesheets/abc', path: '/cost/timesheets/:timesheetId' });

    expect(await screen.findByText('工时单不存在')).toBeInTheDocument();
    expect(server.calls.some((call) => call.url.includes('timesheets'))).toBe(false);
  });
});
