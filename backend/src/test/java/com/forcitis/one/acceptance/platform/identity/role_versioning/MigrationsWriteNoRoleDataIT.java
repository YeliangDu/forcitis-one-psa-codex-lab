package com.forcitis.one.acceptance.platform.identity.role_versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-0034 decision 1 item 7 (DDL migrations only build tables; roles and role_versions are not written by migrations) and guard rule 8;
 * ADR-0034-IMPL card row 0034-G8-03 and card 7.2 (the roles and role_permissions seed leaves the baseline, the navigation_items seed stays).
 * The behaviour side of the migration scan in {@link MigrationRoleDataScanTest}: after every migration has run and before any application
 * start, there is no role row and no role version.
 */
class MigrationsWriteNoRoleDataIT {

    private MigratedRoleRows db;

    @BeforeEach
    void aMigratedDatabase() throws SQLException {
        db = new MigratedRoleRows(getClass());
    }

    @AfterEach
    void rollBack() throws SQLException {
        db.close();
    }

    @Test
    @DisplayName("0034-G8-03 只跑迁移不启动应用：roles 与 role_versions 均为 0 行（系统角色只由启动对账写入）")
    void migrationsLeaveNoRoleRows() throws SQLException {
        assertThat(db.count("select count(*) from platform_identity.roles")).as("roles rows written by migrations").isZero();
        assertThat(db.count("select count(*) from platform_identity.role_versions")).as("role_versions rows written by migrations").isZero();
    }

    /** Card 7.2: navigation_items 种子 :330-336 保留，不得出现其变化；#934 删除同步记录页面及路由，迁移同时删掉它的菜单项 …206，种子列表因此是 …201 至 …205 五项. */
    @Test
    @DisplayName("0034-G8-03 / 卡 7.2 / #934 navigation_items 种子保留：五个导航项 019a…201 至 …205 仍由迁移写入（同步记录页面的菜单项 …206 随页面一起删除）")
    void navigationSeedStays() throws SQLException {
        assertThat(db.column("select item_id::text from platform_identity.navigation_items where item_id::text like '019a0000-0000-7000-8000-0000000002%' order by item_id"))
                .containsExactly("019a0000-0000-7000-8000-000000000201", "019a0000-0000-7000-8000-000000000202", "019a0000-0000-7000-8000-000000000203",
                        "019a0000-0000-7000-8000-000000000204", "019a0000-0000-7000-8000-000000000205");
    }
}
