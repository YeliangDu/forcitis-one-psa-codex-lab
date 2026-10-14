import path from 'node:path';
import { defineConfig } from '@tarojs/cli';
import devConfig from './dev';
import prodConfig from './prod';
import { publicPathOf } from './public-path.mjs';

const workspacePackages = path.resolve(__dirname, '..', '..', '..', 'packages');

// @forcitis/core is shared with apps/web (React 19); its React and TanStack Query imports must resolve to this app's React 18 copies.
const ownCopy = (name: string) => path.dirname(require.resolve(`${name}/package.json`));

export default defineConfig<'webpack5'>(async (merge) => {
  const base = {
    projectName: 'forcitis-one-h5',
    date: '2026-10-14',
    designWidth: 375,
    deviceRatio: { 375: 2, 640: 2.34 / 2, 750: 1, 828: 1.81 / 2 },
    sourceRoot: 'src',
    outputRoot: 'dist',
    plugins: [],
    defineConstants: {
      'process.env.TARO_APP_WECOM_CORP_ID': JSON.stringify(process.env['TARO_APP_WECOM_CORP_ID'] ?? ''),
    },
    copy: { patterns: [], options: {} },
    framework: 'react' as const,
    compiler: 'webpack5' as const,
    cache: { enable: false },
    alias: {
      '@': path.resolve(__dirname, '..', 'src'),
      react: ownCopy('react'),
      'react-dom': ownCopy('react-dom'),
      '@tanstack/react-query': ownCopy('@tanstack/react-query'),
    },
    h5: {
      // The workspace packages ship TypeScript source; @tanstack/query-core ships class private fields that Taro's minifier writes as invalid syntax;
      // zod, query-string (a dependency of Taro's H5 runtime) and its helpers ship syntax newer than the WeCom browsers read. All of them go through Babel (babel-preset-taro compiles them down) like the app's own code; scripts/check-bundle.mjs proves the result.
      webpackChain(chain: { module: { rule: (name: string) => { include: { add: (condition: string | RegExp) => void } } } }) {
        const script = chain.module.rule('script');
        script.include.add(workspacePackages);
        script.include.add(/[\\/](@tanstack|zod|query-string|filter-obj|split-on-first)[\\/]/);
      },
      publicPath: publicPathOf(process.env),
      staticDirectory: 'static',
      router: { mode: 'hash' as const },
      output: { filename: 'js/[name].[hash:8].js', chunkFilename: 'js/[name].[chunkhash:8].js' },
      miniCssExtractPluginOption: { ignoreOrder: true, filename: 'css/[name].[hash].css', chunkFilename: 'css/[name].[chunkhash].css' },
      postcss: { autoprefixer: { enable: true, config: {} }, cssModules: { enable: false } },
    },
  };
  return process.env['NODE_ENV'] === 'development' ? merge({}, base, devConfig) : merge({}, base, prodConfig);
});
