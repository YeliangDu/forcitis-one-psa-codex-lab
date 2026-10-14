package com.forcitis.one.platform.integration.application;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.domain.WecomClient;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResolveWecomUserId {

    private static final Logger log = LoggerFactory.getLogger(ResolveWecomUserId.class);

    private final WecomClient wecom;

    ResolveWecomUserId(WecomClient wecom) {
        this.wecom = wecom;
    }

    public Optional<WecomUserId> execute(String code, WecomEntry entry) {
        if (code == null || code.isBlank() || entry == null) {
            return Optional.empty();
        }
        try {
            return wecom.userIdOfCode(code, entry);
        } catch (ExternalSystemUnavailableException unavailable) {
            log.warn("WeCom code exchange for {} failed: {}", entry, unavailable.getMessage());
            return Optional.empty();
        }
    }
}
