import { z } from 'zod';

type Sink = (message: string) => void;

let sink: Sink = (message) => console.error(message);
const reported = new Set<string>();

/** G-1: a value outside the contract is never dropped silently. Each distinct message is reported once. */
export function reportContractViolation(message: string): void {
  if (reported.has(message)) return;
  reported.add(message);
  sink(message);
}

export function setContractViolationSink(next: Sink): void {
  sink = next;
}

export function forgetReportedViolations(): void {
  reported.clear();
}

/**
 * An enum of the contract that tolerates a value added by a newer server: the value passes through and is reported once,
 * instead of one unknown value failing the whole list it sits in.
 */
export function lenientEnum(where: string, values: readonly string[]) {
  const known = new Set(values);
  return z.string().superRefine((value) => {
    if (!known.has(value)) reportContractViolation(`${where}: the server sent "${value}", which openapi.json does not list`);
  });
}
