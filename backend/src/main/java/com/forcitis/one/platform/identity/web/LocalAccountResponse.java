package com.forcitis.one.platform.identity.web;

import java.util.List;
import java.util.UUID;

/** Card #1478: one account the local test login offers: its id, the employee's name and the codes of the roles it holds now. */
record LocalAccountResponse(UUID userId, String name, List<String> roleCodes) {
}
