package com.forcitis.one.platform.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Idempotency-Key is offered, and the answer stored for 24 hours, only for the POSTs listed in architecture/idempotent-endpoints.txt (ADR-0043 section 4,
 * DATA-REPAIR-timesheet-audit TB-02): an answer without a personal direct identifier and without a sensitive column, of bounded size. Any other
 * POST ignores the header and is not offered it in the contract.
 */
@Component
public class IdempotencyScope {
    static final String RESOURCE = "/architecture/idempotent-endpoints.txt";

    private record Endpoint(String method, PathPattern pattern) {
    }

    private final List<Endpoint> endpoints;

    @Autowired
    public IdempotencyScope() {
        this(listed());
    }

    /** Lines in the format of the list file: METHOD path-template, then an optional comment after '#'. */
    public IdempotencyScope(Collection<String> lines) {
        List<Endpoint> parsed = new ArrayList<>();
        for (String line : lines) {
            String entry = line.contains("#") ? line.substring(0, line.indexOf('#')).strip() : line.strip();
            if (entry.isEmpty()) {
                continue;
            }
            String[] parts = entry.split("\\s+");
            if (parts.length != 2) {
                throw new IllegalStateException("an idempotent endpoint is METHOD path: " + line);
            }
            parsed.add(new Endpoint(parts[0], PathPatternParser.defaultInstance.parse(parts[1])));
        }
        this.endpoints = List.copyOf(parsed);
    }

    /** A scope with more endpoints, for a test that needs a probe endpoint. */
    public IdempotencyScope plus(String... lines) {
        List<String> all = new ArrayList<>(endpoints.stream().map(e -> e.method() + " " + e.pattern().getPatternString()).toList());
        all.addAll(List.of(lines));
        return new IdempotencyScope(all);
    }

    public boolean applies(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        return endpoints.stream().anyMatch(e -> e.method().equals(method) && e.pattern().matches(container));
    }

    private static List<String> listed() {
        try (InputStream in = IdempotencyScope.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the class path: no endpoint could keep an answer");
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList();
        } catch (IOException unreadable) {
            throw new IllegalStateException(RESOURCE + " cannot be read", unreadable);
        }
    }
}
