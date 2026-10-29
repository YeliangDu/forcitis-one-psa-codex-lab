package com.forcitis.one.fixture.web;

import com.forcitis.one.kernel.CursorPage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handlers for ControllerResponseGuardTest, in a web package as real answers are. Not a @RestController, so component scanning of
 * the Spring tests never registers them; the guard test takes the classes directly. Only ControllerResponseGuardTest loads it.
 */
public final class ControllerAnswerSamples {
    private ControllerAnswerSamples() {
    }

    public record SampleResponse(String value) {
    }

    public record OtherResponse(String value) {
    }

    public record SampleView(String value) {
    }

    /** Allowed: sealed, named *Response, every permitted type an allowed answer. */
    public sealed interface ChangeResponse permits ChangedSampleResponse, ChangedOtherResponse {
    }

    public record ChangedSampleResponse(String value) implements ChangeResponse {
    }

    public record ChangedOtherResponse(String value) implements ChangeResponse {
    }

    /** Not allowed: one permitted type is a view. */
    public sealed interface LeakyResponse permits LeakySampleResponse, LeakedView {
    }

    public record LeakySampleResponse(String value) implements LeakyResponse {
    }

    public record LeakedView(String value) implements LeakyResponse {
    }

    /** Not allowed: an interface named *Response that is not sealed can be anything. */
    public interface OpenResponse {
    }

    public static class BaseController {
        @GetMapping("/inherited")
        public SampleView inherited() {
            return new SampleView("i");
        }
    }

    public static class SampleController extends BaseController {
        @GetMapping("/a")
        SampleView view() {
            return new SampleView("a");
        }

        @GetMapping("/b")
        ResponseEntity<CursorPage<SampleResponse>> page() {
            return ResponseEntity.ok(null);
        }

        @GetMapping("/c")
        ResponseEntity<?> anything() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/d")
        void nothing() {
        }

        @GetMapping("/e")
        ResponseEntity<ChangeResponse> sealedChange() {
            return ResponseEntity.ok(new ChangedSampleResponse("e"));
        }

        @GetMapping("/f")
        LeakyResponse leaky() {
            return new LeakySampleResponse("f");
        }

        @GetMapping("/g")
        OpenResponse open() {
            return null;
        }

        String notAHandler() {
            return "x";
        }
    }
}
