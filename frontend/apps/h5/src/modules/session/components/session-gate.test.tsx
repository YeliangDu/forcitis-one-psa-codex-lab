import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { currentUserBody } from '@/shared/testing/fixtures';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { SessionGate } from './session-gate';

const leaveTo = vi.hoisted(() => vi.fn());
vi.mock('@/shared/platform/browser', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/shared/platform/browser')>()), leaveTo }));

const CONFIG = { corpId: 'ww-corp' };
const CHILD = <p>待办内容</p>;

describe('企业微信工作台静默登录', () => {
  it('已有会话：直接进入，不发起登录', async () => {
    const server = stubServer({ 'GET sessions:current': { body: currentUserBody([]) } });
    renderWithProviders(<SessionGate config={CONFIG}>{CHILD}</SessionGate>);

    expect(await screen.findByText('待办内容')).toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
  });

  it('没有会话也没有授权回跳：申请 state（入口为工作台），再带着它去静默授权', async () => {
    window.history.replaceState(null, '', '/#/app/pages/inbox');
    const server = stubServer({
      'GET sessions:current': { status: 401, body: {} },
      'POST login-states': { body: { state: 'S-1' } },
    });
    renderWithProviders(<SessionGate config={CONFIG}>{CHILD}</SessionGate>);

    await waitFor(() => expect(leaveTo).toHaveBeenCalledTimes(1));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ entry: 'WORKBENCH' });
    const target = new URL(String(leaveTo.mock.calls[0]?.[0]));
    expect(target.origin + target.pathname).toBe('https://open.weixin.qq.com/connect/oauth2/authorize');
    expect(target.searchParams.get('scope')).toBe('snsapi_base');
    expect(target.searchParams.get('appid')).toBe('ww-corp');
    expect(target.searchParams.get('state')).toBe('S-1');
    expect(target.searchParams.get('redirect_uri')).toContain('#/app/pages/inbox');
    expect(screen.queryByText('待办内容')).not.toBeInTheDocument();
  });

  it('授权回跳：code 和 state 在提交登录之前就离开地址栏，提交后进入', async () => {
    window.history.replaceState(null, '', '/?code=C-1&state=S-1#/app/pages/inbox');
    let signedIn = false;
    let addressWhenPosting = '';
    const server = stubServer({
      'GET sessions:current': () => (signedIn ? { body: currentUserBody([]) } : { status: 401, body: {} }),
      'POST sessions:wecom-login': () => {
        addressWhenPosting = window.location.href;
        signedIn = true;
        return { body: { userId: '1001', absoluteExpiresAt: '2026-10-15T01:00:00Z', identityConfirmationRequired: false } };
      },
    });
    renderWithProviders(<SessionGate config={CONFIG}>{CHILD}</SessionGate>);

    expect(await screen.findByText('待办内容')).toBeInTheDocument();
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ code: 'C-1', state: 'S-1', entry: 'WORKBENCH' });
    expect(addressWhenPosting).not.toContain('code=');
    expect(addressWhenPosting).not.toContain('state=');
    expect(window.location.search).toBe('');
    expect(window.location.hash).toBe('#/app/pages/inbox');
    expect(leaveTo).not.toHaveBeenCalled();
  });

  it('登录被拒绝：显示原因，可重新登录，重新登录会再去授权而不是重复使用旧 code', async () => {
    window.history.replaceState(null, '', '/?code=C-1&state=S-1');
    const server = stubServer({
      'GET sessions:current': { status: 401, body: {} },
      'POST sessions:wecom-login': { status: 403, body: { errorCode: 'PLATFORM_IDENTITY_WECOM_NOT_BOUND' } },
      'POST login-states': { body: { state: 'S-2' } },
    });
    renderWithProviders(<SessionGate config={CONFIG}>{CHILD}</SessionGate>);

    expect(await screen.findByText('该企业微信用户尚未对应到员工，请联系管理员')).toBeInTheDocument();
    expect(leaveTo).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: '重新登录' }));
    await waitFor(() => expect(leaveTo).toHaveBeenCalledTimes(1));
    expect(server.calls.filter((call) => call.url.includes('wecom-login'))).toHaveLength(1);
  });

  it('登录成功但会话没有保存下来：报错，不再循环去授权', async () => {
    window.history.replaceState(null, '', '/?code=C-1&state=S-1');
    stubServer({
      'GET sessions:current': { status: 401, body: {} },
      'POST sessions:wecom-login': { body: { userId: '1001', absoluteExpiresAt: '2026-10-15T01:00:00Z', identityConfirmationRequired: false } },
    });
    renderWithProviders(<SessionGate config={CONFIG}>{CHILD}</SessionGate>);

    expect(await screen.findByText('登录成功，但登录状态没有保存下来，请重新登录')).toBeInTheDocument();
    expect(leaveTo).not.toHaveBeenCalled();
  });

  it('没有配置企业 ID：直接提示，不发起任何登录请求', async () => {
    const server = stubServer({ 'GET sessions:current': { status: 401, body: {} } });
    renderWithProviders(<SessionGate config={{ corpId: '' }}>{CHILD}</SessionGate>);

    expect(await screen.findByText('尚未配置企业微信登录')).toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
    expect(leaveTo).not.toHaveBeenCalled();
  });

  it('查询会话时服务不可用：提示原因，不去登录', async () => {
    const server = stubServer({ 'GET sessions:current': { status: 503, body: {} } });
    renderWithProviders(<SessionGate config={CONFIG}>{CHILD}</SessionGate>);

    expect(await screen.findByText('服务暂时不可用，请稍后重试')).toBeInTheDocument();
    expect(server.calls.some((call) => call.method === 'POST')).toBe(false);
  });
});
