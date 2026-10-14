import { useEffect, useState } from 'react';
import { fetchTaskPackage } from '../api/task-package-api';

export function useRemainingHours(taskPackageId: number): number | undefined {
  const [hours, setHours] = useState<number>();
  useEffect(() => {
    void fetchTaskPackage(taskPackageId).then((taskPackage) => setHours(taskPackage.remainingHours));
  }, [taskPackageId]);
  return hours;
}
