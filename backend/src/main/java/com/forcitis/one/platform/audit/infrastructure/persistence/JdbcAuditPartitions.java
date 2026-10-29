package com.forcitis.one.platform.audit.infrastructure.persistence;

import com.forcitis.one.platform.audit.api.AuditPartitions;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditPartitions implements AuditPartitions {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcClient jdbc;

    JdbcAuditPartitions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean exists(YearMonth month) {
        return Boolean.TRUE.equals(jdbc.sql("""
                select to_regclass(:name) is not null
                """)
                .param("name", "platform_audit.audit_entries_p" + month.format(MONTH)).query(Boolean.class).single());
    }
}
