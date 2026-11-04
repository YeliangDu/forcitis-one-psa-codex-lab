export interface LoginConfig {
  wecomCorpId: string;
  wecomAgentId: string;
}

type Env = Readonly<Record<string, string | undefined>>;

export function readLoginConfig(env: Env): LoginConfig {
  return {
    wecomCorpId: env['VITE_WECOM_CORP_ID'] ?? '',
    wecomAgentId: env['VITE_WECOM_AGENT_ID'] ?? '',
  };
}

export function wecomConfigured(config: LoginConfig): boolean {
  return config.wecomCorpId !== '' && config.wecomAgentId !== '';
}

export function wecomAuthorizeUrl(config: LoginConfig, state: string, origin: string): string {
  const query = new URLSearchParams({
    login_type: 'CorpApp',
    appid: config.wecomCorpId,
    agentid: config.wecomAgentId,
    redirect_uri: `${origin}/login/wecom`,
    state,
  });
  return `https://login.work.weixin.qq.com/wwlogin/sso/login?${query.toString()}`;
}
