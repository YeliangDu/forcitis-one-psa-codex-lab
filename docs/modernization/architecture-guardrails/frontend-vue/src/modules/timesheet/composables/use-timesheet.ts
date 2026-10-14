import { useQuery } from '@tanstack/vue-query';
import { computed, toValue, type MaybeRefOrGetter } from 'vue';
import { fetchTimesheet } from '../api/timesheet-api';

export function useTimesheet(id: MaybeRefOrGetter<string>) {
  return useQuery({
    queryKey: ['timesheet', computed(() => toValue(id))],
    queryFn: () => fetchTimesheet(toValue(id)),
  });
}
