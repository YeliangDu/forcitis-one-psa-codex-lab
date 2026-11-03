package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keeps the version rows in step with the legal entity, in its transaction; the finance head is part of the legal entity, so a new version carries the head the entity holds (ADR-0073 item 3.6); a second change on the same day closes that day's version and appends the next revision. */
final class LegalEntityHistory {
    private final JdbcClient jdbc;
    private final IdGenerator ids;

    LegalEntityHistory(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    void recordLegalEntity(LegalEntity entity, LocalDate day) {
        record Open(LocalDate from, int revision, boolean same) {
        }
        UUID id = entity.legalEntityId().value();
        UUID head = entity.financeHead() == null ? null : entity.financeHead().value();
        List<Open> open = jdbc.sql("""
                        select effective_from_date, revision_number, (code = :code and name = :name and status = :status and finance_head_employee_id is not distinct from cast(:financeHead as uuid)) as same
                          from organization_workforce.legal_entity_versions where legal_entity_id = :id and effective_to_date is null
                        """)
                .param("id", id).param("code", entity.code().value()).param("name", entity.name()).param("status", entity.status().name()).param("financeHead", head, java.sql.Types.OTHER)
                .query((rs, n) -> new Open(rs.getObject("effective_from_date", LocalDate.class), rs.getInt("revision_number"), rs.getBoolean("same"))).list();
        if (!open.isEmpty() && open.getFirst().same()) {
            return;
        }
        int revision = open.isEmpty() || !open.getFirst().from().equals(day) ? 0 : open.getFirst().revision() + 1;
        if (!open.isEmpty()) {
            jdbc.sql("""
                    update organization_workforce.legal_entity_versions set effective_to_date = :day where legal_entity_id = :id and effective_to_date is null
                    """)
                    .param("id", id).param("day", day).update();
        }
        jdbc.sql("""
                        insert into organization_workforce.legal_entity_versions (legal_entity_version_id, legal_entity_id, effective_from_date, revision_number, code, name, status,
                               finance_head_employee_id)
                        values (:versionId, :id, :day, :revision, :code, :name, :status, :financeHead)
                        """)
                .param("versionId", ids.next()).param("id", id).param("day", day).param("revision", revision).param("code", entity.code().value())
                .param("name", entity.name()).param("status", entity.status().name())
                .param("financeHead", head, java.sql.Types.OTHER).update();
    }
}
