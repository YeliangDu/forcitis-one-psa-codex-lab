// The local test login of a developer's own machine (card #1478); the calls live in @forcitis/api because only that package may talk to the server by hand.
export type { LocalAccount } from '@forcitis/api';
export { fetchLocalAccounts, loginAsLocalAccount } from '@forcitis/api';
