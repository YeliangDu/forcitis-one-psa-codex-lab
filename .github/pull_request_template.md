## 改动说明

## 改动的断言 / 测试及理由

<!-- 放宽、删除或改写已有断言 / 测试的，逐条写明理由；没有写「无」（SESSION_PLAYBOOK 附 M5「第二批」第 2 条） -->

## 新增术语

<!-- 新标识符、界面文字、表列名中登记的提议术语（terms.yaml 词条 id）；没有写「无」 -->

## 是否同步了 OpenAPI 与架构文档

<!-- 接口路径、必填字段、表名有变化时：是 / 否（说明）；无此类变化写「不涉及」 -->

## 验收测试

<!-- 改动了验收测试目录（backend/src/test/java/com/forcitis/one/acceptance/**、backend/src/test/resources/acceptance/**、frontend/**/__acceptance__/**）的提交，说明须含一行 `Acceptance-Test-Author: test-engineer` 或 `Acceptance-Ruling: <裁定编号>`（编号形如 `RULING-12`，大写字母、数字与连字符，以 -数字 结尾）；带 Acceptance-Test-Author 的提交只能改验收测试目录；本 PR 不涉及写「不涉及」 -->
- [ ] 改动验收测试的提交都带上述其中一行（SESSION_PLAYBOOK 附 M1 第 4 条、TEAM-69）

## Codex 实现（模型、强度）

<!-- 由 Codex 实现的写「Codex 实现（<模型>，<强度>）」与返工轮次（SESSION_PLAYBOOK TEAM-32–38）；不是写「不涉及」 -->

## 绕过推送前钩子

<!-- 推送时用过 `PUSH_BYPASS_REASON="…" git push --no-verify` 的，写理由（同 `.claude/push-bypass.log` 的记录）；没有写「无」 -->

## precheck.sh 输出

<!-- 粘贴 `scripts/precheck.sh [基线]` 的完整输出（CODING_STANDARD CS-7.1）；FAIL 须修复，SKIP 须有理由（如未安装工具、需起容器未获同意）。不得包含密钥 -->
```
```
