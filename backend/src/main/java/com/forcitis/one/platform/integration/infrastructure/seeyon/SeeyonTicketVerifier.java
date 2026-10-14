package com.forcitis.one.platform.integration.infrastructure.seeyon;

import com.forcitis.one.platform.integration.api.SsoTicketVerifier;
import com.forcitis.one.platform.integration.api.SsoVerdict;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 200 text/plain with the URL-encoded login name; empty body or HTML login page means unknown ticket; non-200 means OA unavailable; ticket never logged. */
@Component
class SeeyonTicketVerifier implements SsoTicketVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(SeeyonTicketVerifier.class);
    static final String VALIDATE_PATH = "/seeyon/thirdpartyController.do";
    private static final int MAX_LOGIN_NAME = 64;
    private static final int MAX_BODY_BYTES = 4096;

    private final SeeyonProperties.Sso settings;
    private final HttpClient client;

    SeeyonTicketVerifier(SeeyonProperties properties) {
        this.settings = properties.sso();
        this.client = HttpClient.newBuilder().connectTimeout(settings.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public SsoVerdict verify(String ticket) {
        URI uri = target(ticket);
        if (uri == null) {
            return new SsoVerdict.Unavailable();
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(settings.timeout()).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    LOG.warn("Seeyon OA answered HTTP {} to a ticket check", response.statusCode());
                    return new SsoVerdict.Unavailable();
                }
                return interpret(new String(body.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SsoVerdict.Unavailable();
        } catch (IOException e) {
            LOG.warn("Seeyon OA ticket check failed: {}", e.getClass().getSimpleName());
            return new SsoVerdict.Unavailable();
        }
    }

    static SsoVerdict interpret(String body) {
        String decoded = URLDecoder.decode(body.strip(), StandardCharsets.UTF_8);
        if (decoded.isBlank() || decoded.length() > MAX_LOGIN_NAME
                || decoded.chars().anyMatch(c -> c == '<' || c == '>' || c == ' ' || c == '\r' || c == '\n' || Character.isISOControl(c))) {
            return new SsoVerdict.Invalid();
        }
        return new SsoVerdict.LoginName(decoded);
    }

    private URI target(String ticket) {
        String base = settings.baseUrl();
        if (base == null || base.isBlank() || !(base.startsWith("https://") || base.startsWith("http://"))) {
            LOG.warn("the portal base URL (forcitis.platform.integration.seeyon.sso.base-url) is not a usable http(s) URL");
            return null;
        }
        String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(root + VALIDATE_PATH + "?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
    }
}
