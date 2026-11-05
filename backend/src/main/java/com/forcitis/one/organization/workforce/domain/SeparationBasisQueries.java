package com.forcitis.one.organization.workforce.domain;

import java.util.List;
import java.util.Optional;

/** Reads the separation basis list; the list has no writer in code, it changes by migration. */
public interface SeparationBasisQueries {

    Optional<SeparationBasis> find(String key);

    /** Every basis, retired ones included, ordered by key. */
    List<SeparationBasis> findAll();
}
