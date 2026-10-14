import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (/[\\/](react|react-dom|react-router|scheduler|@tanstack)[\\/]/.test(id)) return 'framework';
          if (/[\\/](@ant-design|@rc-component|rc-[a-z-]+)[\\/]/.test(id)) return 'antd-components';
          if (/[\\/]antd[\\/]/.test(id)) return 'antd';
          return 'vendor';
        },
      },
    },
  },
  server: { proxy: { '/api': 'http://localhost:8080' } },
  test: { environment: 'jsdom', setupFiles: ['./src/test/setup.ts'], css: false, testTimeout: 15000 },
});
