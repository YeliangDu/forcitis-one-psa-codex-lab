package com.forcitis.one.platform.integration.infrastructure.wecom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.WecomUserId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WecomMessagePayloadTest {

    static final java.util.UUID ID_42 = com.forcitis.one.TestIds.uuid(42);
    static final java.util.UUID ID_43 = com.forcitis.one.TestIds.uuid(43);
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final List<WecomUserId> RECIPIENTS = List.of(new WecomUserId("alice"), new WecomUserId("bob"));

    @Test
    @DisplayName("I-6 a card message carries touser only: no toparty, no totag, no @all")
    void cardMessageAddressesExactUsersOnly() {
        String body = JSON.writeValueAsString(WecomMessagePayload.textNotice(1000002, RECIPIENTS, "title", "description",
                "https://one.example.test/tasks/42", ID_42));

        assertThat(body).doesNotContainIgnoringCase("toparty").doesNotContainIgnoringCase("totag").doesNotContainIgnoringCase("@all");
        JsonNode card = JSON.readTree(body);
        assertThat(card.path("touser").asString()).isEqualTo("alice|bob");
        assertThat(card.path("msgtype").asString()).isEqualTo("template_card");
        assertThat(card.path("agentid").asLong()).isEqualTo(1000002);
    }

    @Test
    @DisplayName("I-6 the message to replace a card with a handled note addresses exact users only")
    void replacementAddressesExactUsersOnly() {
        String body = JSON.writeValueAsString(WecomMessagePayload.handledReplacement(1000002, RECIPIENTS, "response-code", "title",
                "handled by Alice", "https://one.example.test/tasks/42", ID_42));

        assertThat(body).doesNotContainIgnoringCase("toparty").doesNotContainIgnoringCase("totag").doesNotContainIgnoringCase("@all")
                .doesNotContainIgnoringCase("partyids").doesNotContainIgnoringCase("tagids").doesNotContainIgnoringCase("atall");
        assertThat(JSON.readTree(body).path("userids").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("I-6 a message without recipients cannot be built, so it can never fall back to a broadcast")
    void noRecipientNoMessage() {
        assertThatThrownBy(() -> WecomMessagePayload.textNotice(1, List.of(), "t", "d", "https://one.example.test/", ID_42))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WecomMessagePayload.handledReplacement(1, List.of(), "code", "t", "n", "https://one.example.test/", ID_42))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ARCH 3.7 the card is a text notice without buttons whose click opens the target URL")
    void cardIsATextNoticeWithoutButtons() {
        JsonNode card = JSON.valueToTree(WecomMessagePayload.textNotice(1, RECIPIENTS, "title", "description",
                "https://one.example.test/tasks/42", ID_42)).path("template_card");

        assertThat(card.path("card_type").asString()).isEqualTo("text_notice");
        assertThat(card.path("card_action").path("type").asInt()).isEqualTo(1);
        assertThat(card.path("card_action").path("url").asString()).isEqualTo("https://one.example.test/tasks/42");
        assertThat(card.has("button_list")).isFalse();
        assertThat(card.has("button_selection")).isFalse();
    }

    @Test
    @DisplayName("the task id uses only letters, digits and dashes, is stable, and differs per delivery")
    void taskIdIsSafeAndDeterministic() {
        String id = WecomMessagePayload.taskId(ID_42);

        assertThat(id).matches("one-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").isEqualTo(WecomMessagePayload.taskId(ID_42))
                .isNotEqualTo(WecomMessagePayload.taskId(ID_43));
    }

    @Test
    @DisplayName("long titles and descriptions are cut to what a card can show")
    void textIsCut() {
        JsonNode card = JSON.valueToTree(WecomMessagePayload.textNotice(1, RECIPIENTS, "t".repeat(500), "d".repeat(500),
                "https://one.example.test/", ID_42)).path("template_card").path("main_title");

        assertThat(card.path("title").asString()).hasSize(WecomMessagePayload.MAX_TITLE_CHARS);
        assertThat(card.path("desc").asString()).hasSize(WecomMessagePayload.MAX_DESCRIPTION_CHARS);
    }
}
