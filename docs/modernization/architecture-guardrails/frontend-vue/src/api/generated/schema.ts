// Generated from OpenAPI (Zod schemas). Do not edit by hand.
import { z } from 'zod';

export const TimesheetResponse = z.object({
  id: z.string(),
  workDate: z.string(),
  hours: z.number(),
  costAmount: z.number(),
  status: z.enum(['POSTED', 'PENDING', 'REJECTED', 'REVERSAL']),
});
export type TimesheetResponse = z.infer<typeof TimesheetResponse>;

export const TaskPackageResponse = z.object({ id: z.string(), remainingHours: z.number() });
export type TaskPackageResponse = z.infer<typeof TaskPackageResponse>;
