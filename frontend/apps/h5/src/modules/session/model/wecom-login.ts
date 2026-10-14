export interface LoginConfig {
  corpId: string;
}

export interface AuthorizationReply {
  code: string;
  state: string;
}

export function readLoginConfig(corpId: string | undefined): LoginConfig {
  return { corpId: corpId ?? '' };
}

/** WeCom sends the user back to the page with ?code=…&state=… in front of the hash. */
export function authorizationReplyOf(url: string): AuthorizationReply | undefined {
  const params = new URL(url).searchParams;
  const code = params.get('code');
  const state = params.get('state');
  return code === null || code === '' || state === null || state === '' ? undefined : { code, state };
}

export function withoutAuthorizationReply(url: string): string {
  const parsed = new URL(url);
  parsed.searchParams.delete('code');
  parsed.searchParams.delete('state');
  return `${parsed.pathname}${parsed.search}${parsed.hash}`;
}

/** Silent authorisation: snsapi_base returns a code without asking the user anything. */
export function authorizeUrl(config: LoginConfig, state: string, returnUrl: string): string {
  const query = new URLSearchParams({
    appid: config.corpId,
    redirect_uri: returnUrl,
    response_type: 'code',
    scope: 'snsapi_base',
    state,
  });
  return `https://open.weixin.qq.com/connect/oauth2/authorize?${query.toString()}#wechat_redirect`;
}

export const NOT_CONFIGURED_MESSAGE = '尚未配置企业微信登录';
export const SESSION_NOT_KEPT_MESSAGE = '登录成功，登录状态没有保存下来，请重新登录';
