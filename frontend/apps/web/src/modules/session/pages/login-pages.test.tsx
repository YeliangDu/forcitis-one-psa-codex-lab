import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { problemBody, stubServer } from '@forcitis/core/testing';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { EmergencyLoginPage } from './emergency-login-page';
import { LoginPage } from './login-page';

describe('登录页', () => {
  it('只提供企业微信扫码入口，没有致远单点登录入口，也没有口令输入框', async () => {
    stubServer({ 'GET sessions:current': { status: 401, body: {} } });
    renderWithProviders(<LoginPage />, { route: '/login' });

    expect(await screen.findByText('企业微信扫码登录')).toBeInTheDocument();
    expect(screen.queryByText('致远单点登录')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('口令')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('动态口令')).not.toBeInTheDocument();
    expect(screen.queryByText('应急管理员登录')).not.toBeInTheDocument();
  });

  it('企业微信入口尚未配置时置灰并说明', async () => {
    stubServer({ 'GET sessions:current': { status: 401, body: {} } });
    renderWithProviders(<LoginPage />, { route: '/login' });

    expect(await screen.findByText('尚未配置企业微信登录')).toBeInTheDocument();
    expect(screen.queryByText('尚未配置致远单点登录')).not.toBeInTheDocument();
  });
});

describe('应急管理员登录', () => {
  it('表单只在应急登录路由出现', async () => {
    renderWithProviders(<EmergencyLoginPage />, { route: '/emergency' });

    expect(screen.getByRole('heading', { name: '应急管理员登录' })).toBeInTheDocument();
    expect(screen.getByLabelText('登录名')).toBeInTheDocument();
    expect(screen.getByLabelText('口令')).toBeInTheDocument();
    expect(screen.getByLabelText('动态口令')).toBeInTheDocument();
  });

  it('提交登录名、口令和动态口令，成功后进入系统', async () => {
    const server = stubServer({ 'POST sessions:emergency-login': { body: { userId: '019a0000-0000-7000-8000-000000000009', absoluteExpiresAt: '2026-10-15T00:00:00Z', identityConfirmationRequired: false } } });
    renderWithProviders(<EmergencyLoginPage />, { route: '/emergency' });

    await userEvent.type(screen.getByLabelText('登录名'), 'break-glass');
    await userEvent.type(screen.getByLabelText('口令'), 'a-long-passphrase');
    await userEvent.type(screen.getByLabelText('动态口令'), '123456');
    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/));
    expect(server.calls.find((call) => call.method === 'POST')?.body).toEqual({ loginName: 'break-glass', password: 'a-long-passphrase', totpCode: '123456' });
  });

  it('登录失败时显示中文原因，不跳转到登录页或权限不足页', async () => {
    stubServer({ 'POST sessions:emergency-login': { status: 401, body: problemBody(401, 'PLATFORM_IDENTITY_CREDENTIALS_INVALID') } });
    renderWithProviders(<EmergencyLoginPage />, { route: '/emergency', path: '/emergency' });

    await userEvent.type(screen.getByLabelText('登录名'), 'break-glass');
    await userEvent.type(screen.getByLabelText('口令'), 'wrong');
    await userEvent.type(screen.getByLabelText('动态口令'), '000000');
    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('登录名、口令或动态口令不正确')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/emergency');
  });

  it('不在内网时说明原因', async () => {
    stubServer({ 'POST sessions:emergency-login': { status: 403, body: problemBody(403, 'PLATFORM_IDENTITY_NETWORK_DENIED') } });
    renderWithProviders(<EmergencyLoginPage />, { route: '/emergency', path: '/emergency' });

    await userEvent.type(screen.getByLabelText('登录名'), 'break-glass');
    await userEvent.type(screen.getByLabelText('口令'), 'x');
    await userEvent.type(screen.getByLabelText('动态口令'), '000000');
    await userEvent.click(screen.getByRole('button', { name: '登录' }));

    expect(await screen.findByText('应急管理员只能在公司内网登录')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/emergency');
  });
});
