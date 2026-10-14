import type { UserConfigExport } from '@tarojs/cli';

export default {
  h5: {
    devServer: { proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } } },
  },
} satisfies UserConfigExport<'webpack5'>;
