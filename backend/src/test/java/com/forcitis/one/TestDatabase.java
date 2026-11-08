package com.forcitis.one;

import org.springframework.test.context.DynamicPropertyRegistry;

/** A test class's own database on the fork's shared PostgreSQL (TestPostgres.database). */
public record TestDatabase(String url, String username, String password) {

    public TestDatabase withUrlParam(String name, String value) {
        return new TestDatabase(url + (url.contains("?") ? "&" : "?") + name + "=" + value, username, password);
    }

    public void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", this::url);
        registry.add("spring.datasource.username", this::username);
        registry.add("spring.datasource.password", this::password);
        registry.add("spring.flyway.url", this::url);
        registry.add("spring.flyway.user", this::username);
        registry.add("spring.flyway.password", this::password);
    }

    @Override
    public String toString() {
        return "TestDatabase[" + url + "]";
    }
}
