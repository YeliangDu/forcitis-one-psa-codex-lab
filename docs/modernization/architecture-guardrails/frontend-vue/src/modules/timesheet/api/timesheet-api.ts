import { apiClient } from '@/api/generated/client';
import type { Timesheet } from '../model/timesheet';

export async function fetchTimesheet(id: string): Promise<Timesheet> {
  const response = await apiClient.getTimesheet(id);
  return { ...response };
}
