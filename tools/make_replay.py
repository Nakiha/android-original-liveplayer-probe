"""Build a private, version-bound replay file from captured player events.

Signed URLs and business configuration remain in the output file, never stdout.
"""
import argparse
import hashlib
import json
from pathlib import Path
from urllib.parse import urlsplit


def matching_url_path(value, path):
    if isinstance(value, str):
        return value.startswith(('http://', 'https://')) and urlsplit(value).path == path
    if isinstance(value, dict):
        return any(matching_url_path(v, path) for v in value.values())
    if isinstance(value, list):
        return any(matching_url_path(v, path) for v in value)
    return False


def build(rows, source_version_code, player_id=None):
    if any(row.get('event') in ('agent_error', 'capture_error', 'capture_gap') for row in rows):
        raise ValueError('The recording contains capture errors or gaps; inspect it before creating a replay')
    if any(row.get('event') == 'settings_result' and row['value']['player_id'] == -1 for row in rows):
        raise ValueError('Settings results have ambiguous player attribution')
    candidates = {}
    for row in rows:
        if row.get('event') == 'native_entry':
            call = row['value']
            if call['method'].startswith('cpp_') and call['args'] and isinstance(call['args'][0], int):
                candidates.setdefault(call['args'][0], []).append(call)
    candidates = {pid: calls for pid, calls in candidates.items()
                  if any(c['method'] == 'cpp_setConfig' for c in calls)
                  and any(c['method'] == 'cpp_setStreamDataInfo' for c in calls)
                  and any(c['method'] == 'cpp_play' for c in calls)}
    if player_id is None:
        if len(candidates) != 1:
            raise ValueError('Select --player-id explicitly when the recording has zero or multiple complete players')
        player_id = next(iter(candidates))
    calls = candidates[player_id]
    first_play = next(i for i, call in enumerate(calls) if call['method'] == 'cpp_play')
    calls = calls[:first_play + 1]
    stream_calls = [call for call in calls if call['method'] == 'cpp_setStreamDataInfo']
    if not stream_calls:
        raise ValueError('The first play is missing its captured stream input')
    ready = next(row['value'] for row in rows if row.get('event') == 'capture_ready')
    settings = [row['value'] for row in rows if row.get('event') == 'settings_result'
                and row['value']['player_id'] == player_id]
    result = dict(schema_version=1, source_version_code=source_version_code,
                  native_version=ready['native_version'], native_calls=calls, settings_results=settings,
                  source_statistics_override=ready.get('statistics_override', False),
                  equivalence_gaps=['Uncaptured historical global configuration', 'Business observer and custom network-client implementations'])
    statistics = [row['value']['args'][0] for row in rows if row.get('event') == 'official_callback'
                  and row['value'].get('method') == 'onStatistics' and row['value']['player_id'] == player_id]
    statistics = [s for s in statistics if s.get('url') and s.get('width', 0) > 0 and s.get('fps', 0) > 0]
    if statistics:
        selected = statistics[-1]['url']
        path = urlsplit(selected).path
        info = json.loads(stream_calls[-1]['args'][1])
        qualities = [k for k, v in info.get('data', {}).items() if matching_url_path(v, path)]
        if len(qualities) == 1:
            result.update(selected_resolution=qualities[0], selected_stream_url=selected,
                          selection_provenance='Pin the source-observed output rendition; do not claim to reproduce its ABR selection decision')
    summary = dict(native_version=result['native_version'], setup_calls=len(calls),
                   settings_results=len(settings), rendition_pinned=result.get('selected_resolution'),
                   stream_data_sha256=hashlib.sha256(stream_calls[-1]['args'][1].encode()).hexdigest())
    return result, summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--source-version-code', type=int, required=True)
    parser.add_argument('--player-id', type=int)
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.capture.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    result, summary = build(rows, args.source_version_code, args.player_id)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary))
