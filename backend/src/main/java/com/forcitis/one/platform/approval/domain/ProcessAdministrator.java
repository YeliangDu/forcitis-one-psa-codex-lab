package com.forcitis.one.platform.approval.domain;

import com.forcitis.one.kernel.UserId;
import java.util.Optional;

public interface ProcessAdministrator {

    Optional<UserId> current();
}
