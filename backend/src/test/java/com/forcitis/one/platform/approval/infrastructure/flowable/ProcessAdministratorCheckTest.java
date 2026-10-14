package com.forcitis.one.platform.approval.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.approval.domain.ProcessAdministrator;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessAdministratorCheckTest {

    static final ProcessAdministrator NOBODY = Set::of;
    static final ProcessAdministrator SOMEBODY = () -> Set.of(new UserId(1));

    @Test
    @DisplayName("G-1 D-1a-61 the main configuration switches the check on, and a test configuration is the only place that switches it off")
    void theMainConfigurationRequiresAnAdministrator() throws IOException {
        Properties main = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/config/platform_approval.properties"))) {
            main.load(in);
        }
        assertThat(main.getProperty("forcitis.platform.approval.process-administrator-required")).isEqualTo("true");
        Properties test = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/test/resources/application-default.properties"))) {
            test.load(in);
        }
        assertThat(test.getProperty("forcitis.platform.approval.process-administrator-required")).isEqualTo("false");
    }

    @Test
    @DisplayName("G-1 with the check on (the default) startup fails when nobody holds the role, in any environment; with a holder it passes")
    void startupFailsWithoutAnAdministrator() {
        AssignmentProperties properties = new AssignmentProperties("process_administrator");

        assertThatThrownBy(() -> new ProcessAdministratorCheck(NOBODY, properties, true).verify())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("process_administrator");
        assertThatCode(() -> new ProcessAdministratorCheck(SOMEBODY, properties, true).verify()).doesNotThrowAnyException();
        assertThatCode(() -> new ProcessAdministratorCheck(NOBODY, properties, false).verify()).as("a test configuration may switch it off").doesNotThrowAnyException();
    }
}
