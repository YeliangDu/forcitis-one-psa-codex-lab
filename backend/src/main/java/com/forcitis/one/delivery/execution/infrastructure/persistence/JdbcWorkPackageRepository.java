package com.forcitis.one.delivery.execution.infrastructure.persistence;

import com.forcitis.one.delivery.execution.api.WorkPackageId;
import com.forcitis.one.delivery.execution.domain.WorkPackage;
import com.forcitis.one.delivery.execution.domain.WorkPackageRepository;
import com.forcitis.one.delivery.execution.domain.WorkPackageStatus;
import com.forcitis.one.kernel.Hours;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWorkPackageRepository implements WorkPackageRepository {
    private final JdbcClient jdbc;

    JdbcWorkPackageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkPackage> findById(WorkPackageId id) {
        return jdbc.sql("""
                        select id, planned_hours, actual_hours, status
                          from delivery_execution.work_packages
                         where id = :id
                        """)
                .param("id", id.value())
                .query((rs, n) -> new WorkPackage(new WorkPackageId(rs.getLong("id")),
                        new Hours(rs.getBigDecimal("planned_hours")),
                        new Hours(rs.getBigDecimal("actual_hours")),
                        WorkPackageStatus.valueOf(rs.getString("status"))))
                .optional();
    }
}
