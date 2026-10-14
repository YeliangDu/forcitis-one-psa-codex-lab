import { describe, expect, it } from 'vitest';
import { authorizationReplyOf, authorizeUrl, readLoginConfig, withoutAuthorizationReply } from './wecom-login';

describe('企业微信静默授权的地址处理', () => {
  it('从授权回跳地址里取出 code 和 state', () => {
    expect(authorizationReplyOf('https://one.example.com/?code=C-1&state=S-1#/app/pages/inbox')).toEqual({ code: 'C-1', state: 'S-1' });
  });

  it('缺 code 或缺 state 时视为没有授权回跳', () => {
    expect(authorizationReplyOf('https://one.example.com/?code=C-1#/app/pages/inbox')).toBeUndefined();
    expect(authorizationReplyOf('https://one.example.com/?state=S-1')).toBeUndefined();
    expect(authorizationReplyOf('https://one.example.com/?code=&state=S-1')).toBeUndefined();
    expect(authorizationReplyOf('https://one.example.com/#/app/pages/inbox')).toBeUndefined();
  });

  it('清掉 code 和 state，其余参数和页面路由保留', () => {
    expect(withoutAuthorizationReply('https://one.example.com/?code=C-1&state=S-1&from=wecom#/app/pages/task?taskId=7'))
      .toBe('/?from=wecom#/app/pages/task?taskId=7');
    expect(withoutAuthorizationReply('https://one.example.com/?code=C-1&state=S-1#/app/pages/inbox')).toBe('/#/app/pages/inbox');
  });

  it('授权地址使用 snsapi_base 和企业 ID，回跳地址经过编码', () => {
    const url = authorizeUrl(readLoginConfig('ww-corp'), 'S-9', 'https://one.example.com/?x=1#/app/pages/inbox');
    expect(url.startsWith('https://open.weixin.qq.com/connect/oauth2/authorize?')).toBe(true);
    expect(url.endsWith('#wechat_redirect')).toBe(true);
    const query = new URL(url).searchParams;
    expect(query.get('appid')).toBe('ww-corp');
    expect(query.get('scope')).toBe('snsapi_base');
    expect(query.get('response_type')).toBe('code');
    expect(query.get('state')).toBe('S-9');
    expect(query.get('redirect_uri')).toBe('https://one.example.com/?x=1#/app/pages/inbox');
  });

  it('没有配置企业 ID 时读出空值，由调用方判定为未配置', () => {
    expect(readLoginConfig(undefined)).toEqual({ corpId: '' });
  });
});
