import { createInterface } from 'node:readline';

// One activation per process. Closing the bootstrap reader before creating the
// native bridge keeps its RPC responses out of this protocol. An abandoned
// preparation has a bounded lifetime even if the Android owner disappears.
export function waitForActivation(input, timeoutMs = 45_000) {
  return new Promise((resolve, reject) => {
    const reader = createInterface({ input });
    let settled = false;
    const finish = (active, error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      process.removeListener('SIGINT', cancel);
      process.removeListener('SIGTERM', cancel);
      reader.close();
      input.pause();
      if (error) reject(error); else resolve(active);
    };
    const cancel = () => finish(false);
    const timer = setTimeout(cancel, timeoutMs);
    process.once('SIGINT', cancel);
    process.once('SIGTERM', cancel);
    reader.once('close', cancel);
    reader.once('error', error => finish(false, error));
    reader.once('line', line => {
      try {
        const command = JSON.parse(line);
        if (command.type !== 'activate' || Object.keys(command).length !== 1) {
          throw new Error('Invalid prepared runtime activation');
        }
        finish(true);
      } catch (error) { finish(false, error); }
    });
  });
}
