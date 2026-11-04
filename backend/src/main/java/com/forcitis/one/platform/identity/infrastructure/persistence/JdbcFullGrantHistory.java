package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.domain.GrantHistory.RowPosition;
import com.forcitis.one.platform.identity.domain.GrantRow;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** The only statement that reads open and closed grant rows together (#1173); the grant predicate guard lists this file by name, so no other read may live here. */
@Component
class JdbcFullGrantHistory {

    private final JdbcClient jdbc;

    JdbcFullGrantHistory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<GrantRow> rowsOf(UserId user, @Nullable RowPosition after, int limit) {
        return jdbc.sql("""
                select g.account_role_grant_id, r.code, g.grant_channel_kind, g.effective_from_at, g.effective_to_at, g.revocation_cause_kind, g.granted_by, g.revoked_by
                  from platform_identity.account_role_grants g join platform_identity.roles r on r.role_id = g.role_id
                 where g.user_id = :user
                   and (cast(:afterFrom as timestamptz) is null or (g.effective_from_at, g.account_role_grant_id) < (cast(:afterFrom as timestamptz), cast(:afterId as uuid)))
                 order by g.effective_from_at desc, g.account_role_grant_id desc
                 limit :limit
                """).param("user", user.value())
                .param("afterFrom", after == null ? null : OffsetDateTime.ofInstant(after.effectiveFrom(), ZoneOffset.UTC), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("afterId", after == null ? null : after.grantId().value(), java.sql.Types.OTHER)
                .param("limit", limit).query(JdbcFullGrantHistory::row).list();
    }

    private static GrantRow row(ResultSet rs, int n) throws SQLException {
        OffsetDateTime to = rs.getObject("effective_to_at", OffsetDateTime.class);
        UUID grantedBy = rs.getObject("granted_by", UUID.class);
        UUID revokedBy = rs.getObject("revoked_by", UUID.class);
        return new GrantRow(new AccountRoleGrantId(rs.getObject("account_role_grant_id", UUID.class)), rs.getString("code"), rs.getString("grant_channel_kind"),
                rs.getObject("effective_from_at", OffsetDateTime.class).toInstant(), to == null ? null : to.toInstant(), rs.getString("revocation_cause_kind"),
                grantedBy == null ? null : new UserId(grantedBy), revokedBy == null ? null : new UserId(revokedBy));
    }
}
