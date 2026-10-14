package com.forcitis.one.platform.identity.application;

/** The address is the one resolved through the trusted proxies only. */
public interface ClientOrigin {

    String address();

    String userAgent();
}
