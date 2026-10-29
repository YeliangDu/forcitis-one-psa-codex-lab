package com.forcitis.one.cost.timesheet.infrastructure.persistence;

import com.forcitis.one.cost.timesheet.domain.ConsumedEvents;
import com.forcitis.one.kernel.IdGenerator;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcConsumedEvents implements ConsumedEvents {

    private final JdbcClient jdbc;
    private final IdGenerator ids;

    JdbcConsumedEvents(JdbcClient jdbc, IdGenerator ids) {
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public boolean firstTime(UUID eventId, String consumerName) {
        return jdbc.sql("""
                insert into cost_timesheet.consumed_events (consumed_event_id, event_id, consumer_name) values (:id, :event, :consumer)
                on conflict (event_id, consumer_name) do nothing
                """)
                .param("id", ids.next()).param("event", eventId).param("consumer", consumerName).update() == 1;
    }
}
