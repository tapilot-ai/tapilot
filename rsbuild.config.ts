import { defineConfig } from '@rsbuild/core';
import { pluginNodePolyfill } from '@rsbuild/plugin-node-polyfill';

export default defineConfig({
  source: {
    entry: process.env.TAPILOT_SPEECH_VERIFICATION === '1'
      ? { index: './runtime/index.ts', speechverification: './runtime/speech-verification-entry.ts' }
      : { index: './runtime/index.ts' },
    define: { __SDK_VERSION__: JSON.stringify('tapilot') },
  },
  html: {
    title: 'Tapilot',
    template: './runtime/index.html',
  },
  output: {
    target: 'web',
    distPath: { root: 'dist/runtime' },
    assetPrefix: './',
    polyfill: 'entry',
    externals: ['sharp'],
    sourceMap: false,
  },
  resolve: {
    alias: {
      async_hooks: false,
      'node:async_hooks': false,
      sharp: false,
    },
  },
  plugins: [pluginNodePolyfill()],
});
