/**
 * platform.integration — 权限流程与集成平台 BC: the shared WeCom client (access token cache, OAuth code exchange,
 * text-notice cards) and outbound delivery with retry / dead letter, and the anti-corruption layers to the other
 * external systems: the Seeyon organisation views and single-sign-on ticket check, and the old system's short-name view.
 * Callers see ports named after roles (OrgSource, OrgShortNames, SsoTicketVerifier) and external ids, never product
 * names or encodings. Schema platform_integration (also holds the event publication registry).
 */
package com.forcitis.one.platform.integration;
