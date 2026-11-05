package com.forcitis.one.fixture.externaltx.application;

import com.forcitis.one.fixture.externaltx.port.RemoteGateway;

public class CallsOutsideWithoutTransaction {
    private final RemoteGateway remote;

    public CallsOutsideWithoutTransaction(RemoteGateway remote) {
        this.remote = remote;
    }

    public String execute() {
        return remote.call();
    }
}
