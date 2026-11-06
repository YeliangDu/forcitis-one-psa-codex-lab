import { z } from 'zod';

// The public CSP has no 'unsafe-eval'. zod's JIT probe (new Function) is reported as a CSP violation even though it is caught,
// so run schemas interpreted. This module must be the first import of index.ts: the generated schemas are built at import time.
z.config({ jitless: true });
