package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.kernel.WecomUserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Recipients are always exact user ids; no body can address a department, a tag or everyone (I-6). */
final class WecomMessagePayload {

    static final int MAX_TITLE_CHARS = 40;
    static final int MAX_DESCRIPTION_CHARS = 160;

    private WecomMessagePayload() {
    }

    static Map<String, Object> textNotice(long agentId, List<WecomUserId> recipients, String title, String description,
                                          String targetUrl, UUID deliveryId) {
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("a WeCom message needs at least one recipient");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", recipients.stream().map(WecomUserId::value).collect(Collectors.joining("|")));
        body.put("msgtype", "template_card");
        body.put("agentid", agentId);
        body.put("template_card", card(title, description, targetUrl, deliveryId));
        return body;
    }

    static Map<String, Object> handledReplacement(long agentId, List<WecomUserId> recipients, String responseCode, String title,
                                                  String handledNote, String targetUrl, UUID deliveryId) {
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("a WeCom card update needs at least one recipient");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userids", recipients.stream().map(WecomUserId::value).toList());
        body.put("agentid", agentId);
        body.put("response_code", responseCode);
        body.put("template_card", card(title, handledNote, targetUrl, deliveryId));
        return body;
    }

    /** Letters, digits and "-" only; a delivery keeps its id through every retry, and each delivery (a card, the note that replaces it) has its own. */
    static String taskId(UUID deliveryId) {
        return "one-" + deliveryId;
    }

    private static Map<String, Object> card(String title, String description, String targetUrl, UUID deliveryId) {
        Map<String, Object> mainTitle = new LinkedHashMap<>();
        mainTitle.put("title", cut(title, MAX_TITLE_CHARS));
        if (description != null && !description.isBlank()) {
            mainTitle.put("desc", cut(description, MAX_DESCRIPTION_CHARS));
        }
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", 1);
        action.put("url", targetUrl);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("card_type", "text_notice");
        card.put("main_title", mainTitle);
        card.put("card_action", action);
        card.put("task_id", taskId(deliveryId));
        return card;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
