package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestMssql;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.forcitis.one.fixture.doubles.TestOrgIds;
import com.forcitis.one.platform.integration.api.StaffLookup;
import org.testcontainers.containers.GenericContainer;

/** Real SQL Server (Testcontainers), only under Maven profile legacy-it. */
class SeeyonViewsMssqlIT {

    static final GenericContainer<?> mssql = TestMssql.shared();

    static final String SEEYON_DB = TestMssql.database(SeeyonViewsMssqlIT.class, "seeyontest").name();
    static final String READER_LOGIN = TestMssql.login(SeeyonViewsMssqlIT.class, "forcitis_reader");

    static final Path SCRIPT = Path.of("../docs/modernization/phase1a/SEEYON_VIEWS.sql");
    static final SeeyonMapping MAPPING = new SeeyonMapping(-1726439953579618944L, -8187907605834545337L, "-2682590999914657055", "-3267712510147735998", "00000001");

    static String readerPassword = "Aa1!" + UUID.randomUUID();
    static JdbcClient reader;

    static String url(String database) {
        return TestMssql.url(mssql, database) + ";loginTimeout=30";
    }

    static Connection admin(String database) throws SQLException {
        return TestMssql.adminConnection(mssql, database);
    }

    static void run(String database, String... statements) throws SQLException {
        try (Connection c = admin(database); Statement s = c.createStatement()) {
            for (String sql : statements) {
                s.execute(sql);
            }
        }
    }

    @BeforeAll
    static void createWorld() throws Exception {
        createBaseTables();
        seedBase();
        applyDbaScript();
        widenToTheWholeDatabase();
        reader = JdbcClient.create(new DriverManagerDataSource(url(SEEYON_DB), READER_LOGIN, readerPassword));
    }

    private static void createBaseTables() throws SQLException {
        run(SEEYON_DB,
                "CREATE TABLE dbo.ORG_UNIT (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, PATH nvarchar(200), TYPE nvarchar(16), IS_DELETED bit, IS_INTERNAL bit, IS_ENABLE bit)",
                "CREATE TABLE dbo.ORG_MEMBER (ID bigint PRIMARY KEY, NAME nvarchar(64), CODE nvarchar(32), ORG_DEPARTMENT_ID bigint, ORG_POST_ID bigint, EXT_ATTR_37 nvarchar(64), EXT_ATTR_21 date, we_com_id nvarchar(64), IS_INTERNAL bit, IS_DELETED bit)",
                "CREATE TABLE dbo.ORG_PRINCIPAL (ID bigint IDENTITY PRIMARY KEY, MEMBER_ID bigint, LOGIN_NAME nvarchar(64))",
                "CREATE TABLE dbo.ORG_POST (ID bigint PRIMARY KEY, CODE nvarchar(32), NAME nvarchar(64), SORT_ID int, STATUS int, IS_DELETED bit)",
                "CREATE TABLE dbo.ORG_RELATIONSHIP (ID bigint IDENTITY PRIMARY KEY, SOURCE_ID bigint, OBJECTIVE0_ID bigint, OBJECTIVE1_ID bigint)",
                "CREATE TABLE dbo.ADDRESSBOOK (ID bigint IDENTITY PRIMARY KEY, MEMBER_ID bigint, TYPE nvarchar(16), EXT_ATTR_32 nvarchar(32), EXT_ATTR_33 bigint)",
                "CREATE TABLE dbo.CTP_ENUM (ID bigint PRIMARY KEY, ENUMNAME nvarchar(64))",
                "CREATE TABLE dbo.CTP_ENUM_ITEM (ID bigint PRIMARY KEY, STATE int, REF_ENUMID bigint, SHOWVALUE nvarchar(32))",
                "CREATE TABLE dbo.formmain_0307 (ID bigint IDENTITY PRIMARY KEY, field0217 nvarchar(32), field0234 nvarchar(128), field0284 nvarchar(8), FIELD0244 bigint)",
                "CREATE TABLE dbo.contract_values (ID bigint PRIMARY KEY, amount decimal(18,2))",
                "CREATE PROCEDURE dbo.sp_noop AS SELECT 1");
    }

    private static void seedBase() throws SQLException {
        run(SEEYON_DB,
                "INSERT dbo.ORG_UNIT VALUES (1, 'C1', N'Root', 1, '00000001', 'Account', 0, 1, 1)",
                "INSERT dbo.ORG_UNIT VALUES (2, 'C2', N'Delivery', 2, '000000010001', 'Department', 0, 1, 1)",
                "INSERT dbo.ORG_UNIT VALUES (3, 'C3', N'Stopped', 3, '000000010002', 'Department', 0, 1, 0)",
                "INSERT dbo.ORG_UNIT VALUES (4, 'C4', N'Outside the scope', 4, '00000002', 'Department', 0, 1, 1)",
                "INSERT dbo.CTP_ENUM VALUES (500, N'总部/区域')",
                "INSERT dbo.CTP_ENUM_ITEM VALUES (601, 1, 500, N'总部'), (602, 1, 500, N'区域')",
                "INSERT dbo.ADDRESSBOOK (MEMBER_ID, TYPE, EXT_ATTR_32, EXT_ATTR_33) VALUES (1, 'Department', NULL, 601), (2, 'Department', '-2682590999914657055', 602)",
                "INSERT dbo.ORG_POST VALUES (100, 'P100', N'Consultant', 1, 0, 0), (101, 'P101', N'Old post', 2, 1, 0), (102, 'P102', N'Gone', 3, 0, 1)",
                "INSERT dbo.ORG_MEMBER VALUES (8001, N'Boss', 'E8001', 2, 100, NULL, '1980-01-01', 'wx-8001', 1, 0)",
                "INSERT dbo.ORG_MEMBER VALUES (8002, N'Worker', 'E8002', 2, 100, '8001', '1990-05-05', 'wx-8002', 1, 0)",
                "INSERT dbo.ORG_MEMBER VALUES (8003, N'Leaver', 'E8003', 2, 100, '8001', NULL, 'wx-8003', 1, 0)",
                "INSERT dbo.ORG_MEMBER VALUES (8004, N'Outsider', 'E8004', 4, 100, NULL, NULL, 'wx-8004', 1, 0)",
                "INSERT dbo.ORG_MEMBER VALUES (8005, N'External', 'E8005', 2, 100, NULL, NULL, 'wx-8005', 0, 0)",
                "INSERT dbo.ORG_PRINCIPAL (MEMBER_ID, LOGIN_NAME) VALUES (8001, '13900008001'), (8002, '13900008002'), (8002, '13900008002'), (8004, '13900008004'), (8005, '13900008005')",
                "INSERT dbo.formmain_0307 (field0217, field0234, field0284, FIELD0244) VALUES ('E8001', 'boss@example.test', N'男', -3267712510147735998), "
                        + "('E8002', 'old@example.test', N'女', -8755227263218923811), ('E8002', 'worker@example.test', N'女', -3267712510147735998)",
                "INSERT dbo.ORG_RELATIONSHIP (SOURCE_ID, OBJECTIVE0_ID, OBJECTIVE1_ID) VALUES (8001, 2, -1726439953579618944), (8002, 2, -8187907605834545337), (8003, 2, 424242)",
                "INSERT dbo.contract_values VALUES (1, 1000000.00)");
    }

    private static void applyDbaScript() throws Exception {
        String script = Files.readString(SCRIPT);
        Matcher blocks = Pattern.compile("(?m)^-- ==== BLOCK: (\\S+) ====$").matcher(script);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (blocks.find()) {
            starts.add(blocks.end());
            names.add(blocks.group(1));
        }
        run("master", "CREATE LOGIN " + READER_LOGIN + " WITH PASSWORD = N'" + readerPassword + "', CHECK_POLICY = OFF");
        for (String wanted : List.of("schema", "views", "account")) {
            int i = names.indexOf(wanted);
            assertThat(i).as("block " + wanted).isGreaterThanOrEqualTo(0);
            String body = script.substring(starts.get(i), i + 1 < starts.size() ? script.indexOf("-- ==== BLOCK:", starts.get(i)) : script.length());
            body = body.replace("{{READER_LOGIN}}", READER_LOGIN).replace("{{READER_USER}}", READER_LOGIN);
            for (String batch : body.split("(?m)^GO\\s*$")) {
                String statements = batch.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "").trim();
                if (!statements.isEmpty()) {
                    run(SEEYON_DB, batch);
                }
            }
        }
    }

    /** The shape one-ops applied on 2026-10-15 (ops/dba-scripts/read-all/a8-apply.sql under docs, ADR-0037 item 1.2): whole-database SELECT, database-level DENY of every write. */
    private static void widenToTheWholeDatabase() throws SQLException {
        run(SEEYON_DB, "REVOKE SELECT ON SCHEMA::dbo FROM " + READER_LOGIN, "REVOKE VIEW DEFINITION ON SCHEMA::dbo FROM " + READER_LOGIN,
                "GRANT SELECT ON DATABASE::" + SEEYON_DB + " TO " + READER_LOGIN,
                "DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA "
                        + "ON DATABASE::" + SEEYON_DB + " TO " + READER_LOGIN);
    }

    @Test
    @DisplayName("§9a the views carry exactly the whitelisted columns, and the start-up check passes against the real catalog")
    void startupCheckPassesAgainstSqlServer() {
        SeeyonStartupCheck check = new SeeyonStartupCheck(new JdbcSeeyonSchemaInspector(reader), MAPPING);

        assertThat(check.problems()).isEmpty();
        assertThat(new JdbcSeeyonSchemaInspector(reader).viewColumns()).containsOnlyKeys("V_ORG_UNIT", "V_ORG_RELATIONSHIP", "V_ORG_POST", "V_ORG_MEMBER");
    }

    @Test
    @DisplayName("§9a the views are read through the ownership chain and map to the domain with the shared mapper")
    void viewsAreReadAndMapped() {
        SourceOrgSnapshot snapshot = new SeeyonOrgSource(reader, reader, MAPPING, () -> { }, new SourceIdTranslation(new InMemorySourceReferences())).readSnapshot();

        assertThat(snapshot.units()).extracting(SourceOrgUnit::unitId).containsExactlyInAnyOrder(TestOrgIds.ownOrgUnit(1), TestOrgIds.ownOrgUnit(2), TestOrgIds.ownOrgUnit(3));
        assertThat(snapshot.units().stream().filter(u -> u.unitId().equals(TestOrgIds.ownOrgUnit(3))).findFirst().orElseThrow().state()).isEqualTo(SourceRecordState.DISABLED);
        var delivery = snapshot.units().stream().filter(u -> u.unitId().equals(TestOrgIds.ownOrgUnit(2))).findFirst().orElseThrow();
        assertThat(delivery.regionKind()).isEqualTo(SourceRegionKind.REGIONAL);
        assertThat(delivery.deliveryUnit()).isTrue();
        assertThat(snapshot.units().stream().filter(u -> u.unitId().equals(TestOrgIds.ownOrgUnit(1))).findFirst().orElseThrow().regionKind()).isEqualTo(SourceRegionKind.HEADQUARTERS);

        assertThat(snapshot.relationships()).extracting(r -> r.kind()).containsExactlyInAnyOrder(SourceRelationshipKind.UNIT_HEAD, SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER);
        assertThat(snapshot.positions()).hasSize(3);

        assertThat(snapshot.staff()).extracting(SourcePerson::personId).as("one row per person, in scope only")
                .containsExactlyInAnyOrder(TestOrgIds.ownEmployee(8001), TestOrgIds.ownEmployee(8002), TestOrgIds.ownEmployee(8003));
        SourcePerson worker = snapshot.staff().stream().filter(m -> m.personId().equals(TestOrgIds.ownEmployee(8002))).findFirst().orElseThrow();
        assertThat(worker.supervisorId()).isEqualTo(TestOrgIds.ownEmployee(8001));
        assertThat(worker.loginName()).isEqualTo("13900008002");
        assertThat(worker.email()).isEqualTo("worker@example.test");
        assertThat(worker.feeEarner()).isTrue();
        assertThat(worker.unitId()).isEqualTo(TestOrgIds.ownOrgUnit(2));
        assertThat(snapshot.staff().stream().filter(m -> m.personId().equals(TestOrgIds.ownEmployee(8003))).findFirst().orElseThrow().loginName()).isNull();
    }

    @Test
    @DisplayName("§9a single-person lookup over the real view returns the same person as the bulk read")
    void singleLookup() {
        SeeyonOrgSource source = new SeeyonOrgSource(reader, reader, MAPPING, () -> { }, new SourceIdTranslation(new InMemorySourceReferences()));

        assertThat(source.findStaffByLogin(new StaffLookup.ByWecomUser(new WecomUserId("wx-8002"))))
                .containsExactly(source.readSnapshot().staff().stream().filter(m -> m.personId().equals(TestOrgIds.ownEmployee(8002))).findFirst().orElseThrow());
        assertThat(source.findStaffByLogin(new StaffLookup.ByWecomUser(new WecomUserId("wx-8004")))).as("outside the root path").isEmpty();
    }

    @Test
    @DisplayName("§9a ADR-0037 item 1: the login reads base tables as well as the views; writes stay refused (writesAreDenied)")
    void baseTablesAreReadable() {
        JdbcClient jdbc = reader;

        for (String table : List.of("dbo.ORG_MEMBER", "dbo.ORG_UNIT", "dbo.formmain_0307", "dbo.contract_values", "dbo.ORG_PRINCIPAL")) {
            assertThat(jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single()).as(table).isPositive();
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM forcitis_one.v_org_member").query(Integer.class).single()).isPositive();
    }

    @Test
    @DisplayName("§9a ReadOnly: every write, execute and DDL attempt is refused - on base tables and on the views")
    void writesAreDenied() {
        JdbcClient jdbc = reader;
        List<String> attempts = List.of(
                "INSERT dbo.ORG_MEMBER (ID, NAME, CODE) VALUES (9999, 'x', 'x')",
                "UPDATE dbo.ORG_MEMBER SET NAME = 'x'",
                "DELETE dbo.ORG_MEMBER",
                "UPDATE forcitis_one.v_org_post SET NAME = 'x'",
                "DELETE forcitis_one.v_org_post",
                "EXEC dbo.sp_noop",
                "CREATE TABLE forcitis_one.sneaky (id int)",
                "CREATE VIEW forcitis_one.sneaky_view AS SELECT 1 AS one",
                "DROP VIEW forcitis_one.v_org_post",
                "TRUNCATE TABLE dbo.ORG_UNIT");

        for (String sql : attempts) {
            assertThatThrownBy(() -> jdbc.sql(sql).update()).as(sql).satisfies(e -> {
                assertThat(e.getMessage()).containsAnyOf("denied", "permission");
                assertThat(e.getMessage()).doesNotContain("Login failed");
            });
        }
    }

    @Test
    @DisplayName("§9a an extra column in a view (say the birthday) makes the start-up check refuse; a dropped one too")
    void extraColumnIsRefused() throws SQLException {
        run(SEEYON_DB, "ALTER VIEW forcitis_one.v_org_post AS SELECT P.ID, P.CODE, P.NAME, P.SORT_ID, P.STATUS, P.IS_DELETED, CAST(NULL AS date) AS BIRTHDAY FROM dbo.ORG_POST P");
        try {
            assertThat(new SeeyonStartupCheck(new JdbcSeeyonSchemaInspector(reader), MAPPING).problems())
                    .anyMatch(p -> p.contains("V_ORG_POST") && p.contains("outside the whitelist") && p.contains("BIRTHDAY"));
        } finally {
            run(SEEYON_DB, "ALTER VIEW forcitis_one.v_org_post AS SELECT P.ID, P.CODE, P.NAME, P.SORT_ID, P.STATUS, P.IS_DELETED FROM dbo.ORG_POST P");
        }
        assertThat(new SeeyonStartupCheck(new JdbcSeeyonSchemaInspector(reader), MAPPING).problems()).isEmpty();
    }

    @Test
    @DisplayName("RULE-356 a Seeyon instance whose ids differ from the configuration is refused at start-up, not silently turned into 'no head'")
    void wrongIdsAreRefused() {
        SeeyonMapping wrong = new SeeyonMapping(1, 2, "nope", "nope", "00000001");

        assertThat(new SeeyonStartupCheck(new JdbcSeeyonSchemaInspector(reader), wrong).problems()).hasSize(4);
    }
}
