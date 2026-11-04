package com.forcitis.one.platform.integration.infrastructure.seeyon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.forcitis.one.fixture.doubles.TestOrgIds.ownEmployee;
import static com.forcitis.one.fixture.doubles.TestOrgIds.ownOrgUnit;
import static com.forcitis.one.fixture.doubles.TestOrgIds.ownPosition;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.ExternalSourceBrokenException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceGender;
import com.forcitis.one.platform.integration.api.SourceOrgSnapshot;
import com.forcitis.one.platform.integration.api.SourceOrgUnit;
import com.forcitis.one.platform.integration.api.SourcePerson;
import com.forcitis.one.platform.integration.api.SourceRecordState;
import com.forcitis.one.platform.integration.api.SourceRegionKind;
import com.forcitis.one.platform.integration.api.SourceRelationshipKind;
import com.forcitis.one.platform.integration.application.InMemorySourceReferences;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.forcitis.one.platform.integration.api.StaffLookup;

/** Stand-in tables on PostgreSQL; SQL Server specifics are covered by SeeyonViewsMssqlIT. */
@Testcontainers
class SeeyonViewsOnPostgresTest {

    static final String HEAD_TYPE = "-1726439953579618944";
    static final String MANAGER_TYPE = "-8187907605834545337";
    static final SeeyonMapping MAPPING = new SeeyonMapping(-1726439953579618944L, -8187907605834545337L, "YES-DELIVERY", "YES-FEE", "00000001");

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container();

    static JdbcClient admin;
    static SeeyonOrgSource source;

    @BeforeAll
    static void connect() {
        admin = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        source = new SeeyonOrgSource(admin, admin, MAPPING, () -> { }, new SourceIdTranslation(new InMemorySourceReferences()));
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
        admin.sql("drop schema if exists dbo cascade").update();
        admin.sql("create schema dbo").update();
        admin.sql("create table dbo.ORG_UNIT (ID bigint primary key, PATH varchar(64), TYPE varchar(32))").update();
    }

    /** Top-level paths are Account, the rest Department, as in A8. */
    private void unit(long id, String path, Integer deleted, Integer internal, Integer enabled, String category, String delivery) {
        admin.sql("insert into forcitis_one.v_org_unit values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, "C" + id, "Unit " + id, 1, path, deleted, internal, enabled, category, delivery).update();
        admin.sql("insert into dbo.ORG_UNIT values (?, ?, ?)").params(id, path, path.length() == 8 ? "Account" : "Department").update();
    }

    @Test
    @DisplayName("R-1b-0 TYPE comes from dbo.ORG_UNIT by primary key: an Account below the root, an illegal TYPE or a missing base row rejects the whole read")
    void unitTypeIsReadFromTheBaseTableAndChecked() {
        unit(1, "00000001", 0, 1, 1, null, null);
        unit(2, "000000010001", 0, 1, 1, null, null);
        assertThat(source.readSnapshot().units()).hasSize(2);

        admin.sql("update dbo.ORG_UNIT set TYPE = 'Account' where ID = 2").update();
        assertThatThrownBy(source::readSnapshot).isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("below the sync root are Account");

        admin.sql("update dbo.ORG_UNIT set TYPE = 'department' where ID = 2").update();
        assertThatThrownBy(source::readSnapshot).isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("TYPE");

        admin.sql("delete from dbo.ORG_UNIT where ID = 2").update();
        assertThatThrownBy(source::readSnapshot).as("a view row without its base row is not dropped silently")
                .isInstanceOf(ExternalSourceBrokenException.class).hasMessageContaining("TYPE");
    }

    @Test
    @DisplayName("R-1b-0 the base table is a subset check: missing TYPE or a missing table refuses, an extra column is accepted")
    void startupCheckTreatsBaseTablesAsSubset() {
        seedValidData();
        admin.sql("alter table dbo.ORG_UNIT add column EXTRA_COLUMN int").update();
        assertThat(new SeeyonStartupCheck(inspector(), MAPPING).problems()).isEmpty();

        admin.sql("alter table dbo.ORG_UNIT drop column TYPE").update();
        assertThat(new SeeyonStartupCheck(inspector(), MAPPING).problems())
                .anyMatch(p -> p.contains("dbo.ORG_UNIT") && p.contains("lacks registered columns") && p.contains("TYPE"));

        admin.sql("drop table dbo.ORG_UNIT").update();
        assertThat(new SeeyonStartupCheck(inspector(), MAPPING).problems()).anyMatch(p -> p.contains("dbo.ORG_UNIT is missing"));
    }

    private void member(long id, Long unit, String path, String code, Integer internal, String login, String wecom) {
        admin.sql("insert into forcitis_one.v_org_member values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, "Person " + id, code, unit, path, 100L, null, wecom, internal, login, "p@example.test", "男", "YES-FEE").update();
    }

    @Test
    @DisplayName("RULE-355 NULL flags count as valid and are named by role; deleted, not internal or not enabled disable")
    void flagsDecideActiveOrDisabled() {
        unit(1, "00000001", 0, 1, 1, null, null);
        unit(2, "000000010001", 1, 1, 1, null, null);
        unit(3, "000000010002", 0, 0, 1, null, null);
        unit(4, "000000010003", 0, 1, 0, null, null);
        unit(5, "000000010004", null, null, null, null, null);

        Map<OwnObjectId, SourceOrgUnit> units = byId(source.readSnapshot().units());

        assertThat(units.get(ownOrgUnit(1)).state()).isEqualTo(SourceRecordState.ACTIVE);
        assertThat(units.get(ownOrgUnit(2)).state()).isEqualTo(SourceRecordState.DISABLED);
        assertThat(units.get(ownOrgUnit(3)).state()).isEqualTo(SourceRecordState.DISABLED);
        assertThat(units.get(ownOrgUnit(4)).state()).isEqualTo(SourceRecordState.DISABLED);
        assertThat(units.get(ownOrgUnit(5)).state()).isEqualTo(SourceRecordState.ACTIVE);
        assertThat(units.get(ownOrgUnit(5)).emptyFlags()).containsExactly("deleted", "internal", "enabled");
        assertThat(units.get(ownOrgUnit(1)).emptyFlags()).isEmpty();
    }

    @Test
    @DisplayName("RULE-354 the hierarchy encoding stays inside: the adapter hands out parent ids, root first, children after parents")
    void parentIdsReplaceThePaths() {
        unit(3, "000000010001", 0, 1, 1, null, null);
        unit(2, "00000001", 0, 1, 1, null, null);
        unit(4, "0000000100010001", 0, 1, 1, null, null);

        List<SourceOrgUnit> units = source.readSnapshot().units();

        assertThat(units).extracting(SourceOrgUnit::unitId).containsExactly(ownOrgUnit(2), ownOrgUnit(3), ownOrgUnit(4));
        assertThat(units).extracting(SourceOrgUnit::parentUnitId).containsExactly(null, ownOrgUnit(2), ownOrgUnit(3));
    }

    @Test
    @DisplayName("RULE-354 a unit whose parent is missing breaks the tree: the whole read is rejected, nothing is handed out")
    void brokenTreeIsRejected() {
        unit(2, "00000001", 0, 1, 1, null, null);
        unit(4, "0000000100010001", 0, 1, 1, null, null);

        assertThatThrownBy(source::readSnapshot).isInstanceOf(ExternalSourceBrokenException.class)
                .hasMessageContaining("org unit 4").hasMessageContaining("no parent");
    }

    @Test
    @DisplayName("RULE-354 only the subtree below the root is read; RULE-361 people use the same prefix rule")
    void scopeIsThePathPrefixForUnitsAndPeople() {
        unit(1, "00000001", 0, 1, 1, null, null);
        unit(2, "00000002", 0, 1, 1, null, null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        member(11, 2L, "00000002", "E11", 1, "13900000011", "wx-11");
        member(12, 1L, "00000001", "E12", 0, "13900000012", "wx-12");
        member(13, 1L, "00000001", "", 1, "13900000013", "wx-13");
        member(14, 1L, "00000001", null, 1, "13900000014", "wx-14");
        member(15, null, null, "E15", 1, "13900000015", "wx-15");

        SourceOrgSnapshot snapshot = source.readSnapshot();

        assertThat(snapshot.units()).extracting(SourceOrgUnit::unitId).containsExactly(ownOrgUnit(1));
        assertThat(snapshot.staff()).extracting(SourcePerson::personId).containsExactly(ownEmployee(10));
    }

    @Test
    @DisplayName("D-1a-06 a person whose internal flag is NULL is read as internal and carries the mark for the problem list; 0 stays out")
    void nullInternalFlagCountsAsInternal() {
        unit(1, "00000001", 0, 1, 1, null, null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        member(16, 1L, "00000001", "E16", null, "13900000016", "wx-16");
        member(12, 1L, "00000001", "E12", 0, "13900000012", "wx-12");

        var staff = source.readSnapshot().staff();

        assertThat(staff).extracting(SourcePerson::personId).containsExactlyInAnyOrder(ownEmployee(10), ownEmployee(16));
        assertThat(staff).filteredOn(m -> m.personId().equals(ownEmployee(16))).singleElement().satisfies(m -> assertThat(m.internalFlagMissing()).isTrue());
        assertThat(staff).filteredOn(m -> m.personId().equals(ownEmployee(10))).singleElement().satisfies(m -> assertThat(m.internalFlagMissing()).isFalse());
    }

    @Test
    @DisplayName("RULE-356 only the configured relationship types are read; the type ids are configuration, not code")
    void relationshipTypesComeFromConfiguration() {
        unit(1, "00000001", 0, 1, 1, null, null);
        admin.sql("insert into forcitis_one.v_org_relationship values (801, 1, " + HEAD_TYPE + ", 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (802, 1, " + MANAGER_TYPE + ", 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (803, 1, 12345, 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (804, 1, " + HEAD_TYPE + ", 1)").update();

        var relationships = source.readSnapshot().relationships();

        assertThat(relationships).extracting(r -> r.personId()).containsExactly(ownEmployee(801), ownEmployee(802), ownEmployee(804));
        assertThat(relationships).filteredOn(r -> r.personId().equals(ownEmployee(801))).singleElement()
                .satisfies(r -> assertThat(r.kind()).isEqualTo(SourceRelationshipKind.UNIT_HEAD));
        assertThat(relationships).filteredOn(r -> r.personId().equals(ownEmployee(802))).singleElement()
                .satisfies(r -> assertThat(r.kind()).isEqualTo(SourceRelationshipKind.REGIONAL_RECEIVABLES_MANAGER));
        assertThat(relationships).filteredOn(r -> r.personId().equals(ownEmployee(804))).singleElement()
                .satisfies(r -> assertThat(r.personDeleted()).isTrue());

        SeeyonOrgSource otherInstance = new SeeyonOrgSource(admin, admin, new SeeyonMapping(1, 2, "x", "y", "00000001"), () -> { },
                new SourceIdTranslation(new InMemorySourceReferences()));
        assertThat(otherInstance.readSnapshot().relationships()).isEmpty();
    }

    @Test
    @DisplayName("RULE-356 / RULING-P3B-13: the adapter lists the heads of a unit by signed source id, smallest first, whatever order the view returns them in")
    void headsAreListedBySmallestSourceIdFirst() {
        unit(1, "00000001", 0, 1, 1, null, null);
        admin.sql("insert into forcitis_one.v_org_post values (100, 'P100', 'Post 100', 1, 0, 0)").update();
        member(805, 1L, "00000001", "E805", 1, "13900000805", "wx-805");
        member(-5, 1L, "00000001", "E-5", 1, "13900000005", "wx-5");
        admin.sql("insert into forcitis_one.v_org_relationship values (805, 1, " + HEAD_TYPE + ", 0)").update();
        admin.sql("insert into forcitis_one.v_org_relationship values (-5, 1, " + HEAD_TYPE + ", 0)").update();

        assertThat(source.readSnapshot().relationships()).extracting(r -> r.personId()).containsExactly(ownEmployee(-5), ownEmployee(805));
    }

    @Test
    @DisplayName("RULE-356 region kind, delivery marker and fee-earner marker are resolved with the configured enum ids")
    void markersUseConfiguredEnumIds() {
        unit(1, "00000001", 0, 1, 1, "总部", "YES-DELIVERY");
        unit(2, "000000010001", 0, 1, 1, "区域", "other");
        unit(3, "000000010002", 0, 1, 1, "somewhere", null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        admin.sql("update forcitis_one.v_org_member set FEE_EARNER_ENUM_ID = 'NO'").update();

        SourceOrgSnapshot snapshot = source.readSnapshot();
        Map<OwnObjectId, SourceOrgUnit> units = byId(snapshot.units());

        assertThat(units.get(ownOrgUnit(1)).regionKind()).isEqualTo(SourceRegionKind.HEADQUARTERS);
        assertThat(units.get(ownOrgUnit(1)).deliveryUnit()).isTrue();
        assertThat(units.get(ownOrgUnit(2)).regionKind()).isEqualTo(SourceRegionKind.REGIONAL);
        assertThat(units.get(ownOrgUnit(2)).deliveryUnit()).isFalse();
        assertThat(units.get(ownOrgUnit(3)).regionKind()).isNull();
        assertThat(units.get(ownOrgUnit(3)).rawRegion()).isEqualTo("somewhere");
        assertThat(snapshot.staff().getFirst().feeEarner()).isFalse();
    }

    @Test
    @DisplayName("RULE-367 deleted and disabled positions are read too, so the snapshot can follow them")
    void positionsIncludeDeletedAndDisabled() {
        admin.sql("insert into forcitis_one.v_org_post values (1, 'P1', 'Active', 1, 0, 0)").update();
        admin.sql("insert into forcitis_one.v_org_post values (2, 'P2', 'Disabled', 2, 1, 0)").update();
        admin.sql("insert into forcitis_one.v_org_post values (3, 'P3', 'Deleted', 3, 0, 1)").update();
        admin.sql("insert into forcitis_one.v_org_post values (4, 'P4', 'No flags', 4, null, null)").update();

        var positions = source.readSnapshot().positions();

        assertThat(positions).extracting(p -> p.positionId(), p -> p.state())
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(ownPosition(1), SourceRecordState.ACTIVE),
                        org.assertj.core.groups.Tuple.tuple(ownPosition(2), SourceRecordState.DISABLED),
                        org.assertj.core.groups.Tuple.tuple(ownPosition(3), SourceRecordState.DELETED),
                        org.assertj.core.groups.Tuple.tuple(ownPosition(4), SourceRecordState.ACTIVE));
    }

    @Test
    @DisplayName("single-person lookups (by WeCom id and by login name) and the full read share one view, one scope and one mapper")
    void singleLookupsUseTheSameMapperAndScope() {
        unit(1, "00000001", 0, 1, 1, null, null);
        member(10, 1L, "00000001", "E10", 1, "13900000010", "wx-10");
        member(11, 1L, "00000001", "E11", 1, null, "wx-11");
        member(12, 1L, "00000001", "E12", 0, "13900000012", "wx-12");

        SourceOrgSnapshot all = source.readSnapshot();
        List<SourcePerson> byWecom = source.findStaffByLogin(new StaffLookup.ByWecomUser(new WecomUserId("wx-10")));
        List<SourcePerson> byLogin = source.findStaffByLogin(new StaffLookup.ByLoginName("13900000010"));

        SourcePerson expected = all.staff().stream().filter(m -> m.personId().equals(ownEmployee(10))).findFirst().orElseThrow();
        assertThat(byWecom).singleElement().isEqualTo(expected);
        assertThat(byLogin).singleElement().isEqualTo(expected);
        assertThat(byWecom.getFirst().gender()).isEqualTo(SourceGender.MALE);
        assertThat(byWecom.getFirst().unitId()).isEqualTo(ownOrgUnit(1));
        assertThat(source.findStaffByLogin(new StaffLookup.ByWecomUser(new WecomUserId("wx-11")))).singleElement()
                .satisfies(m -> assertThat(m.loginName()).isNull());
        assertThat(source.findStaffByLogin(new StaffLookup.ByWecomUser(new WecomUserId("wx-12")))).as("out of scope: not internal").isEmpty();
        assertThat(source.findStaffByLogin(new StaffLookup.ByLoginName("13900000012"))).as("out of scope: not internal").isEmpty();
        assertThat(source.findStaffByLogin(new StaffLookup.ByWecomUser(new WecomUserId("wx-none")))).isEmpty();
        assertThat(source.findStaffByLogin(new StaffLookup.ByLoginName("nobody"))).isEmpty();
    }

    private static SeeyonSchemaInspector inspector() {
        JdbcSeeyonSchemaInspector real = new JdbcSeeyonSchemaInspector(admin);
        return new SeeyonSchemaInspector() {
            @Override
            public Map<String, Set<String>> viewColumns() {
                return real.viewColumns();
            }

            @Override
            public Map<String, Set<String>> baseTableColumns() {
                return real.baseTableColumns();
            }

            @Override
            public int countUnitsAtPath(String path) {
                return real.countUnitsAtPath(path);
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

        assertThat(new SeeyonStartupCheck(inspector(), MAPPING).problems()).isEmpty();
        new SeeyonStartupCheck(inspector(), MAPPING).afterSingletonsInstantiated();
    }

    @Test
    @DisplayName("§9a an extra column (say a birthday) is refused, not tolerated")
    void startupCheckRefusesAnExtraColumn() {
        seedValidData();
        admin.sql("alter table forcitis_one.v_org_member add column BIRTHDAY date").update();

        SeeyonStartupCheck check = new SeeyonStartupCheck(inspector(), MAPPING);

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

        List<String> problems = new SeeyonStartupCheck(inspector(), MAPPING).problems();

        assertThat(problems).anyMatch(p -> p.contains("V_ORG_UNIT") && p.contains("lacks") && p.contains("PATH"));
        assertThat(problems).anyMatch(p -> p.contains("V_ORG_POST") && p.contains("is missing"));
        assertThat(problems).anyMatch(p -> p.contains("CONTRACT_VALUES") && p.contains("not a whitelisted view"));
    }

    @Test
    @DisplayName("RULE-356 configured relationship type ids and enum ids that do not exist in the data stop the start-up")
    void startupCheckVerifiesTheConfiguredIds() {
        seedValidData();

        List<String> problems = new SeeyonStartupCheck(inspector(), new SeeyonMapping(1, 2, "NO-SUCH-DELIVERY", "NO-SUCH-FEE", "00000001")).problems();

        assertThat(problems).anyMatch(p -> p.contains("configured head type 1"));
        assertThat(problems).anyMatch(p -> p.contains("regional receivables manager type 2"));
        assertThat(problems).anyMatch(p -> p.contains("delivery-unit 'yes' enum id"));
        assertThat(problems).anyMatch(p -> p.contains("fee-earner 'yes' enum id"));
        assertThat(new SeeyonStartupCheck(inspector(), new SeeyonMapping(0, 0, "", null, "")).problems())
                .anyMatch(p -> p.contains("head-relationship-type-id is not configured"))
                .anyMatch(p -> p.contains("delivery-unit-yes-enum-id is not configured"));
    }

    @Test
    @DisplayName("M7 a root path that is malformed, or that no org unit sits at, stops the start-up")
    void startupCheckVerifiesTheRootPath() {
        seedValidData();

        assertThat(new SeeyonStartupCheck(inspector(), new SeeyonMapping(MAPPING.headRelationshipTypeId(),
                MAPPING.regionalReceivablesManagerRelationshipTypeId(), "YES-DELIVERY", "YES-FEE", "0000009")).problems())
                .anyMatch(p -> p.contains("root-path must be digits"));
        assertThat(new SeeyonStartupCheck(inspector(), new SeeyonMapping(MAPPING.headRelationshipTypeId(),
                MAPPING.regionalReceivablesManagerRelationshipTypeId(), "YES-DELIVERY", "YES-FEE", "00000009")).problems())
                .anyMatch(p -> p.contains("no org unit sits at the configured root path 00000009"));
    }

    private static Map<OwnObjectId, SourceOrgUnit> byId(List<SourceOrgUnit> units) {
        Map<OwnObjectId, SourceOrgUnit> byId = new HashMap<>();
        units.forEach(u -> byId.put(u.unitId(), u));
        return byId;
    }
}
