package com.forcitis.one.accounting.ledger.infrastructure.persistence;

import com.forcitis.one.accounting.ledger.domain.LedgerBasis;
import com.forcitis.one.accounting.ledger.domain.LedgerRepository;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLedgerRepository implements LedgerRepository {
    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcLedgerRepository(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public boolean insertIfAbsent(LegalEntityId legalEntityId, LedgerBasis basis, Instant createdAt) {
        return jdbc.sql("""
                insert into accounting_ledger.ledgers (id, legal_entity_id, basis_kind, created_at, updated_at)
                values (:id, :legalEntityId, :basis, :createdAt, :createdAt)
                on conflict (legal_entity_id, basis_kind) do nothing
                """)
                .param("id", ids.next()).param("legalEntityId", legalEntityId.value()).param("basis", basis.name())
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC)).update() == 1;
    }
}
