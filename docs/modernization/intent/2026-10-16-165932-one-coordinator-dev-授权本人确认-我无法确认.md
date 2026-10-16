## 原话

（dev 授权本人确认）我无法确认 ID，手机号就是我；（见证人）先实现单人模式

## 落点

dev 授权：以 A8 登录名（手机号）与用户所述一致为本人确认依据（会话与仓库只记末 4 位）；不指定见证人，先由 one-1a-par 实现 ADR-0030 单人运维模式（FORCITIS_PLATFORM_IDENTITY_SINGLE_OPERATOR_MODE，可省略 --witness 并标注），出新 rc 后再 grant-role；期间 dev 保留临时开关 PROCESS_ADMINISTRATOR_REQUIRED=false
