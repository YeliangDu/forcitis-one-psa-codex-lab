import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const source = (path: string) => fileURLToPath(new URL(path, import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@tarojs/components': source('./src/shared/testing/taro-components.tsx'),
      '@tarojs/taro': source('./src/shared/testing/taro-stub.ts'),
      '@forcitis/design-system/mobile': source('./src/shared/testing/empty-module.ts'),
      '@': source('./src'),
    },
  },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], css: false, testTimeout: 15000 },
});
