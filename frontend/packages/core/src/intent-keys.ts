import { newIdempotencyKey } from '@forcitis/api';
import { useRef } from 'react';

/**
 * One Idempotency-Key per user intent. The fingerprint names the intent and its content ("approve:工时相符"):
 * a retry of the same intent after a failure gets the key of the first attempt, so the server can recognise it;
 * after a success, or when the content changes, the next intent gets a new key.
 */
export function useIntentKeys() {
  const last = useRef<{ fingerprint: string; key: string } | undefined>(undefined);
  return {
    keyFor(fingerprint: string): string {
      if (last.current?.fingerprint !== fingerprint) last.current = { fingerprint, key: newIdempotencyKey() };
      return last.current.key;
    },
    done(): void {
      last.current = undefined;
    },
  };
}
