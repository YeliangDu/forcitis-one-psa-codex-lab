package com.forcitis.one.platform.identity.infrastructure.persistence;

import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.TicketIssuer;
import com.forcitis.one.platform.identity.domain.TicketIssuerRepository;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTicketIssuerRepository implements TicketIssuerRepository {

    private final JdbcClient jdbc;

    JdbcTicketIssuerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TicketIssuer> findByKid(String kid) {
        return jdbc.sql("""
                        select issuer_code, signing_key_code, key_reference, is_enabled, allowed_role_codes
                          from platform_identity.ticket_issuers
                         where signing_key_code = :kid
                        """)
                .param("kid", kid)
                .query((rs, n) -> new TicketIssuer(rs.getString("issuer_code"), rs.getString("signing_key_code"), rs.getString("key_reference"),
                        rs.getBoolean("is_enabled"),
                        Arrays.stream((String[]) rs.getArray("allowed_role_codes").getArray()).map(RoleCode::new)
                                .collect(Collectors.toSet())))
                .optional();
    }
}
