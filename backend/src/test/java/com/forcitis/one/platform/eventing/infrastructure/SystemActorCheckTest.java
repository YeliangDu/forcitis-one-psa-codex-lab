package com.forcitis.one.platform.eventing.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.SystemActorName;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SystemActorCheckTest {
    static class DeclaredJob {
        @SchedulerLock(name = "test.job")
        public void run() {
        }
    }

    static class UndeclaredJob {
        @SchedulerLock(name = "test.no-such-job")
        public void run() {
        }
    }

    @SystemActorName("test.no-such-listener")
    static class UndeclaredListener {
    }

    @SystemActorName("test.flaky-listener")
    static class DeclaredListener {
    }

    @Test
    @DisplayName("P2 recheck M-2 the start fails, naming the class, when a job lock name or a listener's @SystemActorName is not declared")
    void undeclaredNamesStopTheStart() {
        assertThatCode(() -> SystemActorCheck.check(List.of(DeclaredJob.class, DeclaredListener.class, String.class))).doesNotThrowAnyException();
        assertThatThrownBy(() -> SystemActorCheck.check(List.of(DeclaredJob.class, UndeclaredJob.class))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.no-such-job").hasMessageContaining("UndeclaredJob");
        assertThatThrownBy(() -> SystemActorCheck.check(List.of(UndeclaredListener.class))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.no-such-listener").hasMessageContaining("UndeclaredListener");
    }
}
