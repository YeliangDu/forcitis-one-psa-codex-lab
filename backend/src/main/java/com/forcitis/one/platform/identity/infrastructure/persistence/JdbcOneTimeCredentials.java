package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.platform.identity.domain.OneTimeCredentials;
import com.forcitis.one.platform.integration.api.WecomEntry;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Every consuming statement is atomic in the database (delete-returning, insert-on-conflict), so replays have one winner (I-9). */
@Repository
class JdbcOneTimeCredentials implements OneTimeCredentials {

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcOneTimeCredentials(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public void storeLoginState(String stateHash, String bindingHash, WecomEntry entry, Instant expiresAt) {
        jdbc.sql("""
                insert into platform_identity.login_states (login_state_id, state_hash, binding_hash, entry, expires_at) values (:id, :hash, :binding, :entry, :expires)
                """)
                .param("id", ids.next()).param("hash", stateHash).param("binding", bindingHash).param("entry", entry.name()).param("expires", utc(expiresAt)).update();
    }

    @Override
    public Optional<WecomEntry> consumeLoginState(String stateHash, String bindingHash, Instant now) {
        return jdbc.sql("""
                delete from platform_identity.login_states where state_hash = :hash and binding_hash = :binding and expires_at > :now returning entry
                """)
                .param("hash", stateHash).param("binding", bindingHash).param("now", utc(now))
                .query(String.class).optional().map(WecomEntry::valueOf);
    }

    @Override
    public boolean markTicketUsed(String ticketSource, String ticketHash, Instant retainUntil) {
        return jdbc.sql("""
                        insert into platform_identity.sso_ticket_uses (sso_ticket_use_id, ticket_source, ticket_hash, expires_at)
                        values (:id, :source, :hash, :retain)
                        on conflict do nothing
                        """)
                .param("id", ids.next()).param("source", ticketSource).param("hash", ticketHash).param("retain", utc(retainUntil)).update() == 1;
    }

    @Override
    public int purgeExpired(Instant now) {
        int states = jdbc.sql("""
                delete from platform_identity.login_states where expires_at <= :now
                """).param("now", utc(now)).update();
        int tickets = jdbc.sql("""
                delete from platform_identity.sso_ticket_uses where expires_at <= :now
                """).param("now", utc(now)).update();
        return states + tickets;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
