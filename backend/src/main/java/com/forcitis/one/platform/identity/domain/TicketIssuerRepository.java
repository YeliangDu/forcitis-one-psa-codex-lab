package com.forcitis.one.platform.identity.domain;

import java.util.Optional;

public interface TicketIssuerRepository {

    Optional<TicketIssuer> findByKid(String kid);
}
