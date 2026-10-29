#!/usr/bin/env python3
"""FORCITIS one · C-7 · 运维告警推送（企业微信）。安装为 /usr/local/sbin/one_alert_send，root 运行。

用法：one_alert_send <P1|P2|TEST> <消息>
渠道取自 /etc/forcitis-one/ops-alert.env（root 0600）：有 ONE_ALERT_WEBHOOK 时发到「FORCITIS one 运维」群机器人（A-5 现行渠道）；
否则以 FORCITIS one 应用消息发给 ONE_ALERT_WECOM_USERID（应用凭据取自 one.env 的 WECOM_CORP_ID、WECOM_AGENT_ID、WECOM_APP_SECRET）。
直接调企业微信接口，不依赖业务服务；不打印任何凭据。
退出码：0 已送达企业微信；1 发送失败；2 配置缺失。
"""
import json
import socket
import sys
import urllib.parse
import urllib.request

ONE_ENV = '/etc/forcitis-one/one.env'
ALERT_ENV = '/etc/forcitis-one/ops-alert.env'
API = 'https://qyapi.weixin.qq.com/cgi-bin'


def read_env(path):
    values = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            key, value = line.split('=', 1)
            values[key.strip()] = value.strip().strip("'\"")
    return values


def call(url, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    request = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.load(response)


def main():
    if len(sys.argv) != 3 or sys.argv[1] not in ('P1', 'P2', 'TEST'):
        print(__doc__, file=sys.stderr)
        return 2
    level, message = sys.argv[1], sys.argv[2]
    text = f'【FORCITIS one 运维 · {level}】{socket.gethostname()}\n{message}'
    try:
        webhook = read_env(ALERT_ENV).get('ONE_ALERT_WEBHOOK', '')
    except OSError:
        print('one_alert_send: configuration missing: ops-alert.env', file=sys.stderr)
        return 2
    if webhook:
        try:
            sent = call(webhook, {'msgtype': 'text', 'text': {'content': text}})
        except (OSError, ValueError) as failure:
            print(f'one_alert_send: request failed: {type(failure).__name__}', file=sys.stderr)
            return 1
        if sent.get('errcode') != 0:
            print(f"one_alert_send: webhook errcode={sent.get('errcode')}", file=sys.stderr)
            return 1
        print('one_alert_send: delivered')
        return 0
    try:
        app, alert = read_env(ONE_ENV), read_env(ALERT_ENV)
        corp, agent, secret, user = app['WECOM_CORP_ID'], app['WECOM_AGENT_ID'], app['WECOM_APP_SECRET'], alert['ONE_ALERT_WECOM_USERID']
    except (OSError, KeyError) as missing:
        print(f'one_alert_send: configuration missing: {missing}', file=sys.stderr)
        return 2
    if not all((corp, agent, secret, user)):
        print('one_alert_send: configuration has empty values', file=sys.stderr)
        return 2
    try:
        token = call(f'{API}/gettoken?' + urllib.parse.urlencode({'corpid': corp, 'corpsecret': secret}))
        if token.get('errcode') != 0:
            print(f"one_alert_send: gettoken errcode={token.get('errcode')}", file=sys.stderr)
            return 1
        sent = call(f"{API}/message/send?access_token={token['access_token']}",
                    {'touser': user, 'msgtype': 'text', 'agentid': int(agent), 'text': {'content': text}, 'safe': 0})
    except (OSError, ValueError) as failure:
        print(f'one_alert_send: request failed: {type(failure).__name__}', file=sys.stderr)
        return 1
    if sent.get('errcode') != 0 or sent.get('invaliduser'):
        print(f"one_alert_send: message/send errcode={sent.get('errcode')} invaliduser={'yes' if sent.get('invaliduser') else 'no'}", file=sys.stderr)
        return 1
    print('one_alert_send: delivered')
    return 0


if __name__ == '__main__':
    sys.exit(main())
