package com.forcitis.one;

/**
 * The failure text of a guard: the rule it enforces, what is wrong and how to fix it, so that the failing test is the whole instruction
 * (coding-core section D). The rule is a CS / G / F / D id of the coding and naming standards, or the ADR / invariant the guard cites.
 */
public final class GuardMessage {

    private GuardMessage() {
    }

    public static String of(String rule, String wrong, String fix) {
        return rule + " " + wrong + ". Fix: " + fix + ".";
    }
}
