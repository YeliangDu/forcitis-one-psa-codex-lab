# B-9 交接包：企业微信接口真实返回核对（给运维 + 企业微信管理员）

收件人：运维（在 ECS 上执行），企业微信管理员（准备测试成员）。时点：1a 出口前；须在 B-2 完成、dev 已部署后。依据：1a 第二波报告；`platform_integration.properties` 中 `duplicate-task-error-codes` 为空（注释：公开错误码表未列出，须核实后再设）。

## 要核实的两件事

1. **同一 `task_id` 重复发送模板卡片时，企业微信返回的 `errcode` 是什么。** FORCITIS one 出站投递可能因重投而重复发送同一张卡片，需要把「已发送过」的错误码识别为成功，否则会一直重试直到死信。
2. **模板卡片更新（`message/update_template_card`）的真实返回**：用发送时返回的 `response_code` 把卡片更新为「已处理 / 已超时，已升级给某人」能否成功，`response_code` 用过一次后再用的返回。

## 执行前确认点

- 只对**测试成员**发送（企业微信管理员指定 1–2 名项目组成员，并在 one.env 的 `WECOM_RECIPIENT_ALLOWLIST` 中），不发给其他人。
- 在 ECS 上执行（企业可信 IP 只放行 47.114.56.157）。
- `access_token` 只在 shell 变量中，不打印、不写文件；Secret 从 `/etc/forcitis-one/one.env` 读取。

## 命令（ECS，root，经云助手）

```bash
set -a; . /etc/forcitis-one/one.env; set +a
# 机密只经进程替换以文件形式交给 curl，不出现在命令行参数（ps 看不到）；需要 curl ≥ 7.87（--url-query）
TOKEN=$(curl -s "https://qyapi.weixin.qq.com/cgi-bin/gettoken" --url-query "corpid=${WECOM_CORP_ID}" \
  --url-query "corpsecret@"<(printf %s "$WECOM_APP_SECRET") | python3 -c 'import json,sys;print(json.load(sys.stdin)["access_token"])')
wecom() { curl -s -X POST "https://qyapi.weixin.qq.com/cgi-bin/$1" --url-query "access_token@"<(printf %s "$TOKEN") -d "$2"; }
TO="<测试成员 userid>"          # 由企业微信管理员给出
TASK="one-b9-$(date +%s)"
BODY=$(python3 - "$TO" "$WECOM_AGENT_ID" "$TASK" <<'PY'
import json, sys
to, agent, task = sys.argv[1:]
print(json.dumps({"touser": to, "msgtype": "template_card", "agentid": int(agent),
  "template_card": {"card_type": "text_notice", "task_id": task,
    "main_title": {"title": "FORCITIS one 接口核对", "desc": "B-9 测试，请忽略"},
    "card_action": {"type": 1, "url": "https://one.forcitis.com/"}}}))
PY
)
# 1) 首次发送：期望 errcode=0，记下 response_code（不打印 token）
wecom message/send "$BODY" | tee /tmp/b9-first.json
# 2) 同一 task_id 再发一次：记录 errcode 与 errmsg —— 这就是要设置的「重复」错误码
wecom message/send "$BODY"
# 3) 更新卡片
RC=$(python3 -c 'import json;print(json.load(open("/tmp/b9-first.json")).get("response_code",""))')
wecom message/update_template_card \
  "{\"userids\":[\"${TO}\"],\"agentid\":${WECOM_AGENT_ID},\"response_code\":\"${RC}\",\"button\":{\"replace_name\":\"已处理\"}}"
# 4) 同一 response_code 再更新一次：记录 errcode
wecom message/update_template_card \
  "{\"userids\":[\"${TO}\"],\"agentid\":${WECOM_AGENT_ID},\"response_code\":\"${RC}\",\"button\":{\"replace_name\":\"已处理\"}}"
rm -f /tmp/b9-first.json; unset TOKEN WECOM_APP_SECRET; unset -f wecom
```

`text_notice` 卡片没有按钮，第 3 步若返回「卡片类型不支持」类错误，也照实记录（1a 的卡片就是 `text_notice`，D-1a-138 按钮回调已推迟）；更新卡片要用的卡片类型由 1a 修复批据此决定。

## 回传与落地

- 回报 one-coordinator：四次调用的 `errcode`、`errmsg`（不含 token、response_code）。
- 运维把第 2 步的 `errcode` 写入 one.env：`FORCITIS_PLATFORM_INTEGRATION_WECOM_DELIVERY_DUPLICATE_TASK_ERROR_CODES=<errcode>`（多个用逗号分隔），重启应用；同时请代码会话把该值写进 `platform_integration.properties` 并补测试，作为默认值。

## 验收标准

- 四个返回均已记录；重复错误码已配置；dev 上人为触发一次重投，投递记为成功而非死信（`forcitis.platform.integration.deliveries.dead.letters` 不增加）。
