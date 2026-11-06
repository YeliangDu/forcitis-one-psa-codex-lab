package com.forcitis.one.accounting.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestPostgres;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.integration.api.WecomGateway;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #1440 G1: the signed ledgers table refuses a third basis, a second book of one basis and every change after creation. */
@SpringBootTest(properties = {"flowable.async-executor-activate=false"})
class LedgerTableIT {
    private static final String TABLE = "accounting_ledger.ledgers";

    @ServiceConnection
    static final PostgreSQLContainer postgres = TestPostgres.container().withInitScript("init-schemas.sql").withUrlParam("currentSchema", "platform_approval");

    static {
        postgres.start();
    }

    @MockitoBean
    WecomGateway wecomGateway;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    IdGenerator ids;

    private UUID insert(UUID entity, String basis) {
        UUID id = ids.next();
        jdbc.sql("insert into " + TABLE + " (id, legal_entity_id, basis_kind, created_at, updated_at) values (:id, :entity, :basis, now(), now())")
                .param("id", id).param("entity", entity).param("basis", basis).update();
        return id;
    }

    @Test
    @DisplayName("#1440 an entity keeps one ACCRUAL and one CASH book, and no other basis")
    void oneBookPerEntityAndBasis() {
        UUID entity = ids.next();
        insert(entity, "ACCRUAL");
        insert(entity, "CASH");
        assertThatThrownBy(() -> insert(entity, "CASH")).isInstanceOf(DataAccessException.class).hasMessageContaining("uk_ledgers_legal_entity_id_basis_kind");
        assertThatThrownBy(() -> insert(ids.next(), "FOREIGN")).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_ledgers_basis_kind");
    }

    @Test
    @DisplayName("#1440 a book is never updated, deleted or truncated")
    void aBookNeverChanges() {
        UUID id = insert(ids.next(), "ACCRUAL");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set basis_kind = 'CASH' where id = :id").param("id", id).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("never changed or removed");
        assertThatThrownBy(() -> jdbc.sql("update " + TABLE + " set updated_at = now() where id = :id").param("id", id).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("never changed or removed");
        assertThatThrownBy(() -> jdbc.sql("delete from " + TABLE + " where id = :id").param("id", id).update())
                .isInstanceOf(DataAccessException.class).hasMessageContaining("never changed or removed");
        assertThatThrownBy(() -> jdbc.sql("truncate table " + TABLE).update()).isInstanceOf(DataAccessException.class).hasMessageContaining("never changed or removed");
        assertThat(jdbc.sql("select count(*) from " + TABLE + " where id = :id").param("id", id).query(Long.class).single()).isEqualTo(1L);
    }
}
