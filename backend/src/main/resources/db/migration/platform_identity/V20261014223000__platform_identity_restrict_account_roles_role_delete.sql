-- 账号仍持有的角色不得被删除：删角色时由外键拒绝，而不是悄悄把账号的授权带走。
ALTER TABLE platform_identity.account_roles DROP CONSTRAINT account_roles_role_id_fkey;
ALTER TABLE platform_identity.account_roles
    ADD CONSTRAINT fk_account_roles_role_id FOREIGN KEY (role_id) REFERENCES platform_identity.roles (role_id) ON DELETE RESTRICT;
