package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.LocalDueJobs;
import com.forcitis.one.kernel.PublicEndpoint;
import com.forcitis.one.kernel.SettableClock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Card #1478: set, read and reset the clock of a developer's own machine. Exists only in the profile local; elsewhere the paths are not mapped. */
@RestController
@Profile("local")
@RequestMapping("/api/v1/platform")
class LocalClockController {

    private final SettableClock clock;
    private final List<LocalDueJobs> dueJobs;

    LocalClockController(SettableClock clock, List<LocalDueJobs> dueJobs) {
        this.clock = clock;
        this.dueJobs = dueJobs;
    }

    @GetMapping("/local-clocks")
    @PublicEndpoint(reason = "Profile local only (card #1478): reads the settable clock of this machine")
    LocalClockResponse read() {
        return new LocalClockResponse(clock.instant(), clock.isSet());
    }

    @PostMapping("/local-clocks:set")
    @PublicEndpoint(reason = "Profile local only (card #1478): sets the clock of this machine to a moment, so a golden date can be replayed as today, and runs the daily jobs once for it")
    LocalClockResponse set(@RequestBody LocalClockRequest request) {
        clock.set(request.at());
        dueJobs.forEach(LocalDueJobs::runOnce);
        return read();
    }

    @PostMapping("/local-clocks:reset")
    @PublicEndpoint(reason = "Profile local only (card #1478): puts the clock of this machine back to the real time")
    LocalClockResponse reset() {
        clock.reset();
        return read();
    }
}
