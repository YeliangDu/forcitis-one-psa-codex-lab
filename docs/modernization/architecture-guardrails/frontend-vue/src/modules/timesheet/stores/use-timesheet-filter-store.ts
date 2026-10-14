import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useTimesheetFilterStore = defineStore('timesheet-filter', () => {
  const selectedId = ref('1');
  return { selectedId };
});
