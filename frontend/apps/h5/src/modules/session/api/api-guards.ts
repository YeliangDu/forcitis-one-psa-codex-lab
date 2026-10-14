import { setApiHandlers } from '@forcitis/api';

export interface GuardActions {
  onUnauthenticated: () => void;
}

export function installApiGuards(actions: GuardActions): () => void {
  setApiHandlers(actions);
  return () => setApiHandlers({});
}
