# 待你决定（收件箱在线页）

所有会话要用户定的事都写到这一页，用户只看这一处。页面：https://claude.ai/artifact/RVU6nNNSsygF5sVRWLhQ15 ，源码 `page.html`（不经生成器；重新发布时把脚本包进独立作用域：`<script>` 后加 `(() => {`，`</script>` 前加 `})();`）。日常内容由 one-coordinator 维护；页面由 one-rules 维护。

## 写入与读回

用 ArtifactData，`url` 为页面链接，集合名 `inbox`，文档 id 为事项编号（建议 `<日期>-<会话名>-<短名>`，字母数字与连字符）。新建事项用 `set`；改状态、撤回用 `update` 并带 `if_version`。读回用 `list` 或 `query`（`where: ["status","==","pending"]`）。集合只许页面所有者（用户）与会话以所有者身份写，查看权限只读。

## 渠道

收件箱是要用户定的事的唯一处理点：会话里只说「已进收件箱 N 项」，不在对话里列问题让用户答，不把新事项发到 GitHub。已在 GitHub 发起的事（如用户在某 PR 评论里裁定）在原评论处收，同时在本收件箱建一条 pending 指向该评论；转述引用户原句。用户说「答完」时，调度扫本收件箱（decidedAt 晚于上次核对）与最近更新的确认页，并顺查近期 PR 评论。

## 字段

| 字段 | 必填 | 取值 / 说明 |
|---|---|---|
| `source` | 是 | 来源会话名，如 `one-rules` |
| `title` | 是 | 一句话标题 |
| `ask` | 否 | 要用户选什么、定什么 |
| `options` | 否 | 数组 `[{"label": "选项", "effect": "后果，可空"}]`；没有选项时页面给文字答复框。**不预选** |
| `sheet` | 否 | 对应确认页的单子编号（如 `FD2`）；写了它，调度用 `decide_sync.py --check` 才能核对「收件箱 pending 但确认页已提交」；不写则按 `link` 与 `docs/decisions/decision-pages/README.md` 页面表对应 |
| `link` | 否 | 确认页链接，`https://` 开头；有则页面显示「打开确认页」 |
| `blocker` | 否 | 卡住了什么 |
| `urgency` | 是 | `main` 卡主线 / `normal` 一般 / `low` 不急（待定区按此排序，同级按 `createdAt`） |
| `status` | 是 | `pending` 待定 / `decided` 已定 / `withdrawn` 撤回 |
| `createdAt` | 是 | ISO 时间 |
| `answer` | 已定时 | `{"choice": 选项序号或 null, "label": "所选选项文字", "text": "文字答复", "note": "说明", "by": 用户 id, "at": ISO 时间}`；用户在页面上作答时由页面写入 |
| `decidedAt` | 已定时 | ISO 时间 |
| `intent` | 登记口头决定时必填 | intent 文件名，如 `2026-10-16-054259-one-rules-工时填报义务决策页-FD2-6.md` |
| `withdrawnReason` | 撤回时 | 撤回原因 |
| `answeredByPage` | 页面写 | 用户在页面上作答的事项带此标记，才显示「改回待定」；会话不要写 |

## 示例

待定事项：

```json
{"source": "one-rules", "title": "最低时数是否也计待批请假", "ask": "待批请假算不算最低时数的扣减？",
 "options": [{"label": "算", "effect": "待批请假当天也能提交，工时单标「含待批请假」"}, {"label": "不算"}],
 "link": "https://claude.ai/artifact/DWcj2U7HrYZ2zmVNreBmfS", "blocker": "Phase 2 工时提交规则卡待写",
 "urgency": "normal", "status": "pending", "createdAt": "2026-10-16T05:00:00Z"}
```

登记用户口头作的决定（已定区）：

```json
{"source": "one-coordinator", "title": "整天请假日也可自愿提交工时单", "status": "decided", "urgency": "low",
 "createdAt": "2026-10-16T05:00:00Z", "decidedAt": "2026-10-16T05:00:00Z",
 "answer": {"text": "请假日也自愿填，补上", "at": "2026-10-16T05:00:00Z"}, "intent": "<intent 文件名>"}
```
