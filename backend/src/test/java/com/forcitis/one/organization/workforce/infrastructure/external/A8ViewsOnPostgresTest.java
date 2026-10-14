package com.forcitis.one.organization.workforce.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.Gender;
import com.forcitis.one.organization.workforce.domain.OrgUnitStatus;
import com.forcitis.one.organization.workforce.domain.PositionStatus;
import com.forcitis.one.organization.workforce.domain.RegionCategory;
import com.forcitis.one.organization.workforce.domain.RelationshipKind;
import com.forcitis.one.organization.workforce.domain.SourceMember;
import com.forcitis.one.organization.workforce.domain.SourceOrgUnit;
import com.forcitis.one.organization.workforce.domain.SourceSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The A8 anti-corruption layer against stand-in tables that carry the view names (PostgreSQL). The SQL constants are
 * ANSI enough to run here; no real A8 is ever contacted. The SQL Server specifics (INFORMATION_SCHEMA permissions,
 * ownership chaining, write denial) are covered by A8ViewsMssqlIT under the legacy-it profile.
 */
@Testcontainers
class A8ViewsOnPostgresTest {

    static final String HEAD_TYPE = "-1726439953579618944";
    static final String MANAGER_TYPE = "-8187907605834545337";
    static final A8Mapping MAPPING = new A8Mapping(-1726439953579618944L, -8187907605834545337L, "YES-DELIVERY", "YES-FEE");

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    static JdbcClient admin;
    static ReadOnlyConnection connection;
    static A8OrgSource source;

    @BeforeAll
    static void connect() {
        admin = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        connection = ReadOnlyConnection.open("test-a8", postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        source = new A8OrgSource(connection.jdbc(), MAPPING);
    }

    @AfterAll
    static void close() {
        connection.close();
    }

    @BeforeEach
    void createStandInViews() {
        admin.sql("drop schema if exists forcitis_one cascade").update();
        admin.sql("create schema forcitis_one").update();
        admin.sql("""
                create table forcitis_one.v_org_unit (ID bigint, CODE varchar(32), NAME varchar(64), SORT_ID int, PATH varchar(64),
                    IS_DELETED int, IS_INTERNAL int, IS_ENABLE int, CATEGORY varchar(32), DELIVERY_FLAG_ENUM_ID varchar(32))
                """).update();
        admin.sql("create table forcitis_one.v_org_relationship (SOURCE_MEMBER_ID bigint, ORG_UNIT_ID bigint, RELATIONSHIP_TYPE_ID bigint, MEMBER_IS_DELETED int)").update();
        admin.sql("create table forcitis_one.v_org_post (ID bigint, CODE varchar(32), NAME varchar(64), SORT_ID int, STATUS int, IS_DELETED int)").update();
        admin.sql("""
                create table forcitis_one.v_org_member (ID bigint, NAME varchar(64), CODE varchar(32), ORG_UNIT_ID bigint,
                    ORG_UNIT_PATH varchar(64), POST_ID bigint, SUPERVISOR_MEMBER_ID bigint, WE_COM_ID varchar(64), IS_INTERNAL int,
                    LOGIN_NAME varchar(64), ENTERPRISE_EMAIL varchar(128), GENDER varchar(8), FEE_EARNER_ENUM_ID varchar(32))
                """).update();
    }

    private void unit(long id, String path, Integer deleted, Integer internal, Integer enabled, String category, String delivery) {
        admin.sql("insert into forcitis_one.v_org_unit values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, "C" + id, "Unit " + id, 1, path, deleted, internal, enabled, category, delivery).update();
    }

    private void member(long id, Long unit, String path, String code, Integer internal, String login, String wecom) {
        admin.sql("insert into forcitis_one.v_org_member values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, "Person " + id, code, unit, path, 100L, null, wecom, internal, login, "p@example.test", "男", "YES-FEE").update();
    }

    @Test
    @DisplayName("RULE-355 NULL flags count as valid and are named; IS_DELETED = 1, IS_INTERNAL != 1 or IS_ENABLE != 1 disable")
    void flagsDecideActiveOrDisabled() throws Exception {
        unit(1, "00000001", 0, 1, 1, null, null);
        unit(2, "000000010001", 1, 1, 1, null, null);
        unit(3, "000000010002", 0, 0, 1, null, null);
        unit(4, "000000010003", 0, 1, 0, null, null);
        unit(5, "000000010004", null, null, null, null, null);

        Map<Long, SourceOrgUnit> units = byId(source.fetchAll().orgUnits());

        assertThat(units.get(1L).status()).isEqualTo(OrgUnitStatus.ACTIVE);
        assertThat(units.get(2L).status()).isEqualTo(OrgUnitStatus.DISABLED);
        assertThat(units.get(3L).status()).isEqualTo(OrgUnitStatus.DISABLED);
        assertThat(units.get(4L).status()).isEqualTo(OrgUnitStatus.DISABLED);
        assertThat(units.get(5L).status()).isEqualTo(OrgUnitStatus.ACTIVE);
        assertThat(units.get(5L).nullFlags()).containsExactly("IS_DELETED", "IS_INTERNAL", "IS_ENABLE");
        assertThat(units.get(1L).nullFlags()).isEmpty();
    }

    @Test
    @DisplayName("RULE-354 only the subtree below the root PATH is read; RULE-361 people use the same prefix rule")
    void scopeIsThePathPrefixForUnitsAndPeople() {
        unit(1, "00000001", 0, 1, 1, null, null);
        unit(2, "00000002", 0, 1, 1, null, null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        member(11, 2L, "00000002", "E11", 1, "13900000011", "wx-11");
        member(12, 1L, "00000001", "E12", 0, "13900000012", "wx-12");
        member(13, 1L, "00000001", "", 1, "13900000013", "wx-13");
        member(14, 1L, "00000001", null, 1, "13900000014", "wx-14");
        member(15, null, null, "E15", 1, "13900000015", "wx-15");

        SourceSnapshot snapshot = source.fetchAll();

        assertThat(snapshot.orgUnits()).extracting(u -> u.orgUnitId().value()).containsExactly(1L);
        assertThat(snapshot.members()).extracting(m -> m.userId().value()).containsExactly(10L);
    }

    @Test
    @DisplayName("RULE-356 only the configured relationship types are read; the A8 type ids are configuration, not code")
    void relationshipTypesComeFromConfiguration() {
        unit(1, "00000001", 0, 1, 1, null, null);
        admin.sql("insert into forcitis_one.v_org_relationship values (801, 1, " + HEAD_TYPE + ", 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (802, 1, " + MANAGER_TYPE + ", 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (803, 1, 12345, 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (804, 1, " + HEAD_TYPE + ", 1)").update();

        var relationships = source.fetchAll().relationships();

        assertThat(relationships).extracting(r -> r.memberId().value()).containsExactlyInAnyOrder(801L, 802L, 804L);
        assertThat(relationships).filteredOn(r -> r.memberId().value() == 801).singleElement()
                .satisfies(r -> assertThat(r.kind()).isEqualTo(RelationshipKind.HEAD));
        assertThat(relationships).filteredOn(r -> r.memberId().value() == 802).singleElement()
                .satisfies(r -> assertThat(r.kind()).isEqualTo(RelationshipKind.REGIONAL_RECEIVABLES_MANAGER));
        assertThat(relationships).filteredOn(r -> r.memberId().value() == 804).singleElement()
                .satisfies(r -> assertThat(r.memberDeleted()).isTrue());

        A8OrgSource otherInstance = new A8OrgSource(connection.jdbc(), new A8Mapping(1, 2, "x", "y"));
        assertThat(otherInstance.fetchAll().relationships()).isEmpty();
    }

    @Test
    @DisplayName("RULE-356 category, delivery marker and fee-earner marker are resolved with the configured enum ids")
    void markersUseConfiguredEnumIds() {
        unit(1, "00000001", 0, 1, 1, "总部", "YES-DELIVERY");
        unit(2, "000000010001", 0, 1, 1, "区域", "other");
        unit(3, "000000010002", 0, 1, 1, "somewhere", null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        admin.sql("update forcitis_one.v_org_member set FEE_EARNER_ENUM_ID = 'NO'").update();

        SourceSnapshot snapshot = source.fetchAll();
        Map<Long, SourceOrgUnit> units = byId(snapshot.orgUnits());

        assertThat(units.get(1L).regionCategory()).isEqualTo(RegionCategory.HEADQUARTERS);
        assertThat(units.get(1L).deliveryUnit()).isTrue();
        assertThat(units.get(2L).regionCategory()).isEqualTo(RegionCategory.REGIONAL);
        assertThat(units.get(2L).deliveryUnit()).isFalse();
        assertThat(units.get(3L).regionCategory()).isNull();
        assertThat(units.get(3L).rawCategory()).isEqualTo("somewhere");
        assertThat(snapshot.members().getFirst().feeEarner()).isFalse();
    }

    @Test
    @DisplayName("RULE-367 deleted and disabled positions are read too, so the sync can follow them")
    void positionsIncludeDeletedAndDisabled() {
        admin.sql("insert into forcitis_one.v_org_post values (1, 'P1', 'Active', 1, 0, 0)").update();
        admin.sql("insert into forcitis_one.v_org_post values (2, 'P2', 'Disabled', 2, 1, 0)").update();
        admin.sql("insert into forcitis_one.v_org_post values (3, 'P3', 'Deleted', 3, 0, 1)").update();
        admin.sql("insert into forcitis_one.v_org_post values (4, 'P4', 'No flags', 4, null, null)").update();

        var positions = source.fetchAll().positions();

        assertThat(positions).extracting(p -> p.positionId().value(), p -> p.status())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(1L, PositionStatus.ACTIVE),
                        org.assertj.core.groups.Tuple.tuple(2L, PositionStatus.DISABLED),
                        org.assertj.core.groups.Tuple.tuple(3L, PositionStatus.DELETED),
                        org.assertj.core.groups.Tuple.tuple(4L, PositionStatus.ACTIVE));
    }

    @Test
    @DisplayName("single-person lookup and bulk sync share one view, one scope and one mapper")
    void singleLookupUsesTheSameMapperAndScope() {
        unit(1, "00000001", 0, 1, 1, null, null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        member(11, 1L, "00000001", "E11", 1, null, "wx-11");
        member(12, 1L, "00000001", "E12", 0, "13900000012", "wx-12");

        SourceSnapshot all = source.fetchAll();
        List<SourceMember> one = source.fetchMembersByWecomUserId(new WecomUserId("wx-10"));

        assertThat(one).singleElement().isEqualTo(all.members().stream().filter(m -> m.userId().equals(new UserId(10))).findFirst().orElseThrow());
        assertThat(one.getFirst().gender()).isEqualTo(Gender.MALE);
        assertThat(one.getFirst().orgUnitId()).isEqualTo(new OrgUnitId(1));
        assertThat(source.fetchMembersByWecomUserId(new WecomUserId("wx-11"))).singleElement()
                .satisfies(m -> assertThat(m.loginName()).isNull());
        assertThat(source.fetchMembersByWecomUserId(new WecomUserId("wx-12"))).as("out of scope: not internal").isEmpty();
        assertThat(source.fetchMembersByWecomUserId(new WecomUserId("wx-none"))).isEmpty();
    }

    // ---------------------------------------------------------------- the startup check (columns EQUAL the whitelist)

    private static A8SchemaInspector inspector(List<String> excessive) {
        JdbcA8SchemaInspector real = new JdbcA8SchemaInspector(connection.jdbc());
        return new A8SchemaInspector() {
            @Override
            public Map<String, Set<String>> viewColumns() {
                return real.viewColumns();
            }

            @Override
            public int countRelationshipsOfType(long relationshipTypeId) {
                return real.countRelationshipsOfType(relationshipTypeId);
            }

            @Override
            public int countDeliveryUnitsMarked(String enumId) {
                return real.countDeliveryUnitsMarked(enumId);
            }

            @Override
            public int countFeeEarnersMarked(String enumId) {
                return real.countFeeEarnersMarked(enumId);
            }

            @Override
            public List<String> excessivePermissions() {
                return excessive;
            }
        };
    }

    private void seedValidData() {
        unit(1, "00000001", 0, 1, 1, "总部", "YES-DELIVERY");
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        admin.sql("insert into forcitis_one.v_org_relationship values (801, 1, " + HEAD_TYPE + ", 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (802, 1, " + MANAGER_TYPE + ", 0)").update();
    }

    @Test
    @DisplayName("§9a the columns visible in the view schema equal the whitelist: the check passes only then")
    void startupCheckPassesWhenColumnsEqualTheWhitelist() {
        seedValidData();

        assertThat(new A8StartupCheck(inspector(List.of()), MAPPING).problems()).isEmpty();
        new A8StartupCheck(inspector(List.of()), MAPPING).afterSingletonsInstantiated();
    }

    @Test
    @DisplayName("§9a an extra column (say a birthday) is refused, not tolerated")
    void startupCheckRefusesAnExtraColumn() {
        seedValidData();
        admin.sql("alter table forcitis_one.v_org_member add column BIRTHDAY date").update();

        A8StartupCheck check = new A8StartupCheck(inspector(List.of()), MAPPING);

        assertThat(check.problems()).anyMatch(p -> p.contains("V_ORG_MEMBER") && p.contains("outside the whitelist") && p.contains("BIRTHDAY"));
        assertThatThrownBy(check::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class).hasMessageContaining("BIRTHDAY");
    }

    @Test
    @DisplayName("§9a a missing column, a missing view and an extra object in the view schema are all refused")
    void startupCheckRefusesMissingAndExtraObjects() {
        seedValidData();
        admin.sql("alter table forcitis_one.v_org_unit drop column PATH").update();
        admin.sql("drop table forcitis_one.v_org_post").update();
        admin.sql("create table forcitis_one.contract_values (ID bigint)").update();

        List<String> problems = new A8StartupCheck(inspector(List.of()), MAPPING).problems();

        assertThat(problems).anyMatch(p -> p.contains("V_ORG_UNIT") && p.contains("lacks") && p.contains("PATH"));
        assertThat(problems).anyMatch(p -> p.contains("V_ORG_POST") && p.contains("is missing"));
        assertThat(problems).anyMatch(p -> p.contains("CONTRACT_VALUES") && p.contains("not a whitelisted view"));
    }

    @Test
    @DisplayName("RULE-356 configured relationship type ids and enum ids that do not exist in the data stop the start-up")
    void startupCheckVerifiesTheConfiguredIds() {
        seedValidData();

        List<String> problems = new A8StartupCheck(inspector(List.of()), new A8Mapping(1, 2, "NO-SUCH-DELIVERY", "NO-SUCH-FEE")).problems();

        assertThat(problems).anyMatch(p -> p.contains("configured head type 1"));
        assertThat(problems).anyMatch(p -> p.contains("regional receivables manager type 2"));
        assertThat(problems).anyMatch(p -> p.contains("delivery-unit 'yes' enum id"));
        assertThat(problems).anyMatch(p -> p.contains("fee-earner 'yes' enum id"));
        assertThat(new A8StartupCheck(inspector(List.of()), new A8Mapping(0, 0, "", null)).problems())
                .anyMatch(p -> p.contains("head-relationship-type-id is not configured"))
                .anyMatch(p -> p.contains("delivery-unit-yes-enum-id is not configured"));
    }

    @Test
    @DisplayName("§9a an account with write or database-wide read permissions stops the start-up")
    void startupCheckRefusesAnAccountThatCanWrite() {
        seedValidData();

        assertThat(new A8StartupCheck(inspector(List.of("INSERT", "SELECT")), MAPPING).problems())
                .anyMatch(p -> p.contains("must not have") && p.contains("INSERT"));
    }

    @Test
    @DisplayName("the old-system short names are read with one statement over two columns")
    void legacyShortNamesAreReadFromTwoColumns() {
        admin.sql("drop schema if exists dbo cascade").update();
        admin.sql("create schema dbo").update();
        admin.sql("create table dbo.system_dept (id bigint, short_name varchar(32), name varchar(64), leader_user_id bigint)").update();
        admin.sql("insert into dbo.system_dept values (1, ' HQ ', 'Headquarters', 7)").update();
        admin.sql("insert into dbo.system_dept values (2, null, 'No short name', null)").update();
        admin.sql("insert into dbo.system_dept values (3, '', 'Blank', null)").update();

        var names = new LegacyShortNameSource(connection.jdbc()).fetchShortNames();

        assertThat(names).containsEntry(new OrgUnitId(1), "HQ").containsEntry(new OrgUnitId(2), null).containsEntry(new OrgUnitId(3), null);
        assertThat(LegacyShortNameSource.SELECT_SHORT_NAMES).isEqualTo("SELECT id, short_name FROM dbo.system_dept");
    }

    private static Map<Long, SourceOrgUnit> byId(List<SourceOrgUnit> units) {
        Map<Long, SourceOrgUnit> byId = new java.util.HashMap<>();
        units.forEach(u -> byId.put(u.orgUnitId().value(), u));
        return byId;
    }
}
