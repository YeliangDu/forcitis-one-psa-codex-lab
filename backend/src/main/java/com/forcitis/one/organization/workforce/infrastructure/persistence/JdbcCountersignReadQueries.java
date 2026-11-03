package com.forcitis.one.organization.workforce.infrastructure.persistence;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries;
import com.forcitis.one.organization.workforce.domain.OrgCountersignBasis;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCountersignReadQueries implements CountersignReadQueries {
    private final JdbcClient jdbc;

    JdbcCountersignReadQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<LegalEntityId, String> legalEntityNames(Collection<LegalEntityId> ids) {
        Map<LegalEntityId, String> names = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        jdbc.sql("select id, name from organization_workforce.legal_entities where id in (:ids)").param("ids", ids.stream().map(LegalEntityId::value).toList())
                .query((rs, n) -> names.put(new LegalEntityId(rs.getObject("id", UUID.class)), rs.getString("name"))).list();
        return names;
    }

    @Override
    public Map<EmployeeId, Person> employees(Collection<EmployeeId> ids) {
        Map<EmployeeId, Person> people = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return people;
        }
        jdbc.sql("select id, employee_code, display_name from organization_workforce.employees where id in (:ids)").param("ids", ids.stream().map(EmployeeId::value).toList())
                .query((rs, n) -> people.put(new EmployeeId(rs.getObject("id", UUID.class)), new Person(rs.getString("employee_code"), rs.getString("display_name")))).list();
        return people;
    }

    @Override
    public Optional<String> orgUnitName(OrgUnitId orgUnit) {
        return jdbc.sql("select name from organization_workforce.org_units where id = :id").param("id", orgUnit.value()).query(String.class).optional();
    }

    @Override
    public List<BasisEntry> basisPage(OrgStructureCommandId command, @Nullable UUID after, int limit) {
        if (after != null && jdbc.sql("select 1 from organization_workforce.attribution_countersign_bases where attribution_countersign_basis_id = :after"
                + " and org_structure_command_id = :command").param("after", after).param("command", command.value()).query(Integer.class).optional().isEmpty()) {
            throw new InvalidCursorException("the row the cursor points at is gone");
        }
        return jdbc.sql("""
                select b.attribution_countersign_basis_id, b.employee_id, b.previous_legal_entity_id, b.new_legal_entity_id
                  from organization_workforce.attribution_countersign_bases b
                 where b.org_structure_command_id = :command
                   and (:after::uuid is null or (b.created_at, b.attribution_countersign_basis_id) >
                        (select a.created_at, a.attribution_countersign_basis_id from organization_workforce.attribution_countersign_bases a
                          where a.attribution_countersign_basis_id = :after))
                 order by b.created_at, b.attribution_countersign_basis_id
                 limit :limit
                """).param("command", command.value()).param("after", after, java.sql.Types.OTHER).param("limit", limit)
                .query((rs, n) -> new BasisEntry(rs.getObject("attribution_countersign_basis_id", UUID.class),
                        new OrgCountersignBasis(new EmployeeId(rs.getObject("employee_id", UUID.class)),
                                new LegalEntityId(rs.getObject("previous_legal_entity_id", UUID.class)), new LegalEntityId(rs.getObject("new_legal_entity_id", UUID.class)))))
                .list();
    }
}
