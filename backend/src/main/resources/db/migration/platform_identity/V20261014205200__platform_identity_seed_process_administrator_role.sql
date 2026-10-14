-- 流程管理员角色（D-1a-61）：持有人接收没人可派的审批任务、超时升级的任务，可改派任务、查看全部待办；人选由部署前指定（PREDEPLOY_CHECKLIST B-11）。
INSERT INTO platform_identity.roles (code, name, kind, status, access_scope, is_administrative) VALUES
    ('process_administrator', '流程管理员', 'SYSTEM', 'ENABLED', 'SELF', false);
