package com.forcitis.one.platform.integration.infrastructure.seeyon;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.jdbc.core.simple.JdbcClient;

final class JdbcSeeyonSchemaInspector implements SeeyonSchemaInspector {

    private final JdbcClient jdbc;

    JdbcSeeyonSchemaInspector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Set<String>> viewColumns() {
        Map<String, Set<String>> columns = new TreeMap<>();
        jdbc.sql(SeeyonViewSql.VIEW_COLUMNS_IN_SCHEMA).param("schema", SeeyonViewSql.SCHEMA)
                .query((rs, n) -> {
                    columns.computeIfAbsent(rs.getString("TABLE_NAME").toUpperCase(Locale.ROOT), k -> new TreeSet<>())
                            .add(rs.getString("COLUMN_NAME").toUpperCase(Locale.ROOT));
                    return null;
                }).list();
        return columns;
    }

    @Override
    public int countUnitsAtPath(String path) {
        return jdbc.sql(SeeyonViewSql.COUNT_UNITS_AT_ROOT).param("rootPath", path).query(Integer.class).single();
    }

    @Override
    public int countRelationshipsOfType(long relationshipTypeId) {
        return jdbc.sql(SeeyonViewSql.COUNT_RELATIONSHIPS_OF_TYPE).param("typeId", relationshipTypeId).query(Integer.class).single();
    }

    @Override
    public int countDeliveryUnitsMarked(String enumId) {
        return jdbc.sql(SeeyonViewSql.COUNT_DELIVERY_MARKED).param("enumId", enumId).query(Integer.class).single();
    }

    @Override
    public int countFeeEarnersMarked(String enumId) {
        return jdbc.sql(SeeyonViewSql.COUNT_FEE_EARNER_MARKED).param("enumId", enumId).query(Integer.class).single();
    }
}
