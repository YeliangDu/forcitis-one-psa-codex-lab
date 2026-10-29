## 原话

arch 的三个会话：people 又停了，这几个会话总是停在那里不汇报，检查下为什么

## 落点

查明原因：app 在用户久未发言时暂停会话外发消息，会话停下等用户解锁。定 SESSION_PLAYBOOK §11 消息约定第 7 条：限流时写 ~/.modernize/psa/outbox/<会话名>.md 并继续干活，调度巡检代转；one-arch-people 积压四件已代转 one-architecture
