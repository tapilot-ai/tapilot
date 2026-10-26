#!/usr/bin/env node
import { existsSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
const entry = process.env.ANDROID_USE_ROOT
  ? resolve(process.env.ANDROID_USE_ROOT, "server/cli.mjs")
  : fileURLToPath(new URL("../../../server/cli.mjs", import.meta.url));
if (!existsSync(entry)) {
  process.stderr.write(
    "Android Use runtime is missing. Set ANDROID_USE_ROOT to the installed package containing server/cli.mjs.\n",
  );
  process.exitCode = 1;
} else await import(pathToFileURL(entry).href);
