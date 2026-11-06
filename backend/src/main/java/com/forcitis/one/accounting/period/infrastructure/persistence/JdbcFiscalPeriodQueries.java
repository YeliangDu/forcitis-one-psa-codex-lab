package com.forcitis.one.accounting.period.infrastructure.persistence;

import com.forcitis.one.accounting.period.api.FiscalPeriodId;
import com.forcitis.one.accounting.period.domain.FiscalPeriodQueries;
import com.forcitis.one.accounting.period.domain.FiscalPeriodStatus;
import com.forcitis.one.kernel.LegalEntityId;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFiscalPeriodQueries implements FiscalPeriodQueries {
    private final JdbcClient jdbc;

    JdbcFiscalPeriodQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoredPeriod> lockByKey(LegalEntityId legalEntityId, int fiscalYear, int periodNumber) {
        return jdbc.sql("""
                select id, status from accounting_period.fiscal_periods
                 where legal_entity_id = :legalEntityId and fiscal_year_number = :fiscalYear and period_number = :periodNumber
                   for share
                """)
                .param("legalEntityId", legalEntityId.value(), Types.OTHER).param("fiscalYear", fiscalYear).param("periodNumber", periodNumber)
                .query((rs, n) -> new StoredPeriod(new FiscalPeriodId(rs.getObject("id", UUID.class)), FiscalPeriodStatus.valueOf(rs.getString("status")))).optional();
    }

    @Override
    public Optional<FiscalPeriodId> lockCurrentOpen(LegalEntityId legalEntityId) {
        return jdbc.sql("""
                select id from accounting_period.fiscal_periods
                 where legal_entity_id = :legalEntityId and status = 'OPEN'
                 order by fiscal_year_number, period_number
                 limit 1
                   for share
                """)
                .param("legalEntityId", legalEntityId.value(), Types.OTHER)
                .query((rs, n) -> new FiscalPeriodId(rs.getObject("id", UUID.class))).optional();
    }
}
