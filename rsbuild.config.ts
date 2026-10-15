import { defineConfig } from '@rsbuild/core';

export default defineConfig({
  source: {
    entry: process.env.TAPILOT_SPEECH_VERIFICATION === '1'
      ? { index: './legacy/web-ui/index.ts', speechverification: './legacy/web-ui/speech-verification-entry.ts' }
      : { index: './legacy/web-ui/index.ts' },
    define: { __SDK_VERSION__: JSON.stringify('tapilot') },
  },
  html: {
    title: 'Tapilot',
    template: './legacy/web-ui/index.html',
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
});
