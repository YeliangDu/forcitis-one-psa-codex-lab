package com.forcitis.one.platform.idempotency.infrastructure;

import com.forcitis.one.kernel.IdempotentResponses;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotentResponses implements IdempotentResponses {
    private final JdbcClient jdbc;

    JdbcIdempotentResponses(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(long userId, String key, String requestHash, Instant now, Instant expiresAt) {
        jdbc.sql("delete from platform_idempotency.idempotent_responses where user_id = :user and idempotency_key = :key and expires_at <= :now")
                .param("user", userId).param("key", key).param("now", Timestamp.from(now)).update();
        return jdbc.sql("""
                        insert into platform_idempotency.idempotent_responses (user_id, idempotency_key, request_hash, expires_at)
                        values (:user, :key, :hash, :expires) on conflict do nothing
                        """)
                .param("user", userId).param("key", key).param("hash", requestHash).param("expires", Timestamp.from(expiresAt))
                .update() == 1;
    }

    @Override
    public Optional<Stored> find(long userId, String key) {
        return jdbc.sql("""
                        select request_hash, response_number, response_name, response_text
                          from platform_idempotency.idempotent_responses where user_id = :user and idempotency_key = :key
                        """)
                .param("user", userId).param("key", key)
                .query((rs, n) -> new Stored(rs.getString("request_hash"), (Integer) rs.getObject("response_number"),
                        rs.getString("response_name"), rs.getString("response_text")))
                .optional();
    }

    @Override
    public void finish(long userId, String key, int responseNumber, String responseName, String responseText) {
        jdbc.sql("""
                        update platform_idempotency.idempotent_responses
                           set response_number = :number, response_name = :name, response_text = :text, updated_at = now()
                         where user_id = :user and idempotency_key = :key
                        """)
                .param("number", responseNumber).param("name", responseName, java.sql.Types.VARCHAR).param("text", responseText)
                .param("user", userId).param("key", key).update();
    }

    @Override
    public void release(long userId, String key) {
        jdbc.sql("delete from platform_idempotency.idempotent_responses where user_id = :user and idempotency_key = :key")
                .param("user", userId).param("key", key).update();
    }
}
