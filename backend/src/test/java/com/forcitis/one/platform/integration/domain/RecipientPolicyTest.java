package com.forcitis.one.platform.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecipientPolicyTest {

    static final WecomUserId ALICE = new WecomUserId("alice");
    static final WecomUserId BOB = new WecomUserId("bob");

    @Test
    @DisplayName("RULE-375 outside production only the allow-listed recipients are kept; the others are reported as skipped")
    void allowListKeepsOnlyListedRecipients() {
        RecipientPolicy.Decision decision = new RecipientPolicy(true, Set.of(ALICE)).decide(List.of(ALICE, BOB));

        assertThat(decision.allowed()).containsExactly(ALICE);
        assertThat(decision.skipped()).containsExactly(BOB);
    }

    @Test
    @DisplayName("RULE-375 an empty allow list lets nobody through")
    void emptyAllowListSkipsEverybody() {
        RecipientPolicy.Decision decision = new RecipientPolicy(true, Set.of()).decide(List.of(ALICE, BOB));

        assertThat(decision.allowed()).isEmpty();
        assertThat(decision.skipped()).containsExactly(ALICE, BOB);
    }

    @Test
    @DisplayName("RULE-375 production (not restricted) keeps every recipient")
    void productionKeepsEverybody() {
        RecipientPolicy.Decision decision = new RecipientPolicy(false, Set.of()).decide(List.of(ALICE, BOB));

        assertThat(decision.allowed()).containsExactly(ALICE, BOB);
        assertThat(decision.skipped()).isEmpty();
    }
}
