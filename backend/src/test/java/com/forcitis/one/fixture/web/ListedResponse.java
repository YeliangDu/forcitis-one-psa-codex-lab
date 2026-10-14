package com.forcitis.one.fixture.web;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record ListedResponse(String name, @Nullable String note, List<NestedPart> parts) {
    public record NestedPart(String label) {
    }
}
