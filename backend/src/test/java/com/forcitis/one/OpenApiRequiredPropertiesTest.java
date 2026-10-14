package com.forcitis.one;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.fixture.OutsideWeb;
import com.forcitis.one.fixture.web.ListedResponse;
import com.forcitis.one.fixture.web.ReadsLikeARequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.ResponseEntity;

class OpenApiRequiredPropertiesTest {

    @Test
    @DisplayName("only records in a web package that a controller returns are collected, wrapped or nested, whatever their name")
    void collectsReturnedWebRecords() {
        var found = OpenApiRequiredProperties.responseRecords(List.of(
                ResolvableType.forClassWithGenerics(ResponseEntity.class, ListedResponse.class),
                ResolvableType.forClassWithGenerics(Optional.class, ReadsLikeARequest.class),
                ResolvableType.forClass(OutsideWeb.class)));

        assertThat(found).containsOnlyKeys("ListedResponse", "NestedPart", "ReadsLikeARequest");
    }

    @Test
    @DisplayName("two returned web records with the same simple name stop the application")
    void duplicateSimpleNamesFail() {
        assertThatThrownBy(() -> OpenApiRequiredProperties.responseRecords(List.of(
                ResolvableType.forClass(com.forcitis.one.fixture.web.SharedName.class),
                ResolvableType.forClass(com.forcitis.one.fixture.other.web.SharedName.class))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SharedName");
    }
}
