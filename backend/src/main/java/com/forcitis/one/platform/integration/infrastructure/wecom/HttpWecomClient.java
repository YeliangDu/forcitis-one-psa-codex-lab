package com.forcitis.one.platform.integration.infrastructure.wecom;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.kernel.WecomUserId;
import com.forcitis.one.platform.integration.api.WecomEntry;
import com.forcitis.one.platform.integration.domain.OutboundDelivery;
import com.forcitis.one.platform.integration.domain.WecomClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import com.forcitis.one.kernel.WorkContext;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Token cached and renewed 5 minutes early; a token WeCom reports invalid is dropped and the call repeated once. */
class HttpWecomClient implements WecomClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWecomClient.class);
    static final Duration RENEW_BEFORE_EXPIRY = Duration.ofMinutes(5);
    private static final String TOKEN_KEY = "access-token";
    private static final int INVALID_CODE = 40029;
    private static final Set<Integer> TOKEN_REJECTED = Set.of(40014, 42001, 41001);

    private final WecomProperties settings;
    private final long agentId;
    private final HttpClient http;
    private final JsonMapper json;
    private final Cache<String, AccessToken> tokens;

    HttpWecomClient(WecomProperties settings, JsonMapper json, Clock clock) {
        this.settings = settings;
        this.agentId = Long.parseLong(settings.agentId());
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).followRedirects(HttpClient.Redirect.NEVER).build();
        Ticker ticker = () -> TimeUnit.MILLISECONDS.toNanos(clock.millis());
        this.tokens = Caffeine.newBuilder().maximumSize(1).ticker(ticker).expireAfter(new Expiry<String, AccessToken>() {
            @Override
            public long expireAfterCreate(String key, AccessToken token, long currentTime) {
                return token.cacheFor().toNanos();
            }

            @Override
            public long expireAfterUpdate(String key, AccessToken token, long currentTime, long currentDuration) {
                return token.cacheFor().toNanos();
            }

            @Override
            public long expireAfterRead(String key, AccessToken token, long currentTime, long currentDuration) {
                return currentDuration;
            }
        }).build();
    }

    @Override
    public Optional<WecomUserId> userIdOfCode(String code, WecomEntry entry) {
        Map<String, String> query = entry == WecomEntry.MINI_PROGRAM
                ? Map.of("js_code", code, "grant_type", "authorization_code")
                : Map.of("code", code);
        String path = entry == WecomEntry.MINI_PROGRAM ? "/cgi-bin/miniprogram/jscode2session" : "/cgi-bin/auth/getuserinfo";
        JsonNode answer = call(token -> get(path, token, query), INVALID_CODE);
        if (answer.path("errcode").asInt(0) == INVALID_CODE) {
            return Optional.empty();
        }
        String userId = answer.path("userid").asString(answer.path("UserId").asString(""));
        return userId.isBlank() ? Optional.empty() : Optional.of(new WecomUserId(userId));
    }

    @Override
    public Optional<String> sendCard(OutboundDelivery delivery) {
        Map<String, Object> body = WecomMessagePayload.textNotice(agentId, delivery.recipients(), delivery.title(),
                delivery.description(), delivery.targetUrl(), delivery.deliveryId().value());
        int[] duplicateTask = settings.delivery().duplicateTaskErrorCodes().stream().mapToInt(Integer::intValue).toArray();
        JsonNode answer = call(token -> post("/cgi-bin/message/send", token, body), duplicateTask);
        int errcode = answer.path("errcode").asInt(0);
        if (errcode != 0) {
            log.info("WeCom card {} was sent before (errcode {}): treated as sent", delivery.subject(), errcode);
            return Optional.empty();
        }
        String invalid = answer.path("invaliduser").asString("");
        if (!invalid.isBlank()) {
            log.warn("WeCom card {}: WeCom rejected {} recipient(s) as unknown", delivery.subject(), invalid.split("\\|").length);
        }
        String responseCode = answer.path("response_code").asString("");
        return responseCode.isBlank() ? Optional.empty() : Optional.of(responseCode);
    }

    @Override
    public void replaceWithHandledNote(OutboundDelivery card, OutboundDelivery note) {
        Map<String, Object> body = WecomMessagePayload.handledReplacement(agentId, card.recipients(), card.responseCode(),
                card.title(), note.description(), card.targetUrl(), note.deliveryId().value());
        call(token -> post("/cgi-bin/message/update_template_card", token, body));
    }

    private JsonNode call(Function<String, HttpRequest> request, int... tolerated) {
        for (int attempt = 0; ; attempt++) {
            JsonNode answer = execute(request.apply(accessToken()));
            int errcode = answer.path("errcode").asInt(0);
            if (errcode == 0) {
                return answer;
            }
            if (TOKEN_REJECTED.contains(errcode) && attempt == 0) {
                tokens.invalidate(TOKEN_KEY);
                continue;
            }
            for (int ok : tolerated) {
                if (errcode == ok) {
                    return answer;
                }
            }
            throw new ExternalSystemUnavailableException("WeCom answered errcode " + errcode + ": " + answer.path("errmsg").asString(""));
        }
    }

    private String accessToken() {
        return tokens.get(TOKEN_KEY, key -> requestToken()).value();
    }

    private AccessToken requestToken() {
        HttpRequest request = HttpRequest.newBuilder(uri("/cgi-bin/gettoken", Map.of("corpid", settings.corpId(), "corpsecret", settings.secret())))
                .timeout(settings.readTimeout()).GET().build();
        JsonNode answer = execute(request);
        int errcode = answer.path("errcode").asInt(0);
        String token = answer.path("access_token").asString("");
        if (errcode != 0 || token.isBlank()) {
            throw new ExternalSystemUnavailableException("WeCom refused the access token request, errcode " + errcode);
        }
        Duration cacheFor = Duration.ofSeconds(answer.path("expires_in").asLong(7200)).minus(RENEW_BEFORE_EXPIRY);
        return new AccessToken(token, cacheFor.isNegative() ? Duration.ZERO : cacheFor);
    }

    private HttpRequest get(String path, String token, Map<String, String> query) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("access_token", token);
        all.putAll(query);
        return HttpRequest.newBuilder(uri(path, all)).timeout(settings.readTimeout()).GET().build();
    }

    private HttpRequest post(String path, String token, Map<String, Object> body) {
        return HttpRequest.newBuilder(uri(path, Map.of("access_token", token))).timeout(settings.readTimeout())
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8)).build();
    }

    private URI uri(String path, Map<String, String> query) {
        StringBuilder out = new StringBuilder(settings.baseUrl().replaceAll("/+$", "")).append(path);
        char separator = '?';
        for (Map.Entry<String, String> parameter : query.entrySet()) {
            out.append(separator).append(parameter.getKey()).append('=').append(URLEncoder.encode(parameter.getValue(), StandardCharsets.UTF_8));
            separator = '&';
        }
        return URI.create(out.toString());
    }

    private JsonNode execute(HttpRequest original) {
        HttpRequest.Builder traced = HttpRequest.newBuilder(original, (name, value) -> true);
        WorkContext.correlationId().ifPresent(id -> traced.header(WorkContext.HEADER, id.toString()));
        HttpRequest request = traced.build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new ExternalSystemUnavailableException("WeCom answered HTTP " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalSystemUnavailableException("WeCom call interrupted");
        } catch (IOException | JacksonException failed) {
            throw new ExternalSystemUnavailableException("WeCom call failed: " + failed.getClass().getSimpleName());
        }
    }

    private record AccessToken(String value, Duration cacheFor) {
    }
}
