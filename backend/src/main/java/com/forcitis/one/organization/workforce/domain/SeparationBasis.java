package com.forcitis.one.organization.workforce.domain;

/** One row of the selection list of separation bases; rows change by migration only. A system-only basis is never picked by HR. */
public record SeparationBasis(String key, String displayName, SeparationInitiator defaultInitiator, boolean employerInitiated, boolean systemOnly, boolean active) {
}
