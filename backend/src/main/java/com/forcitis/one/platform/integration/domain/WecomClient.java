package com.forcitis.one.platform.integration.domain;

import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import java.util.Optional;

public interface WecomClient {

    Optional<WecomUserId> userIdOfCode(String code, WecomEntry entry);

    /** The response code that later allows the card to be replaced; empty when WeCom returned none. */
    Optional<String> sendCard(OutboundDelivery delivery);

    /** Replaces the sent card by the note, which is a delivery of its own and gives the replacement its task id. */
    void replaceWithHandledNote(OutboundDelivery card, OutboundDelivery note);
}
