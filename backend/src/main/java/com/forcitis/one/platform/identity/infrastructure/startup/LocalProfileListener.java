package com.forcitis.one.platform.identity.infrastructure.startup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Card #1478: the profile local (a developer's own machine, with the test login of LoginAsLocalTestUser) refuses to start next to anything that looks like
 * a shared environment: the profiles dev or prod, or a database that is not on this machine. Runs before any bean exists and before the listeners that end the
 * process (migrate, read-only verification), so `migrate,local` is checked too. The refusal names the setting, never its value.
 */
final class LocalProfileListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {
    static final String PROFILE = "local";
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]");
    /** jdbc:postgresql://host[:port]/...: one host only (a comma list or a host= parameter would name another machine). */
    private static final Pattern URL = Pattern.compile("jdbc:postgresql://([^/:?,]+|\\[[0-9a-fA-F:]+\\])(?::\\d+)?(?:[/?].*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_PARAMETER = Pattern.compile("[?&;](pg)?host(addr)?=", Pattern.CASE_INSENSITIVE);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 200;
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        List<String> problems = problems(event.getEnvironment());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("The profile local refuses to start (card #1478): " + String.join("; ", problems));
        }
    }

    /** Empty when the profile local is not active, or when nothing in the environment looks like a shared one. */
    static List<String> problems(ConfigurableEnvironment environment) {
        Set<String> active = Set.of(environment.getActiveProfiles());
        Set<String> effective = active.isEmpty() ? Set.of(environment.getDefaultProfiles()) : active;
        List<String> problems = new ArrayList<>();
        if (!effective.contains(PROFILE)) {
            return problems;
        }
        for (String shared : List.of("dev", "prod")) {
            if (effective.contains(shared)) {
                problems.add("the profile " + shared + " is active too");
            }
        }
        for (String key : List.of("spring.datasource.url", "spring.flyway.url")) {
            String url = environment.getProperty(key);
            if (url == null || url.isBlank()) {
                if (key.equals("spring.datasource.url")) {
                    problems.add(key + " is not set");
                }
            } else if (!onThisMachine(url)) {
                problems.add(key + " does not name a database on this machine (localhost, 127.0.0.1 or [::1], one host, no host parameter)");
            }
        }
        return problems;
    }

    static boolean onThisMachine(String url) {
        Matcher matcher = URL.matcher(url.strip());
        return matcher.matches() && LOOPBACK.contains(matcher.group(1).toLowerCase()) && !HOST_PARAMETER.matcher(url).find();
    }
}
