import { describe, expect, it } from 'vitest';
import { readLoginConfig, wecomAuthorizeUrl, wecomConfigured } from './login-config';

describe('login config', () => {
  it('未配置时企业微信登录不可用', () => {
    expect(wecomConfigured(readLoginConfig({}))).toBe(false);
    expect(wecomConfigured(readLoginConfig({ VITE_WECOM_CORP_ID: 'corp', VITE_WECOM_AGENT_ID: '1000002' }))).toBe(true);
  });

  it('扫码地址带一次性 state 和回调地址，回调落在 /login/wecom', () => {
    const url = new URL(wecomAuthorizeUrl(readLoginConfig({ VITE_WECOM_CORP_ID: 'corp', VITE_WECOM_AGENT_ID: '1000002' }), 'st-1', 'https://one.example'));
    expect(url.origin).toBe('https://login.work.weixin.qq.com');
    expect(url.searchParams.get('state')).toBe('st-1');
    expect(url.searchParams.get('redirect_uri')).toBe('https://one.example/login/wecom');
    expect(url.searchParams.get('appid')).toBe('corp');
    expect(url.searchParams.get('agentid')).toBe('1000002');
  });
});
