package com.forcitis.one.fixture.externaltx.application;

import org.springframework.transaction.annotation.Transactional;

public class CallsViaHelper {
    private final RemoteHelper helper;

    public CallsViaHelper(RemoteHelper helper) {
        this.helper = helper;
    }

    @Transactional
    public String execute() {
        return helper.fetch();
    }
}
