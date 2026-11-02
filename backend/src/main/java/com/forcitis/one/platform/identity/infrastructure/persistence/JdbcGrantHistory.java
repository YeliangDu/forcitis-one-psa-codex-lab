package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.domain.GrantHistory;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Held at t = started at or before t and (still open or ended after t); a row that began and ended at one instant was never held. */
@Repository
class JdbcGrantHistory implements GrantHistory {

    private final JdbcClient jdbc;

    JdbcGrantHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<List<HeldRole>> heldAt(UserId user, Instant moment) {
        OffsetDateTime at = OffsetDateTime.ofInstant(moment, ZoneOffset.UTC);
        boolean recorded = jdbc.sql("""
                select exists (select 1 from platform_identity.account_role_grants g where g.user_id = :user and g.effective_from_at <= :at)
                """).param("user", user.value()).param("at", at).query(Boolean.class).single();
        if (!recorded) {
            return Optional.empty();
        }
        return Optional.of(jdbc.sql("""
                select r.code, v.role_name, v.access_scope_kind, v.permission_codes
                  from platform_identity.account_role_grants g
                  join platform_identity.roles r on r.role_id = g.role_id
                  join platform_identity.role_versions v on v.role_id = g.role_id
                   and v.effective_from_at <= :at and (v.%1$s or v.effective_to_at > :at)
                 where g.user_id = :user and g.effective_from_at <= :at and (g.%1$s or g.effective_to_at > :at)
                 order by r.code
                """.formatted(Validity.CURRENT)).param("user", user.value()).param("at", at).query(JdbcGrantHistory::held).list());
    }

    private static HeldRole held(ResultSet rs, int row) throws SQLException {
        return new HeldRole(rs.getString("code"), rs.getString("role_name"), rs.getString("access_scope_kind"), RoleRows.codes(rs, "permission_codes"));
    }
}
