## 原话

QUA-04 拆开先合：第一版（纯文档推送 106s→6s）推上去后单独 PR，one-quality 复核加 CI 全绿即进合并队列，不等第二批、不等 20:00；第二批（precheck_before_pr 合并、heavy_slot 重测、删 CI 重复 build_all）另开 PR；顺带收窄 precheck.sh 第 376 行，pre-push 时 docs/ 不再整目录触发 Java 单测，只认测试真正读的路径，intent/ 这类不读的跳过；新预检合入前纯文档改动攒着一次推

## 落点

GOV-1023-QUA-04：#668 为第一版，第二批另开 PR
