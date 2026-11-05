package com.forcitis.one.fixture.externaltx.application;

import com.forcitis.one.fixture.externaltx.port.RemoteGateway;

/** Not transactional itself: runs in the caller's transaction. */
public class RemoteHelper {
    private final RemoteGateway remote;

    public RemoteHelper(RemoteGateway remote) {
        this.remote = remote;
    }

    public String fetch() {
        return remote.call();
    }
}
