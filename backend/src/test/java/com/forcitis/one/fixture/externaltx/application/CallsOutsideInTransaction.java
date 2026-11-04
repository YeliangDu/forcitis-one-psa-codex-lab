package com.forcitis.one.fixture.externaltx.application;

import com.forcitis.one.fixture.externaltx.port.RemoteGateway;
import org.springframework.transaction.annotation.Transactional;

public class CallsOutsideInTransaction {
    private final RemoteGateway remote;

    public CallsOutsideInTransaction(RemoteGateway remote) {
        this.remote = remote;
    }

    @Transactional
    public String execute() {
        return remote.call();
    }
}
