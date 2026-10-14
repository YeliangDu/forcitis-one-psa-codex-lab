<script setup lang="ts">
// Design-system wrapper: business modules use <AppButton>, never reka-ui directly.
import { Primitive } from 'reka-ui';

const { variant = 'primary', disabled = false } = defineProps<{
  variant?: 'primary' | 'default';
  disabled?: boolean;
}>();
const emit = defineEmits<{ click: [event: MouseEvent] }>();
</script>

<template>
  <Primitive as-child>
    <button
      type="button"
      class="app-button"
      :class="`app-button--${variant}`"
      :disabled="disabled"
      @click="emit('click', $event)"
    >
      <slot />
    </button>
  </Primitive>
</template>

<style scoped>
.app-button {
  padding: 6px 16px;
  border: 1px solid var(--border-default);
  border-radius: 6px;
  background: var(--surface-raised);
  color: var(--text-primary);
  cursor: pointer;
}

.app-button:focus-visible {
  outline: 2px solid var(--border-focus);
}

.app-button--primary {
  border-color: var(--intent-primary-bg);
  background: var(--intent-primary-bg);
  color: var(--intent-primary-fg);
}

.app-button--primary:hover {
  background: var(--intent-primary-bg-hover);
}

.app-button:disabled {
  background: var(--surface-disabled);
  color: var(--text-disabled);
  cursor: not-allowed;
}
</style>
