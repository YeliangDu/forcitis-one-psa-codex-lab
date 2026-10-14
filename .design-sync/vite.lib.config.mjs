// design-sync 专用构建：把 @forcitis/ui 与 @forcitis/design-system 打成一个 ESM 库入口，
// 由 Vite 处理 tokens.css?raw 等 Vite 专有导入，转换器再从 dist 入口打 IIFE。
import { fileURLToPath, URL } from 'node:url';

export default {
  root: fileURLToPath(new URL('../frontend/packages/ui', import.meta.url)),
  logLevel: 'warn',
  build: {
    outDir: fileURLToPath(new URL('../frontend/packages/ui/dist', import.meta.url)),
    emptyOutDir: false,
    cssCodeSplit: false,
    minify: false,
    lib: {
      entry: fileURLToPath(new URL('./lib-entry.ts', import.meta.url)),
      formats: ['es'],
      fileName: () => 'index.js',
      cssFileName: 'ui',
    },
    rollupOptions: { external: ['react', 'react-dom', 'react/jsx-runtime', 'react-dom/client'] },
  },
};
