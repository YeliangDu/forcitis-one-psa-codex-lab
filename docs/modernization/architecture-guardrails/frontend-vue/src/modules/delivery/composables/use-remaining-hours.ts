import { useQuery } from '@tanstack/vue-query';
import { computed, toValue, type MaybeRefOrGetter } from 'vue';
import { fetchTaskPackage } from '../api/task-package-api';

export function useRemainingHours(taskPackageId: MaybeRefOrGetter<string>) {
  const query = useQuery({
    queryKey: ['delivery', 'task-package', computed(() => toValue(taskPackageId))],
    queryFn: () => fetchTaskPackage(toValue(taskPackageId)),
  });
  return computed(() => query.data.value?.remainingHours);
}
