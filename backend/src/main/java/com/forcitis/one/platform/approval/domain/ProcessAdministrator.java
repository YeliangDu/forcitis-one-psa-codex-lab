package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Optional;

/** Receives what nobody could be found for; empty when the environment has not configured one. */
public interface ProcessAdministrator {

    Optional<UserId> current();
}
