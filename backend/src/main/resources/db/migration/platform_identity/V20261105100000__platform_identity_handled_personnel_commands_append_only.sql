-- 已处理的人事命令只追加：数据库自己拒绝改写与删除，不依赖应用代码不写（数据设计清单 D8 / 记分卡 D1；#1329）。
-- 只加触发器，不改表结构、不动已有行；应用只对它 INSERT 与 SELECT（JdbcAccountRepository.saveHandled / isHandled）。
CREATE FUNCTION platform_identity.refuse_append_only_rewrite() RETURNS trigger LANGUAGE plpgsql SET search_path = pg_catalog, pg_temp AS $$
BEGIN
    RAISE EXCEPTION '%.% is append-only: % is not allowed', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP USING ERRCODE = 'restrict_violation';
END $$;
COMMENT ON FUNCTION platform_identity.refuse_append_only_rewrite() IS '只追加的表拒绝 UPDATE、DELETE 与 TRUNCATE（无关闭列的追加表用；授权历史表用 refuse_history_rewrite）';

CREATE TRIGGER tg_handled_personnel_commands_append_only BEFORE UPDATE OR DELETE ON platform_identity.handled_personnel_commands
    FOR EACH ROW EXECUTE FUNCTION platform_identity.refuse_append_only_rewrite();
CREATE TRIGGER tg_handled_personnel_commands_append_only_truncate BEFORE TRUNCATE ON platform_identity.handled_personnel_commands
    FOR EACH STATEMENT EXECUTE FUNCTION platform_identity.refuse_append_only_rewrite();
