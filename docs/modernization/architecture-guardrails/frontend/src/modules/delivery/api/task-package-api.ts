import { apiClient } from '@/api/generated/client';
import type { TaskPackage } from '../model/task-package';

export async function fetchTaskPackage(id: number): Promise<TaskPackage> {
  const response = await apiClient.getTaskPackage(id);
  return { id: response.id, remainingHours: response.remainingHours };
}
