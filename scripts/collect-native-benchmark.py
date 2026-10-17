#!/usr/bin/env python3
"""Collect one real debug-APK task. Timeouts never replay or cancel phone actions."""
import argparse
import json
import pathlib
import shlex
import subprocess
import threading


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('label')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--mode', choices=['full', 'execution'], default='full')
    parser.add_argument('--fixture', choices=['read-auto-brightness', 'read-android-version'], default='read-auto-brightness')
    parser.add_argument('--runtime-preload', choices=['on', 'off', 'reset'], default='reset')
    args = parser.parse_args()
    if pathlib.Path(args.label).name != args.label or args.label in ('.', '..'):
        parser.error('Label must be a single directory name')
    out = pathlib.Path(__file__).resolve().parents[1] / 'artifacts/controlled-execution' / args.label
    out.mkdir(parents=True, exist_ok=False)
    adb = ['adb', '-s', args.serial]

    def shell(*values):
        return subprocess.check_output(adb + ['shell', shlex.join(values)], text=True)

    def reports():
        return set(shell('run-as', 'lifeos.md.tapilot', 'find', 'files/node-runs', '-name', 'android-accessibility-*.html').splitlines())

    before = reports()
    log = subprocess.Popen(adb + ['logcat', '-T', '1', '-v', 'threadtime', 'TapilotPerformance:I', '*:S'], stdout=subprocess.PIPE, text=True)
    # createdAt is a device timestamp. A host clock can be ahead and miss the entire task.
    started = int(shell('date', '+%s%3N').strip())
    timer = threading.Timer(240, log.terminate)
    timer.start()
    wall = None
    try:
        mode = 'run_benchmark' if args.mode == 'full' else 'controlled'
        response = shell('am', 'broadcast', '-n', 'lifeos.md.tapilot/.performance.PerformanceExperimentReceiver',
                         '--es', 'screenshot_shrink_factor', 'reset', '--ez', mode, 'true',
                         '--es', 'capture_format', 'jpeg', '--es', 'fixture', args.fixture,
                         '--es', 'runtime_preload', args.runtime_preload)
        print(response, flush=True)
        if 'result=0' not in response:
            raise RuntimeError('Benchmark rejected')
        with (out / 'logcat.txt').open('w') as target:
            for line in log.stdout:
                target.write(line)
                target.flush()
                if 'TapilotPerformance: {' not in line:
                    continue
                event = json.loads(line.split('TapilotPerformance: ', 1)[1])
                if event.get('phase') == 'task.wall' and event.get('createdAt', 0) >= started:
                    wall = event
                    break
    finally:
        timer.cancel()
        log.terminate()
        log.wait(timeout=5)
    if wall is None:
        raise RuntimeError('Observer timed out; inspect device task state. Do not replay automatically.')
    print(json.dumps(wall), flush=True)
    manifest = json.loads(shell('run-as', 'lifeos.md.tapilot', 'cat', 'files/node-runtime/manifest.json'))
    (out / 'sample.json').write_text(json.dumps({'mode': args.mode, 'fixture': args.fixture, 'capture': 'product-default', 'runtimePreload': args.runtime_preload, 'runtimeSourceFingerprint': manifest['sourceFingerprint'], 'task': wall}, indent=2))
    for index, remote in enumerate(sorted(reports() - before)):
        data = subprocess.check_output(adb + ['exec-out', 'run-as', 'lifeos.md.tapilot', 'cat', remote])
        (out / f'report-{index}.html').write_bytes(data)
        run = remote.split('/midscene_run/')[0]
        for name, suffix in [('planning.log', '/midscene_run/log/planning.log'), ('result.json', '/results/android-runner-result.json')]:
            data = subprocess.run(adb + ['exec-out', 'run-as', 'lifeos.md.tapilot', 'cat', run + suffix], capture_output=True, check=True)
            (out / f'{index}-{name}').write_bytes(data.stdout)
    shell('screencap', '-p', '/sdcard/profile.png')
    subprocess.run(adb + ['pull', '/sdcard/profile.png', str(out / 'end.png')], check=True, stdout=subprocess.DEVNULL)
    if wall.get('status') != 'succeeded':
        raise RuntimeError('Task did not succeed; failed sample preserved')


if __name__ == '__main__':
    main()
