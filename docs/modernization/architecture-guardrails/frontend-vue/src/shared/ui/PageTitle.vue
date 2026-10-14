<script setup lang="ts">
const { text } = defineProps<{ text: string }>();
</script>

<template>
  <h1 class="page-title">{{ text }}</h1>
</template>

<style scoped>
.page-title { margin: 0 0 16px; color: var(--text-primary); font-size: 20px; }
</style>
