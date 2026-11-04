package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.ExternalFailure;
import com.forcitis.one.platform.integration.api.ExternalSource;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.api.OwnObjectId;
import com.forcitis.one.platform.integration.api.SourceObjectKind;
import com.forcitis.one.platform.integration.application.SourceReferences;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads two columns (id, short_name) of one dedicated view; never any token table (I-1). The old system's department ids are translated
 * through the (LEGACY_SYSTEM, ORG_UNIT) source references (ADR-0033 section 2 item 6); a department without one is left out with an
 * alert, so its short name is neither written nor cleared (C8: until P6 imports the references none has one).
 */
final class LegacyOrgNames implements OrgShortNames {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyOrgNames.class);

    private final JdbcClient jdbc;
    private final String viewName;
    private final Runnable recheckBeforeRead;
    private final SourceReferences references;

    LegacyOrgNames(JdbcClient jdbc, String viewName, Runnable recheckBeforeRead, SourceReferences references) {
        this.jdbc = jdbc;
        this.viewName = viewName;
        this.recheckBeforeRead = recheckBeforeRead;
        this.references = references;
    }

    /** The code side of the register check (LegacyReadRegisterTest): the columns of this text equal the registered ones. */
    static String shortNameSql(String viewName) {
        return "SELECT id, short_name FROM " + viewName;
    }

    /** Only short names are read here, so the login is checked again before each read (ADR-0037 item 4.4). */
    @Override
    public Map<OwnObjectId, String> readShortNames() {
        recheckBeforeRead.run();
        Map<Long, String> read = new LinkedHashMap<>();
        try {
            jdbc.sql(shortNameSql(viewName)).query((rs, n) -> {
                long id = rs.getLong("id");
                String shortName = rs.getString("short_name");
                if (id != 0) {
                    read.put(id, shortName == null || shortName.isBlank() ? null : shortName.trim());
                }
                return null;
            }).list();
        } catch (DataAccessException unreachable) {
            throw new ExternalSystemUnavailableException("old system short names cannot be read (" + ExternalFailure.of(unreachable) + ")");
        }
        Map<Long, UUID> own = references.findOwnIds(ExternalSource.LEGACY_SYSTEM, SourceObjectKind.ORG_UNIT, read.keySet());
        Map<OwnObjectId, String> names = new LinkedHashMap<>();
        read.forEach((legacyId, shortName) -> {
            UUID ownId = own.get(legacyId);
            if (ownId != null) {
                names.put(new OwnObjectId(ownId), shortName);
            }
        });
        int unmapped = read.size() - names.size();
        if (unmapped > 0) {
            LOG.error("ALERT platform.integration {} of {} old-system departments have no LEGACY_SYSTEM organisation unit source reference: their short names "
                    + "are unavailable and left as they are", unmapped, read.size());
        }
        return names;
    }
}
