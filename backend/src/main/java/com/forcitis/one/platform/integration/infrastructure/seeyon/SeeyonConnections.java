package com.forcitis.one.platform.integration.infrastructure.seeyon;

import org.springframework.jdbc.core.simple.JdbcClient;

/** Two pools to the same views: login lookups make a person wait, the snapshot and the start-up check do not. */
record SeeyonConnections(JdbcClient interactive, JdbcClient background) {
}
