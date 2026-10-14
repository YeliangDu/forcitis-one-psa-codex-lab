package com.forcitis.one.organization.workforce.infrastructure.external;

import com.forcitis.one.organization.workforce.api.OrgUnitId;
import com.forcitis.one.organization.workforce.domain.ShortNameSource;
import com.forcitis.one.organization.workforce.domain.SourceUnavailableException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads org unit short names from the old system's department table, read-only, two columns only (id, short_name).
 * It touches nothing else of the old database - in particular never any token table (SECURITY_INVARIANTS I-1).
 * Qualified "legacy"; only organization.workforce.infrastructure may use the connection.
 */
final class LegacyShortNameSource implements ShortNameSource {

    /** The one statement sent to the old database. */
    static final String SELECT_SHORT_NAMES = "SELECT id, short_name FROM dbo.system_dept";

    private final JdbcClient jdbc;

    LegacyShortNameSource(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<OrgUnitId, String> fetchShortNames() {
        try {
            Map<OrgUnitId, String> names = new LinkedHashMap<>();
            jdbc.sql(SELECT_SHORT_NAMES).query((rs, n) -> {
                long id = rs.getLong("id");
                String shortName = rs.getString("short_name");
                if (id != 0) {
                    names.put(new OrgUnitId(id), shortName == null || shortName.isBlank() ? null : shortName.trim());
                }
                return null;
            }).list();
            return names;
        } catch (DataAccessException unreachable) {
            throw new SourceUnavailableException("old system short names cannot be read (" + unreachable.getClass().getSimpleName() + ")", unreachable);
        }
    }
}
