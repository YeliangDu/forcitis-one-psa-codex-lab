<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { useRemainingHours } from '@/modules/delivery';
import { AppButton, PageTitle } from '@/shared/ui';
import TimeEntryRow from '../components/TimeEntryRow.vue';
import { useTimesheet } from '../composables/use-timesheet';
import type { TimesheetStatus } from '../model/timesheet-status';
import { useTimesheetFilterStore } from '../stores/use-timesheet-filter-store';

const { selectedId } = storeToRefs(useTimesheetFilterStore());
const timesheet = useTimesheet(selectedId);
const remainingHours = useRemainingHours('1');

const samples: { status: TimesheetStatus; amount: number }[] = [
  { status: 'POSTED', amount: 8_600 },
  { status: 'PENDING', amount: 125_000 },
  { status: 'REJECTED', amount: 3_250_000_00 },
  { status: 'REVERSAL', amount: -42_000 },
];
</script>

<template>
  <section class="timesheet-page">
    <PageTitle text="工时填报" />
    <TimeEntryRow
      v-if="timesheet.data.value"
      :hours="timesheet.data.value.hours"
      :cost-amount="timesheet.data.value.costAmount"
      :status="timesheet.data.value.status"
    />
    <TimeEntryRow
      v-for="sample in samples"
      :key="sample.status"
      :hours="remainingHours ?? 0"
      :cost-amount="sample.amount"
      :status="sample.status"
    />
    <AppButton>提交</AppButton>
  </section>
</template>

<style scoped>
.timesheet-page {
  padding: 24px;
  background: var(--surface-page);
}
</style>
