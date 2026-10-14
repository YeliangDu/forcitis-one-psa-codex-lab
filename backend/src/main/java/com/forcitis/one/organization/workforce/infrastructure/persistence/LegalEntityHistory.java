package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.organization.workforce.domain.LegalEntity;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Keeps the version rows in step with the legal entity, in its transaction; a second change on the same day closes that day's version and appends the next revision. */
final class LegalEntityHistory {
    private final JdbcClient jdbc;

    LegalEntityHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void recordLegalEntity(LegalEntity entity, LocalDate day) {
        record Open(LocalDate from, int revision, boolean same) {
        }
        UUID id = entity.legalEntityId().value();
        List<Open> open = jdbc.sql("""
                        select effective_from_date, revision_number, (code = :code and name = :name and status = :status) as same
                          from organization_workforce.legal_entity_versions where legal_entity_id = :id and effective_to_date is null
                        """)
                .param("id", id).param("code", entity.code().value()).param("name", entity.name()).param("status", entity.status().name())
                .query((rs, n) -> new Open(rs.getDate("effective_from_date").toLocalDate(), rs.getInt("revision_number"), rs.getBoolean("same"))).list();
        if (!open.isEmpty() && open.getFirst().same()) {
            return;
        }
        int revision = open.isEmpty() || !open.getFirst().from().equals(day) ? 0 : open.getFirst().revision() + 1;
        if (!open.isEmpty()) {
            jdbc.sql("update organization_workforce.legal_entity_versions set effective_to_date = :day where legal_entity_id = :id and effective_to_date is null")
                    .param("id", id).param("day", Date.valueOf(day)).update();
        }
        jdbc.sql("""
                        insert into organization_workforce.legal_entity_versions (legal_entity_id, effective_from_date, revision_number, code, name, status)
                        values (:id, :day, :revision, :code, :name, :status)
                        """)
                .param("id", id).param("day", Date.valueOf(day)).param("revision", revision).param("code", entity.code().value())
                .param("name", entity.name()).param("status", entity.status().name()).update();
    }
}
