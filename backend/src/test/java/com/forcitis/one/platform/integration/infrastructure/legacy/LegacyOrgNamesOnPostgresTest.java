package com.forcitis.one.platform.integration.infrastructure.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.util.UUID;
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

@Testcontainers
class LegacyOrgNamesOnPostgresTest {

    static final String VIEW = "legacy_stand_in.v_one_legacy_org_short_name";
    static final String READER = "legacy_names_reader";

    @Container
    static PostgreSQLContainer postgres = TestPostgres.container();

    static JdbcClient admin;

    @BeforeAll
    static void connect() {
        admin = JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
    }

    @AfterAll
    static void close() {
        admin.sql("drop schema if exists legacy_stand_in cascade").update();
        admin.sql("drop role if exists " + READER).update();
    }

    @BeforeEach
    void standIn() {
        admin.sql("drop schema if exists legacy_stand_in cascade").update();
        admin.sql("create schema legacy_stand_in").update();
        admin.sql("create table legacy_stand_in.v_one_legacy_org_short_name (id bigint, short_name varchar(32))").update();
        admin.sql("insert into legacy_stand_in.v_one_legacy_org_short_name values (1, ' HQ '), (2, null), (3, '')").update();
        admin.sql("create table legacy_stand_in.token_table (id bigint, token varchar(32))").update();
        admin.sql("drop role if exists " + READER).update();
    }

    @Test
    @DisplayName("the short names are read with one statement over the two columns of the dedicated view")
    void shortNamesAreRead() {
        var names = new LegacyOrgNames(admin, VIEW).readShortNames();

        assertThat(names).containsEntry(new SourceUnitId(1), "HQ").containsEntry(new SourceUnitId(2), null).containsEntry(new SourceUnitId(3), null);
    }

    @Test
    @DisplayName("an unreachable view is an unavailable source, naming the failure class only")
    void unreadableView() {
        assertThatThrownBy(() -> new LegacyOrgNames(admin, "legacy_stand_in.no_such_view").readShortNames())
                .isInstanceOf(ExternalSystemUnavailableException.class).hasMessageStartingWith("old system short names cannot be read");
    }

    private JdbcClient readerClient() {
        String password = "Aa1" + UUID.randomUUID();
        admin.sql("create role " + READER + " login password '" + password + "'").update();
        admin.sql("grant usage on schema legacy_stand_in to " + READER).update();
        admin.sql("grant select on " + VIEW + " to " + READER).update();
        return JdbcClient.create(new DriverManagerDataSource(postgres.getJdbcUrl(), READER, password));
    }

    @Test
    @DisplayName("I-1 the single-object guard passes for an account that can read only the view")
    void guardPasses() {
        new SingleObjectGuard(readerClient(), VIEW).verify();
    }

    @Test
    @DisplayName("I-1 the single-object guard refuses an account that can also read another object (a token table, say)")
    void guardRefusesMore() {
        JdbcClient reader = readerClient();
        admin.sql("grant select on legacy_stand_in.token_table to " + READER).update();

        assertThatThrownBy(() -> new SingleObjectGuard(reader, VIEW).verify()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("legacy_stand_in.token_table");
    }
}
