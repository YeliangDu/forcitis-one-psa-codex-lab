/**
 * platform.integration — 权限流程与集成平台 BC: the shared WeCom client (access token cache, OAuth code exchange,
 * text-notice cards) and outbound delivery with retry / dead letter. Knows external ids only, never PSA users.
 * Schema platform_integration. Other external systems' anti-corruption layers live in the module that uses them.
 */
package com.forcitis.one.platform.integration;
