package com.forcitis.one.fixture.twoaggregates.application;

import com.forcitis.one.fixture.twoaggregates.domain.AlphaRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

public class WritesViaHelper {
    private final AlphaRepository alphas;
    private final Helper helper;

    public WritesViaHelper(AlphaRepository alphas, Helper helper) {
        this.alphas = alphas;
        this.helper = helper;
    }

    @Transactional
    public void execute() {
        alphas.save("a");
        helper.store(List.of("b"));
    }
}
