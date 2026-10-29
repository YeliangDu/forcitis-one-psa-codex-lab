package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.EmergencyAdmin;
import com.forcitis.one.platform.identity.domain.EmergencyAdminRepository;
import com.forcitis.one.platform.identity.domain.EmergencyAdminStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEmergencyAdminRepository implements EmergencyAdminRepository {

    private static final String COLUMNS = """
            user_id, account_name, password_hash, totp_secret_encrypted, last_totp_step, failed_attempts, locked_until, is_enabled,
            version, event_count
            """;

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcEmergencyAdminRepository(JdbcClient jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EmergencyAdmin> findByLoginName(String loginName) {
        return jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.emergency_admins where account_name = :name
                """)
                .param("name", loginName).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public Optional<EmergencyAdmin> findByUserId(UserId userId) {
        return jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.emergency_admins where user_id = :id
                """)
                .param("id", userId.value()).query((rs, n) -> row(rs)).optional();
    }

    @Override
    public List<EmergencyAdmin> findAll() {
        return jdbc.sql("""
                select
                """ + COLUMNS + """
                 from platform_identity.emergency_admins order by created_at, user_id
                """).query((rs, n) -> row(rs)).list();
    }

    private static EmergencyAdmin row(ResultSet rs) throws SQLException {
        OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
        return EmergencyAdmin.restore(new UserId(rs.getObject("user_id", UUID.class)), rs.getString("account_name"), rs.getString("password_hash"),
                rs.getString("totp_secret_encrypted"), rs.getLong("last_totp_step"), rs.getInt("failed_attempts"),
                lockedUntil == null ? null : lockedUntil.toInstant(), rs.getBoolean("is_enabled"), rs.getLong("version"),
                rs.getLong("event_count"));
    }

    @Override
    public boolean insertIfAbsent(EmergencyAdmin registered) {
        return jdbc.sql("""
                        insert into platform_identity.emergency_admins (user_id, account_name, password_hash, totp_secret_encrypted,
                                                                         is_enabled, version, event_count)
                        values (:id, :name, :hash, :secret, :enabled, :version, :eventSequence)
                        on conflict do nothing
                        """)
                .param("id", registered.userId().value()).param("name", registered.loginName())
                .param("hash", registered.passwordHash()).param("secret", registered.totpSecretEncrypted())
                .param("enabled", registered.enabled()).param("version", registered.versionAfterSave())
                .param("eventSequence", registered.eventSequenceAfterSave()).update() == 1;
    }

    @Override
    public void save(EmergencyAdmin admin) {
        int updated = jdbc.sql("""
                        update platform_identity.emergency_admins
                           set is_enabled = :enabled, password_hash = :hash, totp_secret_encrypted = :secret,
                               last_totp_step = case when :credentialsChanged then 0 else last_totp_step end,
                               failed_attempts = case when :credentialsChanged then 0 else failed_attempts end,
                               locked_until = case when :credentialsChanged then null else locked_until end,
                               updated_at = :updatedAt, version = :next, event_count = :eventSequence
                         where user_id = :id and version = :version and is_enabled = :loadedEnabled
                        """)
                .param("enabled", admin.enabled()).param("hash", admin.passwordHash()).param("secret", admin.totpSecretEncrypted())
                .param("credentialsChanged", admin.credentialsChanged()).param("next", admin.versionAfterSave())
                .param("eventSequence", admin.eventSequenceAfterSave()).param("id", admin.userId().value())
                .param("version", admin.version()).param("loadedEnabled", admin.loadedStatus() == EmergencyAdminStatus.ENABLED)
                .param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        if (updated != 1) {
            throw new DomainException(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE, ErrorKind.CONFLICT,
                    "the emergency account of " + admin.userId().value() + " was changed by someone else");
        }
    }

    @Override
    public boolean reserveAttempt(UserId userId, Instant now, int maxAttempts, Duration lock) {
        String newCount = "case when locked_until is not null and locked_until <= :now then 1 else failed_attempts + 1 end";
        return jdbc.sql("""
                        update platform_identity.emergency_admins
                           set failed_attempts = %s,
                               locked_until = case when (%s) >= :max then :until else null end,
                               updated_at = :updatedAt
                         where user_id = :id and (locked_until is null or locked_until <= :now)
                        """.formatted(newCount, newCount))
                .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).param("max", maxAttempts)
                .param("until", OffsetDateTime.ofInstant(now.plus(lock), ZoneOffset.UTC)).param("id", userId.value()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update() == 1;
    }

    @Override
    public void recordSuccess(UserId userId) {
        jdbc.sql("""
                update platform_identity.emergency_admins set failed_attempts = 0, locked_until = null, updated_at = :updatedAt where user_id = :id
                """)
                .param("id", userId.value()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public boolean acceptTotpStep(UserId userId, long step) {
        return jdbc.sql("""
                        update platform_identity.emergency_admins set last_totp_step = :step, updated_at = :updatedAt
                         where user_id = :id and last_totp_step < :step
                        """)
                .param("step", step).param("id", userId.value()).param("updatedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update() == 1;
    }
}
