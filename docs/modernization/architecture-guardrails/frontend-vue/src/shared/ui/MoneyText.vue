<script setup lang="ts">
import { computed } from 'vue';
import { formatMoneyCompact } from '@/shared/lib/format-money';

const { amount, side } = defineProps<{ amount: number; side?: 'debit' | 'credit' }>();
const text = computed(() => formatMoneyCompact(amount));
</script>

<template>
  <span class="money-text" :class="side ? `money-text--${side}` : undefined">{{ text }}</span>
</template>

<style scoped>
.money-text { font-variant-numeric: tabular-nums; color: var(--text-primary); }
.money-text--debit { color: var(--ledger-debit); }
.money-text--credit { color: var(--ledger-credit); }
</style>
