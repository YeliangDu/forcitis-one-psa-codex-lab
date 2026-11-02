package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.UserSessions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** The tables are Spring Session's; the attributes follow by the foreign key's cascade. */
@Repository
class JdbcUserSessions implements UserSessions {

    private final JdbcClient jdbc;

    JdbcUserSessions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int deleteAllOf(UserId userId) {
        return jdbc.sql("delete from platform_identity.sessions where principal_name = :principal").param("principal", userId.value().toString()).update();
    }
}
