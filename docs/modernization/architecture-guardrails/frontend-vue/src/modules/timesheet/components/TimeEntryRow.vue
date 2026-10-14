<script setup lang="ts">
import { formatHours } from '@/shared/lib/format-hours';
import { MoneyText, StatusBadge } from '@/shared/ui';
import { STATUS_LABEL, statusTone, type TimesheetStatus } from '../model/timesheet-status';

const { hours, costAmount, status } = defineProps<{ hours: number; costAmount: number; status: TimesheetStatus }>();
</script>

<template>
  <div class="time-entry-row">
    <span>{{ formatHours(hours) }}</span>
    <MoneyText :amount="costAmount" side="debit" />
    <StatusBadge :status="statusTone(status)">{{ STATUS_LABEL[status] }}</StatusBadge>
  </div>
</template>

<style scoped>
.time-entry-row {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-subtle);
}
</style>
