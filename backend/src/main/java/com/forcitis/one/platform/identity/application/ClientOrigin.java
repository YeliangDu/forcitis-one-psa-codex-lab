package com.forcitis.one.platform.identity.application;

/** Port to where the current request came from. The address is the one resolved through the trusted proxies only. */
public interface ClientOrigin {

    String address();

    String userAgent();
}
