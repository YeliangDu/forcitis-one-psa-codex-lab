-- #1326: 离职作为账号状态。只加列，不回填（lead 裁定 2026-11-05）：older leavers 以组织目录的在职判断为准。
ALTER TABLE platform_identity.user_accounts ADD COLUMN separated_at timestamptz;

COMMENT ON COLUMN platform_identity.user_accounts.separated_at IS '该账号的人员离职的记录时刻，为空表示未记录离职；离职事件的处理者写入（每个账号，有无角色都写；重复投递保留第一次的时刻），复职事件的处理者清空；只是提示，不是依据：本次改动之前离职的人没有这个值，在职与否一律以组织目录为准，任何代码都不得单凭它放行';
