export type RadioState = { wifi: boolean; data: boolean; airplane: boolean };

export const shellQuote = (value: string) => `'${value.replaceAll("'", "'\\''")}'`;

/** Runs on the phone, so USB loss cannot cancel the recovery deadline. */
export function networkWatchdogScript(directory: string, state: RadioState, seconds = 300) {
  if (!Number.isInteger(seconds) || seconds < 1) throw new Error('Invalid recovery deadline');
  const dir = shellQuote(directory);
  return `#!/system/bin/sh
cd ${dir} || exit 1
touch armed
remaining=${seconds}
while [ "$remaining" -gt 0 ]; do
  [ -f pending ] || exit 0
  sleep 1
  remaining=$((remaining - 1))
done
[ -f pending ] || exit 0
touch fired
cmd connectivity airplane-mode ${state.airplane ? 'enable' : 'disable'}
svc wifi ${state.wifi ? 'enable' : 'disable'}
svc data ${state.data ? 'enable' : 'disable'}
`;
}
