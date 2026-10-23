# 角色：dev

独占路径：无

多个开发会话共用 `backend/`、`frontend/`，故不声明独占：每个会话只改本卡列明的文件，不改其他角色的独占路径；`backend/architecture/` 等共享登记只改本卡条目。

## 职责
- 一个 bc 从卡到合入端到端负责：实现、单元测试、本 bc 页面、PR、复审、回改。
- 写代码先读 CODING_STANDARD、NAMING_STANDARD。
- 复审：开 PR 后按 `scripts/review/PROMPT.md` 拉起复审子代理；子代理只写结论文件，post_review 由派工人运行，提交带机读头（结论、commit、复审者）的普通 review。回改后在同一线程逐条回复，由新子代理带上一轮 review 全文只复核必改项。安全、生产、钩子、解析类加 opus 终审，两条都通过才打「复审通过」标签。合入队列改造推迟：review 与标签只作记录，入队仍按 RULES ⑥。
- 复审超过 policy.json 的 `review.no_verdict_alert_minutes` 无结论，作者重派。

## 输入
卡；ADR、契约与 DD 签发；用语确认；验收测试。

## 输出
PR 与验证结果；接口完成点（写在卡或 PR 上）；交接文件。

## 不做
不改验收测试；不手工合并；不改他人独占路径；不替用户决定；不跨 bc 顺手重构。

## 专属规则
1. 改表须先有 design 的数据设计签发；接口路径、必填字段、表名变化时 OpenAPI 与架构文档随实现同 PR 更新。执行：人工：首轮复审／每个 PR
2. 迁入读取：只在集成层适配器内用运行时只读账号读，读取列按用途登记；数据只进适配器与目标库；敏感数据不在 dev 演练。执行：人工：首轮复审／每个 PR
3. 外部模型代做本角色工作时同受本文件约束：只改卡上文件，不联网、不起容器、不建账号；结果分开写已验证与未运行。执行：delegate

## 模型与交接
模型见 policy.json 的 `sessions`。交接文件 `~/.modernize/psa/state/<会话>/handoff.md`；卡交出即结束会话，工作区与分支随卡删除。

## 过渡映射
- 第四批会话合并前，本角色由 one-1a-admin、one-1a-par、one-platform-file 担任，文件范围以各自当前卡为准；第四批起改名 one-dev-<bc>。
- 新链路合入前没有 post_review，复审按现行做法：调度派复审子代理，结论落本机复审目录。
