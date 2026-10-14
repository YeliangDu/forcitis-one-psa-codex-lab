package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import com.forcitis.one.cost.timesheet.domain.ConsumedEvents;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcConsumedEvents implements ConsumedEvents {

    private final JdbcClient jdbc;

    JdbcConsumedEvents(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean firstTime(UUID eventId, String consumerName) {
        return jdbc.sql("""
                INSERT INTO cost_timesheet.consumed_events (event_id, consumer_name) VALUES (:event, :consumer)
                ON CONFLICT (event_id, consumer_name) DO NOTHING
                """)
                .param("event", eventId).param("consumer", consumerName).update() == 1;
    }
}
