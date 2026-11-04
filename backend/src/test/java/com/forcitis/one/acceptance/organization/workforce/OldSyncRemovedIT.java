package com.forcitis.one.acceptance.organization.workforce;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.TestDatabase;
import com.forcitis.one.TestPostgres;
import com.forcitis.one.acceptance.AcceptanceSupport;
import com.forcitis.one.platform.identity.api.Permission;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * #934 (ADR-0068 section 2.5 D2; ADR-0055 section 6 and 「影响面 · 连接与登记」): the old organisation sync is gone, not only unscheduled.
 * With the settings production runs with (jobs on, and the old sync and sync-purge switches still set, which must change nothing), the context schedules no
 * job of the sync while the personnel command due-date job is scheduled; the sync use cases, its job and its events are no classes any
 * more; the sync run tables are dropped and no column refers to a sync run; no endpoint lives under sync-runs; the sync run permission codes
 * are no longer granted (ADR-0034 decision 1 item 3: a code is kept and marked deprecated, never deleted) and have no constant left for
 * annotations; the event catalogue names no sync event. Successor of #961's SnapshotSyncNotScheduledIT: the sync, not just its schedule,
 * is gone.
 */
@SpringBootTest(properties = {"flowable.async-executor-activate=false", "spring.datasource.hikari.connection-timeout=2000",
        "forcitis.platform.scheduling.jobs-enabled=true", "forcitis.organization.workforce.sync.enabled=true",
        "forcitis.organization.workforce.sync-purge.enabled=true"})
class OldSyncRemovedIT extends AcceptanceSupport {

    static final String WORKFORCE = "com.forcitis.one.organization.workforce";

    /** The sync use cases, its job, the snapshot-change event and its identity listener, and the sync run events (#934 card, lead 2026-11-02). */
    static final List<String> GONE_CLASSES = List.of(WORKFORCE + ".application.SyncOrgSnapshot", WORKFORCE + ".application.ReleaseSyncRun",
            WORKFORCE + ".infrastructure.scheduling.OrgSnapshotSyncJob", WORKFORCE + ".events.SyncRunSucceeded", WORKFORCE + ".events.SyncRunHeld",
            WORKFORCE + ".events.SyncRunFailed", WORKFORCE + ".events.SyncRunReleased", WORKFORCE + ".events.SyncRunHoldReleased",
            WORKFORCE + ".events.OrgSnapshotChanged", "com.forcitis.one.platform.identity.application.HandleOrgSnapshotChange");

    static final Path EVENT_CATALOGUE = Path.of("../docs/architecture/events/asyncapi.yaml");

    static final TestDatabase postgres = TestPostgres.database(OldSyncRemovedIT.class, TestPostgres.MIGRATED).withUrlParam("currentSchema", "platform_approval");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        postgres.register(registry);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DataSource dataSource;

    /** The scheduled methods of the context, as "declaring.Class.method". */
    private List<String> scheduledMethods() {
        List<String> methods = new ArrayList<>();
        for (ScheduledTaskHolder holder : context.getBeansOfType(ScheduledTaskHolder.class).values()) {
            for (ScheduledTask task : holder.getScheduledTasks()) {
                methods.add(task.getTask().toString());
            }
        }
        return methods;
    }

    /** A workforce job whose class is named after the sync (the snapshot sync, the purge of its runs, or any other). */
    private static boolean aSyncJob(String method) {
        int dot = method.lastIndexOf('.');
        String className = dot < 0 ? method : method.substring(0, dot);
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return className.startsWith(WORKFORCE) && simpleName.contains("Sync");
    }

    private List<String> rows(String sql) throws SQLException {
        List<String> found = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement select = connection.prepareStatement(sql); ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                found.add(rs.getString(1));
            }
        }
        return found;
    }

    @Test
    @DisplayName("#934 删除旧同步：按生产设置（作业开、旧同步与同步清理开关仍为 true）启动，人事命令到期作业在排，没有任何 workforce 中以同步命名的定时任务")
    void noJobOfTheSyncIsScheduled() {
        List<String> methods = scheduledMethods();

        assertThat(methods).as("premise: scheduling is on in this context and the personnel command due-date job is scheduled")
                .anyMatch(method -> method.contains("PersonnelCommandDueJob"));
        assertThat(methods.stream().filter(OldSyncRemovedIT::aSyncJob).toList()).as("scheduled tasks of a workforce class named after the sync").isEmpty();
    }

    @Test
    @DisplayName("#934 删除旧同步：同步用例、放行用例、同步作业、OrgSnapshotChanged 及其身份监听、五个同步运行事件都已不是类")
    void theSyncClassesAreGone() {
        List<String> present = new ArrayList<>();
        for (String name : GONE_CLASSES) {
            try {
                Class.forName(name);
                present.add(name);
            } catch (ClassNotFoundException gone) {
                // what the card asks
            }
        }
        assertThat(present).as("classes of the old sync that still exist").isEmpty();
    }

    @Test
    @DisplayName("#934 删除旧同步（ADR-0068 第 2.5 条 D2）：同步运行表与旧的待放行离职表已删除，库中没有任何表或列以 sync_run 命名")
    void theSyncTablesAndColumnsAreGone() throws SQLException {
        assertThat(rows("select table_schema || '.' || table_name from information_schema.tables where table_schema = 'organization_workforce'"))
                .as("premise: the catalogue is read").contains("organization_workforce.employees");
        assertThat(rows("select to_regclass('organization_workforce.sync_runs')::text")).as("organization_workforce.sync_runs").containsOnlyNulls();
        assertThat(rows("select to_regclass('organization_workforce.sync_run_held_separations')::text")).as("organization_workforce.sync_run_held_separations")
                .containsOnlyNulls();
        assertThat(rows("""
                select table_schema || '.' || table_name from information_schema.tables
                 where table_name like '%sync\\_run%' and table_schema not in ('pg_catalog', 'information_schema')"""))
                .as("tables named after a sync run").isEmpty();
        assertThat(rows("""
                select table_schema || '.' || table_name || '.' || column_name from information_schema.columns
                 where column_name like '%sync\\_run%' and table_schema not in ('pg_catalog', 'information_schema')"""))
                .as("columns that refer to a sync run").isEmpty();
    }

    @Test
    @DisplayName("#934 删除旧同步：没有任何接口路径含 sync-run")
    void noEndpointUnderSyncRuns() {
        RequestMappingHandlerMapping mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<String> paths = new ArrayList<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            paths.addAll(info.getPatternValues());
        }

        assertThat(paths).as("premise: the endpoints are read").anyMatch(path -> path.startsWith("/api/v1/organization/"));
        assertThat(paths.stream().filter(path -> path.contains("sync-run")).sorted().toList()).as("endpoints under sync-runs").isEmpty();
    }

    @Test
    @DisplayName("#934 删除旧同步（ADR-0055 第 6 条撤同步记录权限，ADR-0034 决定 1 第 3 项只标停用不删）：含 sync-run 的权限码已停用、不再可授，Permission.Codes 中不再有含 sync-run 的常量")
    void theSyncRunPermissionsAreRetired() throws IllegalAccessException {
        List<String> live = new ArrayList<>();
        for (Permission permission : Permission.values()) {
            if (permission.code().contains("sync-run") && !permission.deprecated()) {
                live.add(permission.code());
            }
        }
        List<String> constants = new ArrayList<>();
        for (Field field : Permission.Codes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class && String.valueOf(field.get(null)).contains("sync-run")) {
                constants.add(field.getName() + " = " + field.get(null));
            }
        }

        assertThat(live).as("sync run permission codes still live (grantable)").isEmpty();
        assertThat(constants).as("Permission.Codes constants of sync run codes").isEmpty();
    }

    @Test
    @DisplayName("#934 删除旧同步：事件目录 asyncapi.yaml 不再登记同步运行事件与 OrgSnapshotChanged")
    void theEventCatalogueNamesNoSyncEvent() throws IOException {
        String catalogue = Files.readString(EVENT_CATALOGUE);
        assertThat(catalogue).as("premise: the catalogue is read").contains("EmployeeHired");

        List<String> named = new ArrayList<>();
        Matcher match = Pattern.compile("SyncRun\\w*|OrgSnapshotChanged").matcher(catalogue);
        while (match.find()) {
            if (!named.contains(match.group())) {
                named.add(match.group());
            }
        }
        assertThat(named).as("sync events in %s", EVENT_CATALOGUE).isEmpty();
    }
}
