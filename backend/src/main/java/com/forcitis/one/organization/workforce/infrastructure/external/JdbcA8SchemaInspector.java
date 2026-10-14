package com.forcitis.one.organization.workforce.infrastructure.external;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads the facts the startup check needs, through the read-only A8 connection. */
final class JdbcA8SchemaInspector implements A8SchemaInspector {

    private final JdbcClient jdbc;

    JdbcA8SchemaInspector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Set<String>> viewColumns() {
        Map<String, Set<String>> columns = new TreeMap<>();
        jdbc.sql(A8ViewSql.VIEW_COLUMNS_IN_SCHEMA).param("schema", A8ViewSql.SCHEMA)
                .query((rs, n) -> {
                    columns.computeIfAbsent(rs.getString("TABLE_NAME").toUpperCase(Locale.ROOT), k -> new TreeSet<>())
                            .add(rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                    return null;
                }).list();
        return columns;
    }

    @Override
    public int countRelationshipsOfType(long relationshipTypeId) {
        return jdbc.sql(A8ViewSql.COUNT_RELATIONSHIPS_OF_TYPE).param("typeId", relationshipTypeId).query(Integer.class).single();
    }

    @Override
    public int countDeliveryUnitsMarked(String enumId) {
        return jdbc.sql(A8ViewSql.COUNT_DELIVERY_MARKED).param("enumId", enumId).query(Integer.class).single();
    }

    @Override
    public int countFeeEarnersMarked(String enumId) {
        return jdbc.sql(A8ViewSql.COUNT_FEE_EARNER_MARKED).param("enumId", enumId).query(Integer.class).single();
    }

    @Override
    public List<String> excessivePermissions() {
        return ReadOnlyGuard.excessiveDatabasePermissions(jdbc);
    }
}
