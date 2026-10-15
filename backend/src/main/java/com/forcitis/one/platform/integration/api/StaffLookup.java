package com.forcitis.one.platform.integration.api;

import com.forcitis.one.kernel.WecomUserId;
import java.util.Objects;

/** The outside sign-in a person of the organisation source is looked up by; goes with DA-3 (ADR-0033 section 4). */
public sealed interface StaffLookup {

    record ByWecomUser(WecomUserId wecomUserId) implements StaffLookup {
        public ByWecomUser {
            Objects.requireNonNull(wecomUserId, "wecomUserId");
        }
    }

    record ByLoginName(String loginName) implements StaffLookup {
        public ByLoginName {
            Objects.requireNonNull(loginName, "loginName");
        }
    }
}
