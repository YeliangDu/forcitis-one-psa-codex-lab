package com.forcitis.one.platform.file.api;

import java.util.Objects;

/** Declared when a file is stored and never changed: a signed file derives from the unsigned one, it is not its next version (ADR-0060 section 4). */
public sealed interface Lineage {

    record FirstVersion() implements Lineage {
    }

    record NextVersion(FileId previous) implements Lineage {

        public NextVersion {
            Objects.requireNonNull(previous, "previous");
        }
    }

    record DerivedFrom(FileId source, DerivationKind derivation) implements Lineage {

        public DerivedFrom {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(derivation, "derivation");
        }
    }
}
