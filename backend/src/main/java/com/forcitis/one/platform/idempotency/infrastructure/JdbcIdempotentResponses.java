package com.forcitis.one.platform.idempotency.infrastructure;

import com.forcitis.one.kernel.IdGenerator;
import com.forcitis.one.kernel.IdempotentResponses;
import com.forcitis.one.kernel.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class JdbcIdempotentResponses implements IdempotentResponses {
    private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final IdGenerator ids;

    JdbcIdempotentResponses(JdbcClient jdbc, JsonMapper json, Clock clock, IdGenerator ids) {
        this.ids = ids;
        this.clock = clock;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean claim(UserId userId, String key, String requestHash, Instant now, Instant expiresAt, Duration reclaimAfter) {
        jdbc.sql("""
                        delete from platform_idempotency.idempotent_responses
                         where user_id = :user and idempotency_key = :key
                           and (expires_at <= :now or (response_number is null and updated_at <= :staleBefore))
                        """)
                .param("user", userId.value()).param("key", key).param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).param("staleBefore", OffsetDateTime.ofInstant(now.minus(reclaimAfter), ZoneOffset.UTC)).update();
        return jdbc.sql("""
                        insert into platform_idempotency.idempotent_responses (idempotent_response_id, user_id, idempotency_key, request_hash, expires_at, created_at, updated_at)
                        values (:id, :user, :key, :hash, :expires, :now, :now) on conflict do nothing
                        """)
                .param("id", ids.next()).param("user", userId.value()).param("key", key).param("hash", requestHash).param("expires", OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC))
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update() == 1;
    }

    @Override
    public Optional<Stored> find(UserId userId, String key) {
        return jdbc.sql("""
                        select request_hash, response_number, response_name, response_text, response_header_text
                          from platform_idempotency.idempotent_responses where user_id = :user and idempotency_key = :key
                        """)
                .param("user", userId.value()).param("key", key)
                .query((rs, n) -> new Stored(rs.getString("request_hash"), (Integer) rs.getObject("response_number"),
                        rs.getString("response_name"), rs.getString("response_text"),
                        rs.getString("response_header_text") == null ? Map.of() : json.readValue(rs.getString("response_header_text"), HEADERS)))
                .optional();
    }

    @Override
    public void finish(UserId userId, String key, int responseNumber, String responseName, String responseText, Map<String, String> responseHeaders) {
        jdbc.sql("""
                        update platform_idempotency.idempotent_responses
                           set response_number = :number, response_name = :name, response_text = :text, response_header_text = :headers, updated_at = :updatedAt
                         where user_id = :user and idempotency_key = :key and response_number is null
                        """)
                .param("number", responseNumber).param("name", responseName, java.sql.Types.VARCHAR).param("text", responseText)
                .param("headers", json.writeValueAsString(responseHeaders)).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("user", userId.value()).param("key", key).update();
    }

    @Override
    public void release(UserId userId, String key) {
        jdbc.sql("""
                delete from platform_idempotency.idempotent_responses where user_id = :user and idempotency_key = :key and response_number is null
                """)
                .param("user", userId.value()).param("key", key).update();
    }

    @Override
    public void holdUntilExpiry(UserId userId, String key) {
        jdbc.sql("""
                update platform_idempotency.idempotent_responses set updated_at = expires_at where user_id = :user and idempotency_key = :key and response_number is null
                """)
                .param("user", userId.value()).param("key", key).update();
    }

    @Override
    public int purgeExpired(Instant now) {
        return jdbc.sql("""
                delete from platform_idempotency.idempotent_responses where expires_at <= :now
                """).param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).update();
    }
}
