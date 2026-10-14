排序: 060
工作单元: Phase 1a 平台桥接（试点）
状态: 修复批完成（2026-10-15）：PR #72 合并（main 3485e880），opus 整合复审通过（phase1a/review/REVIEW_integration.md）；剩余工作转 061（one-1a-next）
负责会话: psa-1a-platform-bridge-reimagine（已交接给 one-1a-next）
入口 / 阻塞: 入口条件见 brief：老系统安全止血按用户决定不再阻塞（I-1..I-9 保障）；骨架与 CI 已就绪（PR #9）；RDS 已开通（PG 18 高可用 pgm-bp13vak0sew3m003，现作测试开发用）；FORCITIS one 专用 ECS 已开通（i-bp122xlpar8w2uylmal2，杭州 K 区，g8i.xlarge 4C16G，内网 172.16.89.86，包年自动续费，安全组 sg-bp1hjbq8dkoaxa2nljzq 仅 80/443，RDS 白名单分组 forcitis_one）；待办：首次部署前完成 blockers/090（整套数据范围，NEXT_BATCH F-1）；FORCITIS one 用数据库与角色、部署（运维）
下一条命令: 见 061（one-1a-next 按 phase1a/NEXT_BATCH_DRAFT.md 与 NEXT_BATCH_PLAN.md 开工）
