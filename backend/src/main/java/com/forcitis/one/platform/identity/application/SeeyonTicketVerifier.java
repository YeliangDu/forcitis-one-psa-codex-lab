package com.forcitis.one.platform.identity.application;

/** Port to the Seeyon OA ticket check (server to server; the HTTP client lives in infrastructure.seeyon). */
public interface SeeyonTicketVerifier {

    SeeyonVerdict verify(String ticket);
}
