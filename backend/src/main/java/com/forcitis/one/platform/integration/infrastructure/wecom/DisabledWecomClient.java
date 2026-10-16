package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.WecomClient;
import java.util.Optional;

class DisabledWecomClient implements WecomClient {

    private static final String MESSAGE = "WeCom access is disabled (forcitis.platform.integration.wecom.enabled=false)";

    @Override
    public Optional<WecomUserId> userIdOfCode(String code, WecomEntry entry) {
        throw new ExternalSystemUnavailableException(MESSAGE);
    }

    @Override
    public Optional<String> sendCard(OutboundDelivery delivery) {
        throw new ExternalSystemUnavailableException(MESSAGE);
    }

    @Override
    public void replaceWithHandledNote(OutboundDelivery card, OutboundDelivery note) {
        throw new ExternalSystemUnavailableException(MESSAGE);
    }
}
