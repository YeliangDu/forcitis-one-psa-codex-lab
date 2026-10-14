import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { VERSION_HEADER, currentUserBody, problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { WorkCalendarPage } from './work-calendar-page';

const READ = 'organization.attendance:work-calendar:read';
const UPDATE = 'organization.attendance:work-calendar:update';

const PAST = { calendarYear: 2020, days: [{ calendarDate: '2020-10-01', dayKind: 'HOLIDAY' }] };
const FUTURE = { calendarYear: 2999, days: [] };

function server(extra: Parameters<typeof stubServer>[0] = {}, permissions: string[] = [READ, UPDATE]) {
  return stubServer({
    'GET sessions:current': { body: currentUserBody(permissions) },
    'GET work-calendars/2020': { body: PAST, headers: VERSION_HEADER },
    'GET work-calendars/2999': { body: FUTURE, headers: VERSION_HEADER },
    'GET work-calendars': { body: { items: [{ calendarYear: 2020 }, { calendarYear: 2999 }] } },
    ...extra,
  });
}

async function openYear(year: string) {
  renderWithProviders(<WorkCalendarPage />);
  await userEvent.selectOptions(await screen.findByLabelText('年度'), year);
}

describe('工作日历维护页', () => {
  it('按年显示每一天是工作日、休息日、节假日还是调休上班日；过去的日期标为已锁定', async () => {
    server();
    await openYear('2020');

    expect(await screen.findByRole('button', { name: '2020-10-01 节假日 已锁定' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2020-10-03 休息日 已锁定' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2020-10-05 工作日 已锁定' })).toBeInTheDocument();
  });

  it('未来的日期不锁定，可以直接改成节假日并保存，保存时带着读到的版本', async () => {
    const requests = server({ 'PUT work-calendars/2999': { body: FUTURE } });
    await openYear('2999');

    await userEvent.click(await screen.findByRole('button', { name: '2999-01-02 工作日' }));
    await userEvent.selectOptions(await screen.findByLabelText('日期类型'), 'HOLIDAY');
    await userEvent.click(screen.getByRole('button', { name: '确定' }));
    expect(screen.getByRole('button', { name: '2999-01-02 节假日' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));

    await waitFor(() => expect(requests.calls.some((call) => call.method === 'PUT')).toBe(true));
    const put = requests.calls.find((call) => call.method === 'PUT');
    expect(put?.body).toEqual({ days: [{ calendarDate: '2999-01-02', dayKind: 'HOLIDAY' }], lockedDaysConfirmed: false });
    expect(put?.headers.get('If-Match')).toBe('"1"');
    expect(put?.headers.get('Idempotency-Key')).toBeNull();
  });

  it('调休上班日只能设在周末', async () => {
    server();
    await openYear('2999');

    await userEvent.click(await screen.findByRole('button', { name: '2999-01-02 工作日' }));
    expect(within(await screen.findByLabelText('日期类型')).queryByRole('option', { name: '调休上班日' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '取消' }));
    await userEvent.click(screen.getByRole('button', { name: '2999-01-05 休息日' }));
    expect(within(await screen.findByLabelText('日期类型')).getByRole('option', { name: '调休上班日' })).toBeInTheDocument();
  });

  it('修改已锁定的日期要二次确认；不确认就不发请求，确认后带着确认标记保存', async () => {
    const requests = server({ 'PUT work-calendars/2020': { body: PAST } });
    await openYear('2020');

    await userEvent.click(await screen.findByRole('button', { name: '2020-10-01 节假日 已锁定' }));
    expect(await screen.findByText('这个日期已锁定')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('日期类型'), '');
    await userEvent.click(screen.getByRole('button', { name: '确定' }));
    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));

    const dialog = (await screen.findByText('确认修改已锁定的日期')).closest('[role="dialog"]') as HTMLElement;
    expect(within(dialog).getByText('2020-10-01')).toBeInTheDocument();
    expect(requests.calls.some((call) => call.method === 'PUT')).toBe(false);
    await userEvent.click(within(dialog).getByRole('button', { name: '取消' }));
    expect(requests.calls.some((call) => call.method === 'PUT')).toBe(false);

    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
    await userEvent.click(await screen.findByRole('button', { name: '确认修改' }));
    await waitFor(() => expect(requests.calls.some((call) => call.method === 'PUT')).toBe(true));
    expect(requests.calls.find((call) => call.method === 'PUT')?.body).toEqual({ days: [], lockedDaysConfirmed: true });
  });

  it('日历里出现服务端新加的日期类型：显示破折号，不报错', async () => {
    server({ 'GET work-calendars/2020': { body: { calendarYear: 2020, days: [{ calendarDate: '2020-10-01', dayKind: 'COMPANY_DAY' }] }, headers: VERSION_HEADER } });
    await openYear('2020');

    expect(await screen.findByRole('button', { name: '2020-10-01 — 已锁定' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2020-10-02 工作日 已锁定' })).toBeInTheDocument();
  });

  it('没有修改权限时只能看，不能点日期，也没有保存按钮', async () => {
    server({}, [READ]);
    await openYear('2020');

    expect(await screen.findByRole('button', { name: '2020-10-01 节假日 已锁定' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: '保存修改' })).not.toBeInTheDocument();
  });

  it('这一年还没有维护过时提示，保存第一份日历时带 If-Match "0"（B3），不带 If-None-Match', async () => {
    const requests = server({
      'GET work-calendars/2999': { status: 404, body: problemBody(404, 'ORGANIZATION_ATTENDANCE_YEAR_NOT_FOUND') },
      'PUT work-calendars/2999': { body: FUTURE },
    });
    await openYear('2999');

    expect(await screen.findByText('这个年度还没有维护工作日历')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: '2999-01-02 工作日' }));
    await userEvent.selectOptions(await screen.findByLabelText('日期类型'), 'HOLIDAY');
    await userEvent.click(screen.getByRole('button', { name: '确定' }));
    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));

    await waitFor(() => expect(requests.calls.some((call) => call.method === 'PUT')).toBe(true));
    const put = requests.calls.find((call) => call.method === 'PUT');
    expect(put?.headers.get('If-Match')).toBe('"0"');
    expect(put?.headers.has('If-None-Match')).toBe(false);
  });

  it('保存时日历已被别人修改（412）：提示刷新，重新读到新版本，草稿被丢弃并说明', async () => {
    let reads = 0;
    server({
      'GET work-calendars/2999': () => {
        reads += 1;
        return { body: reads === 1 ? FUTURE : { calendarYear: 2999, days: [{ calendarDate: '2999-01-03', dayKind: 'HOLIDAY' }] }, headers: { ETag: `"${reads}"` } };
      },
      'PUT work-calendars/2999': { status: 412, body: problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH') },
    });
    await openYear('2999');

    await userEvent.click(await screen.findByRole('button', { name: '2999-01-02 工作日' }));
    await userEvent.selectOptions(await screen.findByLabelText('日期类型'), 'HOLIDAY');
    await userEvent.click(screen.getByRole('button', { name: '确定' }));
    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));

    expect(await screen.findByText('内容刚刚被其他人修改，请刷新后重试')).toBeInTheDocument();
    expect(await screen.findByText('已载入最新内容，你的修改未保存')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2999-01-02 工作日' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '2999-01-03 节假日' })).toBeInTheDocument();
    expect(reads).toBe(2);
  });

  it('重新读到的内容相同、只有版本不同时，草稿保留，保存用新版本', async () => {
    let reads = 0;
    const requests = server({
      'GET work-calendars/2999': () => {
        reads += 1;
        return { body: FUTURE, headers: { ETag: `"${reads}"` } };
      },
      'PUT work-calendars/2999': (request) => (request.headers.get('If-Match') === '"1"' ? { status: 412, body: problemBody(412, 'PLATFORM_WEB_VERSION_MISMATCH') } : { body: FUTURE }),
    });
    await openYear('2999');

    await userEvent.click(await screen.findByRole('button', { name: '2999-01-02 工作日' }));
    await userEvent.selectOptions(await screen.findByLabelText('日期类型'), 'HOLIDAY');
    await userEvent.click(screen.getByRole('button', { name: '确定' }));
    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
    expect(await screen.findByText('内容刚刚被其他人修改，请刷新后重试')).toBeInTheDocument();
    await waitFor(() => expect(reads).toBe(2));
    expect(screen.queryByText('已载入最新内容，你的修改未保存')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '保存修改' }));
    await waitFor(() => expect(requests.calls.filter((call) => call.method === 'PUT')).toHaveLength(2));
    expect(requests.calls.filter((call) => call.method === 'PUT')[1]?.headers.get('If-Match')).toBe('"2"');
  });
});
