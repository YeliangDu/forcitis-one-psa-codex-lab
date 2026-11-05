package com.forcitis.one.fixture.twoaggregates.application;

import com.forcitis.one.fixture.twoaggregates.domain.BetaRepository;
import java.util.List;

/** Not transactional itself: runs in the caller's transaction. */
public class Helper {
    private final BetaRepository betas;

    public Helper(BetaRepository betas) {
        this.betas = betas;
    }

    public void store(List<String> values) {
        values.forEach(betas::save);
    }
}
