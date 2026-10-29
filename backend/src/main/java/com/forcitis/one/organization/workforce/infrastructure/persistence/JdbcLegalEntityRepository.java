package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.LegalEntityCode;
import com.forcitis.one.organization.workforce.api.WorkforceError;
import com.forcitis.one.organization.workforce.domain.LegalEntity;
import com.forcitis.one.organization.workforce.domain.LegalEntityRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLegalEntityRepository implements LegalEntityRepository {
    private final JdbcClient jdbc;
    private final LegalEntityHistory history;
    private final Clock clock;
    private final BusinessZone zone;
    private final IdGenerator ids;

    JdbcLegalEntityRepository(JdbcClient jdbc, Clock clock, BusinessZone zone, IdGenerator ids) {
        this.ids = ids;
        this.jdbc = jdbc;
        this.history = new LegalEntityHistory(jdbc, ids);
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public Optional<LegalEntity> find(LegalEntityId legalEntityId) {
        return load(legalEntityId, "");
    }

    @Override
    public Optional<LegalEntity> findForUpdate(LegalEntityId legalEntityId) {
        return load(legalEntityId, " for update");
    }

    private Optional<LegalEntity> load(LegalEntityId legalEntityId, String lock) {
        return jdbc.sql("""
                select id, code, name, status, version, event_count from organization_workforce.legal_entities where id = :id
                """ + lock)
                .param("id", legalEntityId.value())
                .query((rs, n) -> LegalEntity.restore(new LegalEntityId(rs.getObject("id", UUID.class)), new LegalEntityCode(rs.getString("code")),
                        rs.getString("name"), rs.getString("status"),
                        rs.getLong("version"), rs.getLong("event_count")))
                .optional();
    }

    @Override
    public LegalEntity insert(LegalEntity unsaved) {
        try {
            jdbc.sql("""
                            insert into organization_workforce.legal_entities (id, code, name, status, version, event_count)
                            values (:id, :code, :name, :status, :version, :eventSequence)
                            """)
                    .param("id", unsaved.legalEntityId().value()).param("code", unsaved.code().value()).param("name", unsaved.name()).param("status", unsaved.status().name())
                    .param("version", unsaved.versionAfterSave()).param("eventSequence", unsaved.eventSequenceAfterSave())
                    .update();
            history.recordLegalEntity(unsaved, zone.today(clock));
            return unsaved;
        } catch (DuplicateKeyException taken) {
            throw codeTaken(unsaved.code());
        }
    }

    @Override
    public void save(LegalEntity legalEntity) {
        int updated;
        try {
            updated = jdbc.sql("""
                            update organization_workforce.legal_entities
                               set code = :code, name = :name, status = :status, version = :next, event_count = :eventSequence, updated_at = :updatedAt
                             where id = :id and version = :version and status = :loadedStatus and code = :loadedCode
                            """)
                    .param("code", legalEntity.code().value()).param("name", legalEntity.name())
                    .param("status", legalEntity.status().name()).param("next", legalEntity.versionAfterSave())
                    .param("eventSequence", legalEntity.eventSequenceAfterSave())
                    .param("id", legalEntity.legalEntityId().value()).param("version", legalEntity.version())
                    .param("loadedStatus", legalEntity.loadedStatus().name()).param("loadedCode", legalEntity.loadedCode().value())
                    .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .update();
        } catch (DuplicateKeyException taken) {
            throw codeTaken(legalEntity.code());
        }
        if (updated != 1) {
            throw new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "legal entity " + legalEntity.legalEntityId().value() + " was changed by someone else");
        }
        history.recordLegalEntity(legalEntity, zone.today(clock));
    }

    private static DomainException codeTaken(LegalEntityCode code) {
        return new DomainException(WorkforceError.ORGANIZATION_WORKFORCE_LEGAL_ENTITY_CODE_TAKEN, ErrorKind.CONFLICT,
                "the legal entity code " + code.value() + " is already used");
    }
}
