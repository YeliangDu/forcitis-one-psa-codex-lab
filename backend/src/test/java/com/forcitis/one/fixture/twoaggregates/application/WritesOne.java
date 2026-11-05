package com.forcitis.one.fixture.twoaggregates.application;

import com.forcitis.one.fixture.twoaggregates.domain.AlphaRepository;
import com.forcitis.one.fixture.twoaggregates.domain.BetaRepository;
import org.springframework.transaction.annotation.Transactional;

public class WritesOne {
    private final AlphaRepository alphas;
    private final BetaRepository betas;

    public WritesOne(AlphaRepository alphas, BetaRepository betas) {
        this.alphas = alphas;
        this.betas = betas;
    }

    @Transactional
    public void execute() {
        alphas.save(betas.load());
    }
}
