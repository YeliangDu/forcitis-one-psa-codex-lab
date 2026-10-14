package com.forcitis.one.organization.workforce.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.RelationshipKind;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/**
 * Real SQL Server (Testcontainers), only under the Maven profile legacy-it: ./mvnw -B verify -Plegacy-it.
 * Runs the DBA proposal docs/modernization/phase1a/A8_VIEWS.sql against stand-in base tables and proves the §9a
 * claims: the views return the whitelisted columns (and only those) through the ownership chain, the account can
 * read the views but not the base tables, every write / execute / DDL is refused, and the start-up check passes
 * - and refuses an extra column. Also: the old-system account reads two columns of one table and nothing else.
 * No real account, password or host appears here: passwords are generated per run.
 */
@Testcontainers
class A8ViewsMssqlIT {

    @Container
    static MSSQLServerContainer mssql = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    static final Path SCRIPT = Path.of("../docs/modernization/phase1a/A8_VIEWS.sql");
    static final A8Mapping MAPPING = new A8Mapping(-1726439953579618944L, -8187907605834545337L, "-2682590999914657055", "-3267712510147735998");

    static String readerPassword = "Aa1!" + UUID.randomUUID();
    static ReadOnlyConnection reader;
    static ReadOnlyConnection legacyReader;

    static String url(String database) {
        return "jdbc:sqlserver://" + mssql.getHost() + ":" + mssql.getMappedPort(1433) + ";databaseName=" + database
                + ";encrypt=true;trustServerCertificate=true;loginTimeout=30";
    }

    static Connection admin(String database) throws SQLException {
        return DriverManager.getConnection(url(database), mssql.getUsername(), mssql.getPassword());
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
        run("master", "CREATE DATABASE a8test", "CREATE DATABASE legacytest");
        createA8BaseTables();
        seedA8();
        applyDbaScript();
        reader = ReadOnlyConnection.open("it-a8", url("a8test"), "forcitis_reader", readerPassword);
        createLegacy();
    }

    @AfterAll
    static void close() {
        if (reader != null) {
            reader.close();
        }
        if (legacyReader != null) {
            legacyReader.close();
        }
    }

    // ---------------------------------------------------------------- stand-in A8 and the DBA script

    private static void createA8BaseTables() throws SQLException {
        run("a8test",
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

    private static void seedA8() throws SQLException {
        run("a8test",
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
        run("master", "CREATE LOGIN forcitis_reader WITH PASSWORD = N'" + readerPassword + "', CHECK_POLICY = OFF");
        for (String wanted : List.of("schema", "views", "account")) {
            int i = names.indexOf(wanted);
            assertThat(i).as("block " + wanted).isGreaterThanOrEqualTo(0);
            String body = script.substring(starts.get(i), i + 1 < starts.size() ? script.indexOf("-- ==== BLOCK:", starts.get(i)) : script.length());
            body = body.replace("{{READER_LOGIN}}", "forcitis_reader").replace("{{READER_USER}}", "forcitis_reader");
            for (String batch : body.split("(?m)^GO\\s*$")) {
                String statements = batch.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)--.*$", "").trim();
                if (!statements.isEmpty()) {
                    run("a8test", batch);
                }
            }
        }
    }

    private static void createLegacy() throws SQLException {
        run("legacytest",
                "CREATE TABLE dbo.system_dept (id bigint PRIMARY KEY, short_name nvarchar(32), name nvarchar(64), leader_user_id bigint)",
                "CREATE TABLE dbo.system_oauth2_access_token (id bigint PRIMARY KEY, access_token nvarchar(255))",
                "INSERT dbo.system_dept VALUES (2, N'DEL', N'Delivery', 8001), (3, NULL, N'Stopped', NULL)",
                "INSERT dbo.system_oauth2_access_token VALUES (1, 'not-a-real-token')");
        String password = "Aa1!" + UUID.randomUUID();
        run("master", "CREATE LOGIN legacy_reader WITH PASSWORD = N'" + password + "', CHECK_POLICY = OFF");
        run("legacytest", "CREATE USER legacy_reader FOR LOGIN legacy_reader",
                "GRANT SELECT ON dbo.system_dept (id, short_name) TO legacy_reader",
                "DENY SELECT ON dbo.system_oauth2_access_token TO legacy_reader",
                "DENY INSERT, UPDATE, DELETE, EXECUTE ON SCHEMA::dbo TO legacy_reader");
        legacyReader = ReadOnlyConnection.open("it-legacy", url("legacytest"), "legacy_reader", password);
    }

    // ---------------------------------------------------------------- the claims

    @Test
    @DisplayName("§9a the views carry exactly the whitelisted columns, and the start-up check passes against the real catalog")
    void startupCheckPassesAgainstSqlServer() {
        A8StartupCheck check = new A8StartupCheck(new JdbcA8SchemaInspector(reader.jdbc()), MAPPING);

        assertThat(check.problems()).isEmpty();
        assertThat(new JdbcA8SchemaInspector(reader.jdbc()).viewColumns()).containsOnlyKeys("V_ORG_UNIT", "V_ORG_RELATIONSHIP", "V_ORG_POST", "V_ORG_MEMBER");
        assertThat(new JdbcA8SchemaInspector(reader.jdbc()).excessivePermissions()).isEmpty();
    }

    @Test
    @DisplayName("§9a the views are read through the ownership chain and map to the domain with the shared mapper")
    void viewsAreReadAndMapped() {
        SourceSnapshot snapshot = new A8OrgSource(reader.jdbc(), MAPPING).fetchAll();

        assertThat(snapshot.orgUnits()).extracting(u -> u.orgUnitId().value()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(snapshot.orgUnits().stream().filter(u -> u.orgUnitId().value() == 3).findFirst().orElseThrow().status()).isEqualTo(OrgUnitStatus.DISABLED);
        var delivery = snapshot.orgUnits().stream().filter(u -> u.orgUnitId().value() == 2).findFirst().orElseThrow();
        assertThat(delivery.regionCategory()).isEqualTo(RegionCategory.REGIONAL);
        assertThat(delivery.deliveryUnit()).isTrue();
        assertThat(snapshot.orgUnits().stream().filter(u -> u.orgUnitId().value() == 1).findFirst().orElseThrow().regionCategory()).isEqualTo(RegionCategory.HEADQUARTERS);

        assertThat(snapshot.relationships()).extracting(r -> r.kind()).containsExactlyInAnyOrder(RelationshipKind.HEAD, RelationshipKind.REGIONAL_RECEIVABLES_MANAGER);
        assertThat(snapshot.positions()).hasSize(3);

        assertThat(snapshot.members()).extracting(m -> m.userId().value()).as("one row per person, in scope only")
                .containsExactlyInAnyOrder(8001L, 8002L, 8003L);
        SourceMember worker = snapshot.members().stream().filter(m -> m.userId().equals(new UserId(8002))).findFirst().orElseThrow();
        assertThat(worker.leaderId()).isEqualTo(new UserId(8001));
        assertThat(worker.loginName()).isEqualTo("13900008002");
        assertThat(worker.email()).isEqualTo("worker@example.test");
        assertThat(worker.feeEarner()).isTrue();
        assertThat(worker.orgUnitId()).isEqualTo(new OrgUnitId(2));
        assertThat(snapshot.members().stream().filter(m -> m.userId().value() == 8003).findFirst().orElseThrow().loginName()).isNull();
    }

    @Test
    @DisplayName("§9a single-person lookup over the real view returns the same person as the bulk read")
    void singleLookup() {
        A8OrgSource source = new A8OrgSource(reader.jdbc(), MAPPING);

        assertThat(source.fetchMembersByWecomUserId(new com.forcitis.one.kernel.WecomUserId("wx-8002")))
                .containsExactly(source.fetchAll().members().stream().filter(m -> m.userId().value() == 8002).findFirst().orElseThrow());
        assertThat(source.fetchMembersByWecomUserId(new com.forcitis.one.kernel.WecomUserId("wx-8004"))).as("outside the root path").isEmpty();
    }

    @Test
    @DisplayName("§9a A8ReadOnly: the account cannot read a single base table - including the birthday column and a money table")
    void baseTablesAreDenied() {
        JdbcClient jdbc = reader.jdbc();

        for (String table : List.of("dbo.ORG_MEMBER", "dbo.ORG_UNIT", "dbo.formmain_0307", "dbo.contract_values", "dbo.ORG_PRINCIPAL")) {
            assertThatThrownBy(() -> jdbc.sql("SELECT * FROM " + table).query().listOfRows()).as(table).hasMessageContaining("permission was denied");
        }
        assertThatThrownBy(() -> jdbc.sql("SELECT EXT_ATTR_21 FROM dbo.ORG_MEMBER").query().listOfRows()).hasMessageContaining("permission was denied");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM forcitis_one.v_org_member").query(Integer.class).single()).isPositive();
    }

    @Test
    @DisplayName("§9a A8ReadOnly: every write, execute and DDL attempt is refused - on base tables and on the views")
    void writesAreDenied() {
        JdbcClient jdbc = reader.jdbc();
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
        run("a8test", "ALTER VIEW forcitis_one.v_org_post AS SELECT P.ID, P.CODE, P.NAME, P.SORT_ID, P.STATUS, P.IS_DELETED, CAST(NULL AS date) AS BIRTHDAY FROM dbo.ORG_POST P");
        try {
            assertThat(new A8StartupCheck(new JdbcA8SchemaInspector(reader.jdbc()), MAPPING).problems())
                    .anyMatch(p -> p.contains("V_ORG_POST") && p.contains("outside the whitelist") && p.contains("BIRTHDAY"));
        } finally {
            run("a8test", "ALTER VIEW forcitis_one.v_org_post AS SELECT P.ID, P.CODE, P.NAME, P.SORT_ID, P.STATUS, P.IS_DELETED FROM dbo.ORG_POST P");
        }
        assertThat(new A8StartupCheck(new JdbcA8SchemaInspector(reader.jdbc()), MAPPING).problems()).isEmpty();
    }

    @Test
    @DisplayName("RULE-356 an A8 instance whose ids differ from the configuration is refused at start-up, not silently turned into 'no head'")
    void wrongIdsAreRefused() {
        A8Mapping wrong = new A8Mapping(1, 2, "nope", "nope");

        assertThat(new A8StartupCheck(new JdbcA8SchemaInspector(reader.jdbc()), wrong).problems()).hasSize(4);
    }

    @Test
    @DisplayName("§9a an account that was given too much (db_datareader) is refused by the permission probe")
    void tooManyPermissionsAreDetected() throws SQLException {
        String password = "Aa1!" + UUID.randomUUID();
        run("master", "CREATE LOGIN greedy WITH PASSWORD = N'" + password + "', CHECK_POLICY = OFF");
        run("a8test", "CREATE USER greedy FOR LOGIN greedy", "ALTER ROLE db_datareader ADD MEMBER greedy", "GRANT INSERT ON SCHEMA::dbo TO greedy");
        try (ReadOnlyConnection greedy = ReadOnlyConnection.open("it-greedy", url("a8test"), "greedy", password)) {
            assertThat(ReadOnlyGuard.excessiveDatabasePermissions(greedy.jdbc())).contains("SELECT");
            assertThat(new A8StartupCheck(new JdbcA8SchemaInspector(greedy.jdbc()), MAPPING).problems())
                    .anyMatch(p -> p.contains("must not have"));
        }
    }

    // ---------------------------------------------------------------- the old system: two columns of one table

    @Test
    @DisplayName("I-1 the old-system account reads id and short_name of the department table and nothing else, never a token table")
    void legacyAccountIsConfinedToTwoColumns() {
        Map<OrgUnitId, String> names = new LegacyShortNameSource(legacyReader.jdbc()).fetchShortNames();

        assertThat(names).containsEntry(new OrgUnitId(2), "DEL").containsEntry(new OrgUnitId(3), null);
        assertThatThrownBy(() -> legacyReader.jdbc().sql("SELECT name FROM dbo.system_dept").query().listOfRows()).hasMessageContaining("permission was denied");
        assertThatThrownBy(() -> legacyReader.jdbc().sql("SELECT leader_user_id FROM dbo.system_dept").query().listOfRows()).hasMessageContaining("permission was denied");
        assertThatThrownBy(() -> legacyReader.jdbc().sql("SELECT * FROM dbo.system_oauth2_access_token").query().listOfRows()).hasMessageContaining("permission was denied");
        assertThatThrownBy(() -> legacyReader.jdbc().sql("UPDATE dbo.system_dept SET short_name = 'x'").update()).hasMessageContaining("denied");
        assertThat(ReadOnlyGuard.excessiveDatabasePermissions(legacyReader.jdbc())).isEmpty();
    }
}
