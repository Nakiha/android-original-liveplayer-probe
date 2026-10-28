"""Send a private replay to the debug probe and save its private JSONL locally.

The original APK must already be imported in the probe. No URL is printed.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('replay', type=Path)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--seconds', type=int, default=20)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 120:
        parser.error('--seconds must be between 1 and 120')
    payload = args.replay.read_bytes()
    document = json.loads(payload)
    adb = [args.adb, '-s', args.serial]

    def run(*command, data=None):
        return subprocess.run(adb + list(command), input=data, capture_output=True, check=True).stdout

    run('shell', 'run-as', 'local.originalplayer.probe', 'mkdir', '-p', 'files/replays')
    run('shell', 'run-as', 'local.originalplayer.probe', 'sh', '-c',
        shlex.quote('cat > files/replays/session.json'), data=payload)
    run('shell', 'am', 'start', '-W', '-S', '-f', '0x10008000',
        '-n', 'local.originalplayer.probe/.MainActivity', '--es', 'probe_action', 'run',
        '--es', 'probe_mode', 'play', '--es', 'probe_replay', 'session.json')
    print('Replay launched; waiting for original player callbacks.', flush=True)
    time.sleep(args.seconds)
    raw = run('exec-out', 'run-as', 'local.originalplayer.probe', 'cat', 'files/probe.jsonl')
    rows = [json.loads(line) for line in raw.decode('utf-8').splitlines() if line.strip()]
    starts = [i for i, row in enumerate(rows) if row.get('event') == 'probe_begin']
    if not starts:
        raise RuntimeError('The probe did not record a session')
    rows = rows[starts[-1]:]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in rows), encoding='utf-8')
    stats = [row['value'][0] for row in rows if row.get('event') == 'original_callback.onStatistics']
    expected = document.get('selected_stream_url')
    playing = [s for s in stats if s.get('width', 0) > 0 and s.get('fps', 0) > 0]
    matched = bool(expected and playing and all(s.get('url') == expected for s in playing))
    failures = sum(row.get('event') in ('probe_failure', 'uncaught_java_failure') for row in rows)
    summary = dict(statistics_samples=len(stats), probe_failures=failures,
                   settings_default_fallbacks=sum(row.get('event') == 'replay_settings_default_fallback' for row in rows),
                   selected_url_matches_source=matched if expected else None)
    if stats:
        summary['last_statistics'] = {k: stats[-1].get(k) for k in
                                      ('videoCodec', 'width', 'height', 'fps', 'codecName', 'isHardwareDecode',
                                       'dnsResolver', 'audioBufferMs', 'videoBufferMs', 'stallTimeMs', 'errorCode')}
        if stats[-1].get('url'):
            summary['selected_url_sha256'] = hashlib.sha256(stats[-1]['url'].encode()).hexdigest()
    print(json.dumps(summary, ensure_ascii=False))
    if failures or not stats or (expected and not matched):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
