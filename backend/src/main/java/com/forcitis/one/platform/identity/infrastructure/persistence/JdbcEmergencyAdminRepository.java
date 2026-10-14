package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmergencyAdminRepository implements EmergencyAdminRepository {

    private final JdbcClient jdbc;

    JdbcEmergencyAdminRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EmergencyAdmin> findByLoginName(String loginName) {
        return jdbc.sql("""
                        select user_id, account_name, password_hash, totp_secret_encrypted, last_totp_step, failed_attempts,
                               locked_until, is_enabled
                          from platform_identity.emergency_admins
                         where account_name = :name
                        """)
                .param("name", loginName)
                .query((rs, n) -> {
                    OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
                    return new EmergencyAdmin(new UserId(rs.getLong("user_id")), rs.getString("account_name"),
                            rs.getString("password_hash"), rs.getString("totp_secret_encrypted"), rs.getLong("last_totp_step"),
                            rs.getInt("failed_attempts"), lockedUntil == null ? null : lockedUntil.toInstant(),
                            rs.getBoolean("is_enabled"));
                }).optional();
    }

    @Override
    public void register(EmergencyAdmin admin) {
        jdbc.sql("""
                        insert into platform_identity.emergency_admins (user_id, account_name, password_hash, totp_secret_encrypted)
                        values (:id, :name, :hash, :secret)
                        on conflict (user_id) do update
                           set account_name = excluded.account_name, password_hash = excluded.password_hash,
                               totp_secret_encrypted = excluded.totp_secret_encrypted, last_totp_step = 0,
                               failed_attempts = 0, locked_until = null, is_enabled = true, updated_at = now()
                        """)
                .param("id", admin.userId().value()).param("name", admin.loginName()).param("hash", admin.passwordHash())
                .param("secret", admin.totpSecretEncrypted()).update();
    }

    @Override
    public void recordFailure(UserId userId, Instant now, int maxAttempts, Duration lock) {
        // One statement: the counter restarts after an expired lock, and the lock is set when the new count reaches the limit.
        String newCount = "case when locked_until is not null and locked_until <= :now then 1 else failed_attempts + 1 end";
        jdbc.sql("""
                        update platform_identity.emergency_admins
                           set failed_attempts = %s,
                               locked_until = case when (%s) >= :max then :until else null end,
                               updated_at = now()
                         where user_id = :id
                        """.formatted(newCount, newCount))
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).param("max", maxAttempts)
                .param("until", OffsetDateTime.ofInstant(now.plus(lock), ZoneOffset.UTC)).param("id", userId.value()).update();
    }

    @Override
    public void recordSuccess(UserId userId) {
        jdbc.sql("update platform_identity.emergency_admins set failed_attempts = 0, locked_until = null, updated_at = now() where user_id = :id")
                .param("id", userId.value()).update();
    }

    @Override
    public boolean acceptTotpStep(UserId userId, long step) {
        return jdbc.sql("""
                        update platform_identity.emergency_admins set last_totp_step = :step, updated_at = now()
                         where user_id = :id and last_totp_step < :step
                        """)
                .param("step", step).param("id", userId.value()).update() == 1;
    }
}
