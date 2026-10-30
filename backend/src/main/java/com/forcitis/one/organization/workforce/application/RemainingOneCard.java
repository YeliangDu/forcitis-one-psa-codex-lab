package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The WeCom ids of the usable administrators a notice at one remaining reaches: each once, without the held, and only those who have a WeCom user id. */
final class RemainingOneCard {
    private RemainingOneCard() {
    }

    static List<WecomUserId> recipients(OrgDirectory directory, Set<EmployeeId> usable, Set<EmployeeId> held) {
        List<WecomUserId> recipients = new ArrayList<>();
        for (EmployeeId person : usable) {
            if (held.contains(person)) {
                continue;
            }
            directory.findEmployee(person).map(profile -> profile.wecomUserId()).ifPresent(recipients::add);
        }
        return recipients;
    }
}
