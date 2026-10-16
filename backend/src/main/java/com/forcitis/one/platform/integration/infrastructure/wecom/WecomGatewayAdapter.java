package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.DeliverySubject;
import com.forcitis.one.platform.integration.api.WecomCard;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.api.WecomGateway;
import com.forcitis.one.platform.integration.application.MarkWecomCardHandled;
import com.forcitis.one.platform.integration.application.QueueWecomCard;
import com.forcitis.one.platform.integration.application.ResolveWecomUserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class WecomGatewayAdapter implements WecomGateway {

    private final ResolveWecomUserId resolve;
    private final QueueWecomCard queue;
    private final MarkWecomCardHandled markHandled;

    WecomGatewayAdapter(ResolveWecomUserId resolve, QueueWecomCard queue, MarkWecomCardHandled markHandled) {
        this.resolve = resolve;
        this.queue = queue;
        this.markHandled = markHandled;
    }

    @Override
    public Optional<WecomUserId> resolveWecomUserId(String code, WecomEntry entry) {
        return resolve.execute(code, entry);
    }

    @Override
    public void send(WecomCard card) {
        queue.execute(card);
    }

    @Override
    public void markHandled(DeliverySubject subject, String handledNote) {
        markHandled.execute(subject, handledNote);
    }
}
