package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.BrowserIdentifiers;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcBrowserIdentifiers implements BrowserIdentifiers {

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcBrowserIdentifiers(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public Optional<Known> find(String identifierHash, UserId userId) {
        return jdbc.sql("""
                        select last_login_at, retired_at is not null as retired from platform_identity.browser_identifiers
                         where identifier_hash = :hash and user_id = :user
                        """)
                .param("hash", identifierHash).param("user", userId.value())
                .query((rs, n) -> new Known(rs.getObject("last_login_at", OffsetDateTime.class).toInstant(), rs.getBoolean("retired"))).optional();
    }

    @Override
    public void replace(UserId userId, String oldHash, String newHash, Instant now) {
        OffsetDateTime at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        if (oldHash != null) {
            jdbc.sql("""
                    update platform_identity.browser_identifiers set retired_at = :now, updated_at = :now
                     where identifier_hash = :old and user_id = :user and retired_at is null
                    """).param("now", at).param("old", oldHash).param("user", userId.value()).update();
            var moved = jdbc.sql("""
                    update platform_identity.browser_identifiers set retired_at = :now, updated_at = :now
                     where identifier_hash = :old and user_id <> :user and retired_at is null
                    returning user_id, last_login_at
                    """).param("now", at).param("old", oldHash).param("user", userId.value())
                    .query((rs, n) -> new Moved(rs.getObject("user_id", UUID.class), rs.getObject("last_login_at", OffsetDateTime.class))).list();
            for (Moved other : moved) {
                insert(other.userId(), newHash, other.lastLoginAt());
            }
        }
        insert(userId.value(), newHash, at);
    }

    private record Moved(UUID userId, OffsetDateTime lastLoginAt) {
    }

    private void insert(UUID userId, String hash, OffsetDateTime lastLoginAt) {
        jdbc.sql("""
                insert into platform_identity.browser_identifiers (browser_identifier_id, user_id, identifier_hash, last_login_at)
                values (:id, :user, :hash, :at)
                """).param("id", ids.next()).param("user", userId).param("hash", hash).param("at", lastLoginAt).update();
    }

    @Override
    public int invalidateAll(UserId userId) {
        return jdbc.sql("""
                delete from platform_identity.browser_identifiers where user_id = :user
                """).param("user", userId.value()).update();
    }

    @Override
    public int purge(Instant olderThan) {
        return jdbc.sql("""
                delete from platform_identity.browser_identifiers
                 where last_login_at < :limit or retired_at < :limit
                """).param("limit", OffsetDateTime.ofInstant(olderThan, ZoneOffset.UTC)).update();
    }
}
