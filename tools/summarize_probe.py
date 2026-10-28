"""Summarize the last probe session without emitting URLs, paths, or device identifiers."""
import argparse
from collections import Counter
import json
from pathlib import Path


def summarize(rows):
    starts = [i for i, row in enumerate(rows) if row.get('event') == 'probe_begin']
    if not starts:
        raise ValueError('No probe_begin event')
    rows = rows[starts[-1]:]
    events = Counter(row['event'] for row in rows)
    statistics = [row['value'][0] for row in rows if row['event'] == 'original_callback.onStatistics']
    report = {
        'native_version': next((r['value'] for r in rows if r['event'] == 'original_native_version'), None),
        'first_video_frame_callback': events['original_callback.onFirstVideoFrameRender'] > 0,
        'first_audio_frame_callback': events['original_callback.onFirstAudioFrameRender'] > 0,
        'statistics_count': len(statistics),
        'error_callback_count': events['original_callback.onError'],
        'stall_start_callback_count': events['original_callback.onStallStart'],
        'failure_event_count': sum(v for k, v in events.items() if 'failure' in k),
    }
    keys = ['videoCodec', 'codecName', 'isHardwareDecode', 'format', 'protocol', 'width', 'height',
            'fps', 'audioBufferMs', 'videoBufferMs', 'stallTimeMs', 'firstFrameTimeMs', 'errorCode']
    report['last_statistics'] = {k: statistics[-1][k] for k in keys if k in statistics[-1]} if statistics else None
    report['scope'] = 'Last session only. Buffer fields retain original SDK semantics; no inference of A/V presentation sync.'
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('jsonl', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = summarize([json.loads(line) for line in args.jsonl.read_text(encoding='utf-8-sig').splitlines() if line.strip()])
    result = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(result, encoding='utf-8')
    print(result)
