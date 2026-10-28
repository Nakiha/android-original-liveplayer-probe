"""Observe a running original live player using a pre-authorized Frida environment.

All payloads, including signed URLs, are written to a local JSONL file, never stdout.
Requires matching frida/frida-tools and a rooted development device/emulator.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import threading
import time

import frida
import frida_tools


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True)
    parser.add_argument('--pid', required=True, type=int)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--seconds', type=int, default=35)
    parser.add_argument('--statistics', action='store_true', help='Explicit diagnostic change: enable original statistics callbacks at 1 second')
    args = parser.parse_args()
    if not 1 <= args.seconds <= 120:
        parser.error('--seconds must be between 1 and 120')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    bridge = Path(frida_tools.__file__).parent / 'bridges' / 'java.js'
    if not bridge.is_file():
        raise RuntimeError('Install a matching frida-tools package with the Java bridge')
    source = bridge.read_text(encoding='utf-8') + '\nglobalThis.Java = bridge;\n'
    source += 'globalThis.LP_OPTIONS=' + json.dumps({'statistics': args.statistics}) + ';\n'
    source += Path(__file__).with_name('observe_liveplayer.js').read_text(encoding='utf-8')
    ready = threading.Event()
    detached = threading.Event()
    counters = Counter()
    device = frida.get_device(args.device, timeout=5)
    session = device.attach(args.pid)
    session.on('detached', lambda *unused: detached.set())
    with args.output.open('w', encoding='utf-8') as output:
        def message(item, data):
            record = item.get('payload') if item.get('type') == 'send' else {'event': 'agent_error', 'value': item}
            output.write(json.dumps(record, ensure_ascii=False) + '\n')
            output.flush()
            counters[record.get('event', 'unknown')] += 1
            if record.get('event') == 'capture_ready':
                ready.set()
        script = session.create_script(source, name='live-player-observation', runtime='qjs')
        script.on('message', message)
        script.load()
        if not ready.wait(15):
            session.detach()
            raise RuntimeError('Player observation did not become ready; inspect the local log')
        print('Player observation ready; payloads stay in the local file.', flush=True)
        deadline = time.monotonic() + args.seconds
        while time.monotonic() < deadline and not detached.wait(0.25):
            pass
        unexpected_detach = detached.is_set()
        session.detach()
    print(json.dumps({'events': dict(counters), 'target_exited_before_timeout': unexpected_detach}), flush=True)


if __name__ == '__main__':
    main()
