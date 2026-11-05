import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

export const installerName = process.platform === 'win32' ? 'Install Tapilot.cmd' : process.platform === 'darwin' ? 'Install Tapilot.command' : 'install-tapilot.sh';
export function writeInstaller(directory) {
  const content = process.platform === 'win32'
    ? '@echo off\r\nsetlocal\r\n"%~dp0bin\\node.exe" "%~dp0runtime\\scripts\\onboard.mjs" %*\r\nexit /b %errorlevel%\r\n'
    : '#!/bin/sh\nset -eu\ncd -- "$(dirname -- "$0")"\nexec ./bin/node ./runtime/scripts/onboard.mjs "$@"\n';
  fs.writeFileSync(path.join(directory, installerName), content, { mode: 0o755 });
}
export function nodeLicense() {
  const bin = path.dirname(fs.realpathSync(process.execPath));
  for (const candidate of [path.join(bin, 'LICENSE'), path.resolve(bin, '../LICENSE')]) {
    if (fs.existsSync(candidate)) return candidate;
  }
  throw new Error('The selected Node distribution must include its LICENSE');
}
export function zipDirectory(directory, destination) {
  if (process.platform === 'win32') {
    // Environment variables preserve paths without interpolating PowerShell code.
    execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      'Add-Type -AssemblyName System.IO.Compression.FileSystem; [IO.Compression.ZipFile]::CreateFromDirectory($env:TAPILOT_ZIP_SOURCE, $env:TAPILOT_ZIP_TARGET)'],
    { env: { ...process.env, TAPILOT_ZIP_SOURCE: directory, TAPILOT_ZIP_TARGET: destination } });
  } else execFileSync('zip', ['-qr', destination, '.'], { cwd: directory });
}
