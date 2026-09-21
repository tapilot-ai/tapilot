import { defineConfig } from '@rslib/core';

export default defineConfig({
  lib: [
    {
      output: { distPath: { root: 'dist/lib' } },
      format: 'cjs',
      syntax: 'es2020',
    },
    {
      output: { distPath: { root: 'dist/es' } },
      dts: { distPath: 'dist/types' },
      format: 'esm',
      syntax: 'es2020',
    },
  ],
  source: {
    tsconfigPath: 'tsconfig.json',
    entry: {
      index: './src/index.ts',
      protocol: './src/protocol.ts',
    },
  },
});
