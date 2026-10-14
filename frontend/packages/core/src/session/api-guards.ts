import { type ApiHandlers, setApiHandlers } from '@forcitis/api';

export function installApiGuards(actions: ApiHandlers): () => void {
  setApiHandlers(actions);
  return () => setApiHandlers({});
}
