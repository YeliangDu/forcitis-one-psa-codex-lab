package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.platform.identity.domain.ExpiredSessions;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables are Spring Session's; the framework's own clean-up is switched off so that every session that ends is audited first. */
@Repository
class JdbcExpiredSessions implements ExpiredSessions {

    private final JdbcClient jdbc;
    private final SessionStoreClock storeClock = new SessionStoreClock();

    JdbcExpiredSessions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Expired> find(long absoluteTimeoutMillis, int limit) {
        return jdbc.sql("""
                        select primary_id, principal_name,
                               case when creation_time + :absolute < :now then 'ABSOLUTE' else 'IDLE' end as cause
                          from platform_identity.sessions
                         where expiry_time < :now or creation_time + :absolute < :now
                         order by expiry_time
                         limit :limit
                        """)
                .param("now", storeClock.millis()).param("absolute", absoluteTimeoutMillis).param("limit", limit)
                .query((rs, n) -> new Expired(rs.getString("primary_id").trim(), rs.getString("principal_name"), Cause.valueOf(rs.getString("cause"))))
                .list();
    }

    @Override
    public int deleteSessions(List<String> primaryKeys) {
        if (primaryKeys.isEmpty()) {
            return 0;
        }
        return jdbc.sql("""
                delete from platform_identity.sessions where primary_id in (:ids)
                """).param("ids", primaryKeys).update();
    }
}
