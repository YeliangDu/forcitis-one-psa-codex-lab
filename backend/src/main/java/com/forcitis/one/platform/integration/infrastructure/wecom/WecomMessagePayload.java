package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.kernel.WecomUserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Request bodies for WeCom. Recipients are always exact user ids; no body can address a department, a tag or everyone (I-6). */
final class WecomMessagePayload {

    static final int MAX_TITLE_CHARS = 40;
    static final int MAX_DESCRIPTION_CHARS = 160;

    private WecomMessagePayload() {
    }

    static Map<String, Object> textNotice(long agentId, List<WecomUserId> recipients, String title, String description,
                                          String targetUrl, String deduplicationKey) {
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("a WeCom message needs at least one recipient");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", recipients.stream().map(WecomUserId::value).collect(Collectors.joining("|")));
        body.put("msgtype", "template_card");
        body.put("agentid", agentId);
        body.put("template_card", card(title, description, targetUrl, deduplicationKey));
        return body;
    }

    static Map<String, Object> handledReplacement(long agentId, List<WecomUserId> recipients, String responseCode, String title,
                                                  String handledNote, String targetUrl, String deduplicationKey) {
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("a WeCom card update needs at least one recipient");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userids", recipients.stream().map(WecomUserId::value).toList());
        body.put("agentid", agentId);
        body.put("response_code", responseCode);
        body.put("template_card", card(title, handledNote, targetUrl, deduplicationKey));
        return body;
    }

    /** Letters, digits and "-" only, so any deduplication key is a valid WeCom task id; the hash keeps distinct keys distinct. */
    static String taskId(String deduplicationKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(deduplicationKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("one-");
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<String, Object> card(String title, String description, String targetUrl, String deduplicationKey) {
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
        card.put("task_id", taskId(deduplicationKey));
        return card;
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
