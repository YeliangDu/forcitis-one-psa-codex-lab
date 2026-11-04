import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { LoginCallbackPage } from './login-callback-page';

describe('登录回调页', () => {
  afterEach(() => window.history.replaceState(null, '', '/'));

  it('企业微信回调：提交 code 和 state，并立刻把它们从地址栏清掉', async () => {
    window.history.replaceState(null, '', '/login/wecom?code=C-1&state=S-1');
    const server = stubServer({ 'POST sessions:wecom-login': { body: { userId: '019a0000-0000-7000-8000-000000000001', absoluteExpiresAt: '2026-10-15T00:00:00Z', identityConfirmationRequired: false } } });
    renderWithProviders(<LoginCallbackPage kind="wecom" />, { route: '/login/wecom' });

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ code: 'C-1', state: 'S-1', entry: 'QR_CODE_LOGIN' });
    expect(window.location.search).toBe('');
  });

  it('外部系统的签名票据：从地址的片段里取，提交后片段也被清掉', async () => {
    window.history.replaceState(null, '', '/login/ticket#ticket=SIGNED.1');
    const server = stubServer({ 'POST sessions:ticket-login': { body: { userId: '019a0000-0000-7000-8000-000000000001', absoluteExpiresAt: '2026-10-15T00:00:00Z' } } });
    renderWithProviders(<LoginCallbackPage kind="ticket" />, { route: '/login/ticket' });

    await waitFor(() => expect(server.calls.some((call) => call.method === 'POST')).toBe(true));
    expect(server.calls.find((call) => call.method === 'POST')?.url.endsWith('sessions:ticket-login')).toBe(true);
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ ticket: 'SIGNED.1' });
    expect(window.location.hash).toBe('');
  });

  it('外部票据放在查询参数里不被接受，不发请求', async () => {
    window.history.replaceState(null, '', '/login/ticket?ticket=SIGNED.1');
    const server = stubServer({});
    renderWithProviders(<LoginCallbackPage kind="ticket" />, { route: '/login/ticket' });

    expect(await screen.findByText('登录信息不完整，请重新登录')).toBeInTheDocument();
    expect(server.calls).toHaveLength(0);
  });

  it('缺少参数时不发请求，直接提示重新登录', async () => {
    window.history.replaceState(null, '', '/login/wecom');
    const server = stubServer({});
    renderWithProviders(<LoginCallbackPage kind="wecom" />, { route: '/login/wecom' });

    expect(await screen.findByText('登录信息不完整，请重新登录')).toBeInTheDocument();
    expect(server.calls).toHaveLength(0);
  });
});
