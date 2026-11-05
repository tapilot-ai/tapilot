import { lstat, chmod } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join } from 'node:path';
const exec = promisify(execFile);
// Never interpolate paths or credentials into PowerShell source. Only an empty
// new path may be hardened; existing credential paths must pass inspection.
async function windowsAcl(path, protect) {
  const script = `
$ErrorActionPreference = 'Stop'
$p = $env:TAPILOT_PRIVATE_PATH
$item = Get-Item -LiteralPath $p -Force
if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'reparse point' }
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
if ($env:TAPILOT_PRIVATE_PROTECT -eq '1') {
  if ($item.PSIsContainer) { $acl = New-Object Security.AccessControl.DirectorySecurity }
  else { $acl = New-Object Security.AccessControl.FileSecurity }
  $acl.SetOwner($sid)
  $acl.SetAccessRuleProtection($true, $false)
  if ($item.PSIsContainer) {
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
  } else { $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'Allow') }
  $acl.AddAccessRule($rule)
  Set-Acl -LiteralPath $p -AclObject $acl
}
$acl = Get-Acl -LiteralPath $p
if ($acl.GetOwner([Security.Principal.SecurityIdentifier]).Value -ne $sid.Value) { throw 'foreign owner' }
$allowed = @($sid.Value, 'S-1-5-18', 'S-1-5-32-544')
$mine = $false
foreach ($rule in $acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier])) {
  if ($rule.AccessControlType -eq 'Allow') {
    if ($allowed -notcontains $rule.IdentityReference.Value) { throw 'foreign access' }
    if ($rule.IdentityReference.Value -eq $sid.Value) { $mine = $true }
  }
}
if (-not $mine) { throw 'missing owner access' }
`;
  try {
    await exec(join(process.env.SystemRoot || 'C:\\Windows', 'System32/WindowsPowerShell/v1.0/powershell.exe'),
      ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')],
      { env: { ...process.env, TAPILOT_PRIVATE_PATH: path, TAPILOT_PRIVATE_PROTECT: protect ? '1' : '0' }, timeout: 15000, windowsHide: true });
  } catch { throw new Error('unsafe_private_path'); }
}
export async function assertPrivatePath(path, directory = false) {
  const stat = await lstat(path);
  if (stat.isSymbolicLink() || (directory ? !stat.isDirectory() : !stat.isFile())) throw new Error('unsafe_private_path');
  if (process.platform === 'win32') await windowsAcl(path, false);
  else if (stat.mode & 0o077) throw new Error('unsafe_private_path');
}
export async function protectPrivatePath(path, directory = false) {
  const stat = await lstat(path);
  if (stat.isSymbolicLink() || (directory ? !stat.isDirectory() : !stat.isFile())) throw new Error('unsafe_private_path');
  if (process.platform === 'win32') await windowsAcl(path, true);
  else await chmod(path, directory ? 0o700 : 0o600);
}
