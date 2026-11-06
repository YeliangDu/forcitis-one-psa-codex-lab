package com.forcitis.one.accounting.period.infrastructure.persistence;

import com.forcitis.one.accounting.period.domain.FiscalPeriod;
import com.forcitis.one.accounting.period.domain.FiscalPeriodRepository;
import com.forcitis.one.accounting.period.domain.FiscalYear;
import com.forcitis.one.kernel.LegalEntityId;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFiscalPeriodRepository implements FiscalPeriodRepository {
    private final JdbcClient jdbc;

    JdbcFiscalPeriodRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Integer> latestYear(LegalEntityId legalEntityId) {
        return jdbc.sql("select max(fiscal_year_number) as latest_year from accounting_period.fiscal_periods where legal_entity_id = :legalEntityId")
                .param("legalEntityId", legalEntityId.value(), Types.OTHER)
                .query((rs, n) -> Optional.ofNullable(rs.getObject("latest_year", Integer.class))).single();
    }

    @Override
    public boolean hasYear(LegalEntityId legalEntityId, int fiscalYear) {
        return jdbc.sql("select exists (select 1 from accounting_period.fiscal_periods where legal_entity_id = :legalEntityId and fiscal_year_number = :fiscalYear)")
                .param("legalEntityId", legalEntityId.value(), Types.OTHER).param("fiscalYear", fiscalYear)
                .query(Boolean.class).single();
    }

    @Override
    public void insertAll(List<FiscalPeriod> periods) {
        for (FiscalPeriod period : periods) {
            OffsetDateTime at = OffsetDateTime.ofInstant(period.createdAt(), ZoneOffset.UTC);
            try {
                jdbc.sql("""
                        insert into accounting_period.fiscal_periods (id, legal_entity_id, fiscal_year_number, period_number, status, created_at, updated_at, version)
                        values (:id, :legalEntityId, :fiscalYear, :periodNumber, :status, :at, :at, 0)
                        """)
                        .param("id", period.id().value(), Types.OTHER).param("legalEntityId", period.legalEntityId().value(), Types.OTHER)
                        .param("fiscalYear", period.fiscalYear()).param("periodNumber", period.periodNumber()).param("status", period.status().name())
                        .param("at", at).update();
            } catch (DuplicateKeyException lost) {
                throw FiscalYear.alreadyOpened(period.legalEntityId(), period.fiscalYear());
            }
        }
    }
}
