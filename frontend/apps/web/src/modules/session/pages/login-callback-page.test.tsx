import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { LoginCallbackPage } from './login-callback-page';

describe('登录回调页', () => {
  afterEach(() => window.history.replaceState(null, '', '/'));

  it('企业微信回调：提交 code 和 state，并立刻把它们从地址栏清掉', async () => {
    window.history.replaceState(null, '', '/login/wecom?code=C-1&state=S-1');
    const server = stubServer({ 'POST sessions:wecom-login': { body: { userId: '1', absoluteExpiresAt: '2026-10-15T00:00:00Z' } } });
    renderWithProviders(<LoginCallbackPage kind="wecom" />, { route: '/login/wecom' });

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ code: 'C-1', state: 'S-1', entry: 'QR_CODE_LOGIN' });
    expect(window.location.search).toBe('');
  });

  it('致远回调：提交票据，票据不留在地址栏', async () => {
    window.history.replaceState(null, '', '/login/sso?ticket=TK-9');
    const server = stubServer({ 'POST sessions:sso-login': { body: { userId: '1', absoluteExpiresAt: '2026-10-15T00:00:00Z' } } });
    renderWithProviders(<LoginCallbackPage kind="sso" />, { route: '/login/sso' });

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ ticket: 'TK-9' });
    expect(window.location.search).toBe('');
  });

  it('致远不可用时显示原因，并给出回到老系统登录的链接', async () => {
    window.history.replaceState(null, '', '/login/sso?ticket=TK-9');
    stubServer({ 'POST sessions:sso-login': { status: 503, body: { errorCode: 'PLATFORM_IDENTITY_SSO_UNAVAILABLE', legacyLoginUrl: 'https://old.example.com/login' } } });
    renderWithProviders(<LoginCallbackPage kind="sso" />, { route: '/login/sso' });

    expect(await screen.findByText('致远暂时无法验证登录，请稍后重试或回到老系统登录')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '回到老系统登录' })).toHaveAttribute('href', 'https://old.example.com/login');
  });

  it('缺少参数时不发请求，直接提示重新登录', async () => {
    window.history.replaceState(null, '', '/login/wecom');
    const server = stubServer({});
    renderWithProviders(<LoginCallbackPage kind="wecom" />, { route: '/login/wecom' });

    expect(await screen.findByText('登录信息不完整，请重新登录')).toBeInTheDocument();
    expect(server.calls).toHaveLength(0);
  });
});
