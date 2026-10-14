import { VueQueryPlugin } from '@tanstack/vue-query';
import { createPinia } from 'pinia';
import { createApp } from 'vue';
import '@/shared/design-system';
import App from './App.vue';

createApp(App).use(createPinia()).use(VueQueryPlugin).mount('#app');
