package com.forcitis.one.platform.identity.infrastructure.seeyon;

import com.forcitis.one.platform.identity.application.SeeyonSettings;
import com.forcitis.one.platform.identity.application.SeeyonTicketVerifier;
import com.forcitis.one.platform.identity.application.SeeyonVerdict;
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

/**
 * Anti-corruption layer to the Seeyon OA ticket check (RULE-320/321): GET {baseUrl}/seeyon/thirdpartyController.do?ticket=...
 * answers 200 text/plain with the URL-encoded login name, or an empty body / an HTML login page for a ticket it does not know.
 * Connect and read time out after 5 s (configurable); redirects are not followed; a non-200 answer means OA is unavailable.
 * The login name is accepted only if it is non-blank, at most 64 characters, and free of '&lt;', '&gt;', blanks, CR and LF.
 * Only identity code may use this class (SECURITY_INVARIANTS, REIMAGINED_ARCHITECTURE §3.3). The ticket is never logged.
 */
@Component
class SeeyonOaTicketVerifier implements SeeyonTicketVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(SeeyonOaTicketVerifier.class);
    static final String VALIDATE_PATH = "/seeyon/thirdpartyController.do";
    private static final int MAX_LOGIN_NAME = 64;
    private static final int MAX_BODY_BYTES = 4096;

    private final SeeyonSettings settings;
    private final HttpClient client;

    SeeyonOaTicketVerifier(SeeyonSettings settings) {
        this.settings = settings;
        this.client = HttpClient.newBuilder().connectTimeout(settings.timeout()).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public SeeyonVerdict verify(String ticket) {
        URI uri = target(ticket);
        if (uri == null) {
            return new SeeyonVerdict.Unavailable();
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(settings.timeout()).GET().build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    LOG.warn("Seeyon OA answered HTTP {} to a ticket check", response.statusCode());
                    return new SeeyonVerdict.Unavailable();
                }
                return interpret(new String(body.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SeeyonVerdict.Unavailable();
        } catch (IOException e) {
            LOG.warn("Seeyon OA ticket check failed: {}", e.getClass().getSimpleName());
            return new SeeyonVerdict.Unavailable();
        }
    }

    static SeeyonVerdict interpret(String body) {
        String decoded = URLDecoder.decode(body.strip(), StandardCharsets.UTF_8);
        if (decoded.isBlank() || decoded.length() > MAX_LOGIN_NAME
                || decoded.chars().anyMatch(c -> c == '<' || c == '>' || c == ' ' || c == '\r' || c == '\n' || Character.isISOControl(c))) {
            return new SeeyonVerdict.Invalid();
        }
        return new SeeyonVerdict.LoginName(decoded);
    }

    private URI target(String ticket) {
        String base = settings.baseUrl();
        if (base == null || base.isBlank() || !(base.startsWith("https://") || base.startsWith("http://"))) {
            LOG.warn("Seeyon SSO is enabled but SEEYON_OA_BASE_URL is not a usable http(s) URL");
            return null;
        }
        String root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return URI.create(root + VALIDATE_PATH + "?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8));
    }
}
