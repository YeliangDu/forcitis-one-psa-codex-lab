package com.forcitis.one.acceptance.platform.integration;

import com.forcitis.one.TestMssql;
import com.forcitis.one.acceptance.AccountsAcceptanceSupport;
import com.forcitis.one.kernel.WorkContext;
import com.forcitis.one.organization.workforce.application.SyncOrgSnapshot;
import com.forcitis.one.organization.workforce.application.SyncResult;
import com.forcitis.one.organization.workforce.domain.SyncTrigger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * The organisation source (A8 views) and the old system (to-do and short-name views) as real SQL Server databases behind the real
 * adapters, for the R-1b P3b cases on source ids (ADR-0033 section 2). The SQL Server container is started once per test JVM and shared by
 * the classes that extend this one; each class starts from a world that holds only the base rows the start-up check of the views needs
 * (a root unit, a person, one relationship of each configured type, the configured markers), and adds its own rows. Each class has its own
 * PostgreSQL. The three read-only logins are built like the RDS ones ({@link ReadOnlyBaselineFixture}) and checked against them.
 * <p>
 * {@link #syncOnce()} is the one place that triggers a sync (card section 4 item 2): it calls the sync use case of workforce today, and is the
 * only line to change when split a moves the sync into platform.integration.
 */
@TestPropertySource(properties = {
        "forcitis.platform.approval.deadline.enabled=false",
        "forcitis.platform.integration.seeyon.views.enabled=true",
        "forcitis.platform.integration.seeyon.views.head-relationship-type-id=" + OrgSourceWorldSupport.HEAD_TYPE,
        "forcitis.platform.integration.seeyon.views.regional-receivables-manager-relationship-type-id=" + OrgSourceWorldSupport.MANAGER_TYPE,
        "forcitis.platform.integration.seeyon.views.delivery-unit-yes-enum-id=" + OrgSourceWorldSupport.DELIVERY_YES,
        "forcitis.platform.integration.seeyon.views.fee-earner-yes-enum-id=" + OrgSourceWorldSupport.FEE_EARNER_YES,
        "forcitis.platform.integration.seeyon.views.root-path=" + OrgSourceWorldSupport.ROOT_PATH,
        "forcitis.platform.integration.legacy.enabled=true"})
abstract class OrgSourceWorldSupport extends AccountsAcceptanceSupport {

    static final long HEAD_TYPE = 7001;
    static final long MANAGER_TYPE = 7002;
    static final String DELIVERY_YES = "9001";
    static final String FEE_EARNER_YES = "9002";
    static final String ROOT_PATH = "00000001";
    static final String SHORT_NAME_VIEW = "dbo.v_one_legacy_org_short_name";

    static final long BASE_UNIT = 1;
    static final long BASE_PERSON = 8001;
    static final long BASE_POST = 100;

    static final String ORG_SOURCE = "ORG_SOURCE";
    static final String LEGACY_SYSTEM = "LEGACY_SYSTEM";

    @DynamicPropertySource
    static void externalWorld(DynamicPropertyRegistry registry) {
        registry.add("forcitis.platform.integration.seeyon.views.jdbc-url", () -> World.url(World.A8_WORLD));
        registry.add("forcitis.platform.integration.seeyon.views.username", () -> World.A8_READER);
        registry.add("forcitis.platform.integration.seeyon.views.password", () -> World.A8_READER_PASSWORD);
        registry.add("forcitis.platform.integration.legacy.jdbc-url", () -> World.url(World.LEGACY_WORLD));
        registry.add("forcitis.platform.integration.legacy.username", () -> World.NAMES_READER);
        registry.add("forcitis.platform.integration.legacy.password", () -> World.NAMES_READER_PASSWORD);
    }

    @BeforeAll
    static void onlyTheBaseRows() throws SQLException {
        World.run(World.A8_WORLD, "DELETE dbo.ORG_UNIT WHERE ID <> " + BASE_UNIT, "DELETE dbo.post_rows WHERE ID <> " + BASE_POST,
                "DELETE dbo.member_rows WHERE ID <> " + BASE_PERSON,
                "DELETE dbo.relationship_rows WHERE NOT (SOURCE_MEMBER_ID = " + BASE_PERSON + " AND ORG_UNIT_ID = " + BASE_UNIT + ")");
        World.run(World.LEGACY_WORLD, "DELETE dbo.short_name_rows");
    }

    @Autowired
    SyncOrgSnapshot sync;

    @Autowired
    ApplicationContext context;

    /** Triggers one sync the way the scheduled job does, through the real adapter; a failed run stops the test with its reason. */
    protected SyncResult syncOnce() {
        SyncResult result;
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            result = sync.execute(SyncTrigger.MANUAL, false);
        }
        if (!"SUCCEEDED".equals(result.status().name())) {
            throw new AssertionError("the sync ended " + result.status() + ": " + result.note());
        }
        return result;
    }

    // ---- the organisation source ----

    static void unit(long id, String code, String name, String path) throws SQLException {
        World.run(World.A8_WORLD, "INSERT dbo.ORG_UNIT VALUES (" + id + ", '" + code + "', N'" + name + "', 1, '" + path + "', 0, 1, 1, NULL, NULL, N'Department')");
    }

    static void post(long id, String code, String name) throws SQLException {
        World.run(World.A8_WORLD, "INSERT dbo.post_rows VALUES (" + id + ", '" + code + "', N'" + name + "', 1, 0, 0)");
    }

    /** A person in the subtree of the root, with the fee-earner marker. */
    static void member(long id, String name, String code, long unitId, String unitPath, Long postId, Long supervisorId, String wecomUserId,
            String loginName) throws SQLException {
        try (Connection c = World.admin(World.A8_WORLD); PreparedStatement insert = c.prepareStatement(
                "INSERT dbo.member_rows VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, N'女', ?)")) {
            insert.setLong(1, id);
            insert.setString(2, name);
            insert.setString(3, code);
            insert.setLong(4, unitId);
            insert.setString(5, unitPath);
            insert.setObject(6, postId);
            insert.setObject(7, supervisorId);
            insert.setString(8, wecomUserId);
            insert.setString(9, loginName);
            insert.setString(10, "person" + Math.abs(id % 100_000) + "@example.test");
            insert.setString(11, FEE_EARNER_YES);
            insert.executeUpdate();
        }
    }

    static void renameMember(long id, String name, String code, String loginName) throws SQLException {
        try (Connection c = World.admin(World.A8_WORLD); PreparedStatement update = c.prepareStatement(
                "UPDATE dbo.member_rows SET NAME = ?, CODE = ?, LOGIN_NAME = ? WHERE ID = ?")) {
            update.setString(1, name);
            update.setString(2, code);
            update.setString(3, loginName);
            update.setLong(4, id);
            update.executeUpdate();
        }
    }

    static void relationship(long person, long unit, long type) throws SQLException {
        World.run(World.A8_WORLD, "INSERT dbo.relationship_rows VALUES (" + person + ", " + unit + ", " + type + ", 0)");
    }

    // ---- the old system ----

    static void legacyShortName(long legacyUnit, String shortName) throws SQLException {
        try (Connection c = World.admin(World.LEGACY_WORLD); PreparedStatement insert = c.prepareStatement("INSERT dbo.short_name_rows VALUES (?, ?)")) {
            insert.setLong(1, legacyUnit);
            insert.setString(2, shortName);
            insert.executeUpdate();
        }
    }

    // ---- source references and the snapshot, read black-box ----

    /** Allocates an own id for a source object through the integration service, as a one-off import (P6) would make the reference. */
    protected UUID allocate(String sourceSystem, String kind, long sourceId) {
        try (WorkContext.Scope job = WorkContext.enter(UUID.randomUUID(), null, "organization.workforce.snapshot.sync")) {
            return SourceReferencesProbe.of(context).allocate(sourceSystem, kind, sourceId);
        }
    }

    /** The own ids of the confirmed references of one source object; one at most when the sync matches by source id. */
    protected List<UUID> ownIdsOf(String kind, long sourceId) throws SQLException {
        List<UUID> found = new ArrayList<>();
        try (Connection integration = schemaConnection("platform_integration"); PreparedStatement select = integration.prepareStatement("""
                select own_id from source_references
                 where source_system = ? and source_object_kind = ? and source_id = ? and confirmation_status = 'CONFIRMED'""")) {
            select.setString(1, ORG_SOURCE);
            select.setString(2, kind);
            select.setLong(3, sourceId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    found.add(rs.getObject(1, UUID.class));
                }
            }
        }
        return found;
    }

    /** The one own id of a source object, failing when the sync made none or several. */
    protected UUID ownIdOf(String kind, long sourceId) throws SQLException {
        List<UUID> found = ownIdsOf(kind, sourceId);
        if (found.size() != 1) {
            throw new AssertionError("ADR-0033 section 2 item 2: one confirmed " + kind + " reference of the organisation source for " + sourceId
                    + " after the sync, found " + found);
        }
        return found.getFirst();
    }

    protected int references(String kind) throws SQLException {
        try (Connection integration = schemaConnection("platform_integration"); PreparedStatement count = integration.prepareStatement(
                "select count(*) from source_references where source_system = ? and source_object_kind = ?")) {
            count.setString(1, ORG_SOURCE);
            count.setString(2, kind);
            try (ResultSet rs = count.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Counts the insert statements on the source references from here on, made or not (an allocation of a known object inserts nothing but
     * still runs its statement): a statement trigger of the test database, the evidence that known objects are not allocated again.
     */
    protected void countAllocations() throws SQLException {
        try (Connection integration = schemaConnection("platform_integration"); Statement statement = integration.createStatement()) {
            statement.execute("create schema if not exists acceptance_probe");
            statement.execute("create table if not exists acceptance_probe.allocation_statements (n bigint not null)");
            statement.execute("truncate acceptance_probe.allocation_statements");
            statement.execute("""
                    create or replace function acceptance_probe.count_allocation() returns trigger language plpgsql as $$
                    begin insert into acceptance_probe.allocation_statements values (1); return null; end $$""");
            statement.execute("drop trigger if exists acceptance_count_allocation on source_references");
            statement.execute("create trigger acceptance_count_allocation after insert on source_references for each statement "
                    + "execute function acceptance_probe.count_allocation()");
        }
    }

    protected int allocationStatements() throws SQLException {
        try (Connection integration = schemaConnection("platform_integration"); Statement statement = integration.createStatement();
             ResultSet rs = statement.executeQuery("select count(*) from acceptance_probe.allocation_statements")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** One column of the snapshot row whose id, as text, is the own id; null when there is no such row. */
    protected String snapshotValue(String table, UUID id, String column) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select " + column + "::text from " + table + " where " + (table.equals("assignments") ? "employee_id" : "id") + "::text = ?")) {
            select.setString(1, id.toString());
            try (ResultSet rs = select.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    protected boolean inSnapshot(String table, UUID id) throws SQLException {
        try (Connection workforce = schemaConnection("organization_workforce"); PreparedStatement select = workforce.prepareStatement(
                "select count(*) from " + table + " where id::text = ?")) {
            select.setString(1, id.toString());
            try (ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    /** The SQL Server of the external systems, started once per JVM. */
    static final class World {
        static final String SA_PASSWORD = TestMssql.SA_PASSWORD;
        static final String A8_READER_PASSWORD = "Bb2!" + UUID.randomUUID();
        static final String NAMES_READER_PASSWORD = "Dd4!" + UUID.randomUUID();

        static final GenericContainer<?> MSSQL = TestMssql.shared();
        static final String A8_WORLD = TestMssql.database(OrgSourceWorldSupport.class, "a8world").name();
        static final String LEGACY_WORLD = TestMssql.database(OrgSourceWorldSupport.class, "legacyworld").name();
        static final String A8_READER = TestMssql.login(OrgSourceWorldSupport.class, "a8_reader");
        static final String NAMES_READER = TestMssql.login(OrgSourceWorldSupport.class, "names_reader");

        static {
            try {
                ReadOnlyBaselineFixture.createRdsPlatform(World::run);
                createA8World();
                createLegacyWorld();
                ReadOnlyBaselineFixture.createViewSchemaReader(World::run, World.A8_WORLD, World.A8_READER, A8_READER_PASSWORD);
                ReadOnlyBaselineFixture.createSingleViewReader(World::run, World.LEGACY_WORLD, SHORT_NAME_VIEW, World.NAMES_READER, NAMES_READER_PASSWORD);
                ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(url(World.A8_WORLD), World.A8_WORLD, World.A8_READER, A8_READER_PASSWORD);
                ReadOnlyBaselineFixture.assertMatchesTheRdsBaseline(url(World.LEGACY_WORLD), World.LEGACY_WORLD, World.NAMES_READER, NAMES_READER_PASSWORD);
            } catch (SQLException e) {
                throw new IllegalStateException("cannot prepare the external databases; SQL Server log:\n" + MSSQL.getLogs(), e);
            }
        }

        private World() {
        }

        static String url(String database) {
            return TestMssql.url(MSSQL, database);
        }

        static Connection admin(String database) throws SQLException {
            return TestMssql.adminConnection(MSSQL, database);
        }

        static void run(String database, String... statements) throws SQLException {
            try (Connection c = admin(database); Statement s = c.createStatement()) {
                for (String sql : statements) {
                    s.execute(sql);
                }
            }
        }

        private static void createA8World() throws SQLException {
            run(World.A8_WORLD, "CREATE SCHEMA " + ReadOnlyBaselineFixture.VIEW_SCHEMA + " AUTHORIZATION dbo",
                    "CREATE TABLE dbo.ORG_UNIT (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, PATH nvarchar(200), "
                            + "IS_DELETED bit, IS_INTERNAL bit, IS_ENABLE bit, CATEGORY nvarchar(16), DELIVERY_FLAG_ENUM_ID nvarchar(32), TYPE nvarchar(32) NULL)",
                    "CREATE TABLE dbo.relationship_rows (SOURCE_MEMBER_ID bigint, ORG_UNIT_ID bigint, RELATIONSHIP_TYPE_ID bigint, MEMBER_IS_DELETED bit)",
                    "CREATE TABLE dbo.post_rows (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, STATUS int, IS_DELETED bit)",
                    "CREATE TABLE dbo.member_rows (ID bigint PRIMARY KEY, NAME nvarchar(64), CODE nvarchar(32), ORG_UNIT_ID bigint, ORG_UNIT_PATH nvarchar(200), "
                            + "POST_ID bigint, SUPERVISOR_MEMBER_ID bigint, WE_COM_ID nvarchar(64), IS_INTERNAL bit, LOGIN_NAME nvarchar(64), "
                            + "ENTERPRISE_EMAIL nvarchar(128), GENDER nvarchar(8), FEE_EARNER_ENUM_ID nvarchar(32))",
                    "INSERT dbo.ORG_UNIT VALUES (" + BASE_UNIT + ", 'C1', N'Root', 1, '" + ROOT_PATH + "', 0, 1, 1, N'总部', '" + DELIVERY_YES + "', N'Account')",
                    "INSERT dbo.post_rows VALUES (" + BASE_POST + ", 'P100', N'Consultant', 1, 0, 0)",
                    "INSERT dbo.member_rows VALUES (" + BASE_PERSON + ", N'Base person', 'E8001', " + BASE_UNIT + ", '" + ROOT_PATH + "', " + BASE_POST
                            + ", NULL, 'wx-world-8001', 1, 'world-8001', 'base@example.test', N'女', '" + FEE_EARNER_YES + "')",
                    "INSERT dbo.relationship_rows VALUES (" + BASE_PERSON + ", " + BASE_UNIT + ", " + HEAD_TYPE + ", 0), (" + BASE_PERSON + ", " + BASE_UNIT
                            + ", " + MANAGER_TYPE + ", 0)",
                    "CREATE VIEW forcitis_one.v_org_unit AS SELECT ID, CODE, NAME, SORT_ID, PATH, IS_DELETED, IS_INTERNAL, IS_ENABLE, CATEGORY, "
                            + "DELIVERY_FLAG_ENUM_ID FROM dbo.ORG_UNIT",
                    "CREATE VIEW forcitis_one.v_org_relationship AS SELECT SOURCE_MEMBER_ID, ORG_UNIT_ID, RELATIONSHIP_TYPE_ID, MEMBER_IS_DELETED FROM dbo.relationship_rows",
                    "CREATE VIEW forcitis_one.v_org_post AS SELECT ID, CODE, NAME, SORT_ID, STATUS, IS_DELETED FROM dbo.post_rows",
                    "CREATE VIEW forcitis_one.v_org_member AS SELECT ID, NAME, CODE, ORG_UNIT_ID, ORG_UNIT_PATH, POST_ID, SUPERVISOR_MEMBER_ID, WE_COM_ID, "
                            + "IS_INTERNAL, LOGIN_NAME, ENTERPRISE_EMAIL, GENDER, FEE_EARNER_ENUM_ID FROM dbo.member_rows");
        }

        private static void createLegacyWorld() throws SQLException {
            run(World.LEGACY_WORLD,
                    "CREATE TABLE dbo.short_name_rows (id bigint, short_name nvarchar(64))",
                    "CREATE VIEW " + SHORT_NAME_VIEW + " AS SELECT id, short_name FROM dbo.short_name_rows");
        }
    }
}
