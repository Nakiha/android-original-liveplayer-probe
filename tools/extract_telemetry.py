"""Inventory and project numeric telemetry from local original/probe JSONL.

Retains each monitor layer and counter window separately. No automatic summing
or ratio calculation: duplicate layers, sessions and cumulative/delta counters
cannot safely be combined just because they share a field name.
"""
import argparse
from collections import Counter
import json
import math
from pathlib import Path
import re


METRIC = re.compile(r'(stall|buffer|delay|sei|temperature|thermal|fps|bitrate|bandwidth|download_speed|downloadSpeed|first.*frame|firstFrame|total_cost|play_time|duration|cpu_rate|gpu_usage|battery|mem_pss|render.*pts|audio.*pts|video.*pts)', re.I)
IDENTIFIER = re.compile(r'(url|domain|ip$|room|user|session|uuid|token|device_id|push_client|pushClient|model|source|version|time_ms$|timestamp)', re.I)


def unpack(value):
    if isinstance(value, dict) and 'java_type' in value and 'value' in value:
        value = value['value']
    if isinstance(value, str):
        try:
            return json.loads(value)
        except (ValueError, TypeError):
            return None
    return value


def numeric(value):
    if isinstance(value, str):
        if not re.fullmatch(r'-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?', value):
            return None
        value = float(value) if any(c in value.lower() for c in '.e') else int(value)
    if isinstance(value, (int, float, bool)) and (not isinstance(value, float) or math.isfinite(value)):
        return value
    if isinstance(value, list) and all(numeric(v) is not None for v in value):
        return [numeric(v) for v in value]
    return None


def payloads(row):
    event, value = row.get('event', ''), row.get('value')
    if event == 'telemetry_monitor' and isinstance(value, dict):
        yield value.get('layer', 'unknown'), unpack(value.get('payload'))
    elif event in ('telemetry_app_info', 'telemetry_performance', 'telemetry_report') and isinstance(value, dict):
        yield value.get('layer', event), unpack(value.get('payload'))
    elif event == 'telemetry_statistics' and isinstance(value, dict):
        yield 'native_statistics', value.get('statistics')
    elif event == 'device_telemetry' and isinstance(value, dict):
        yield 'probe_os_reference', value
    elif event == 'telemetry_battery_observed' and isinstance(value, dict):
        yield 'original_battery_receiver', value
    elif event == 'original_callback.onStatistics' and value:
        yield 'probe_statistics', value[0]
    elif event == 'original_callback.onMonitorLog' and value:
        yield 'independent_probe_monitor', unpack(value[0])
    elif event == 'official_callback' and isinstance(value, dict):
        args = value.get('args', [])
        if args and value.get('method') == 'onMonitorLog':
            yield 'native_observer', unpack(args[0])
        elif args and value.get('method') == 'onStatistics':
            yield 'native_statistics', args[0]


def extract(rows):
    inventory, samples, errors, coverage = {}, [], [], []
    session = 0
    for index, row in enumerate(rows):
        if row.get('event') == 'probe_begin':
            session += 1
        if row.get('event') == 'telemetry_hook':
            coverage.append(row.get('value'))
        if row.get('event') in ('agent_error', 'telemetry_capture_error', 'capture_error', 'capture_gap', 'capture_cleanup_timeout'):
            errors.append({'row': index + 1, 'event': row['event']})
        for layer, data in payloads(row):
            if not isinstance(data, dict):
                continue
            pending = [('', data)]
            projected = {}
            while pending:
                prefix, block = pending.pop()
                for key, value in block.items():
                    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_.: -]{0,99}', key):
                        continue
                    path = prefix + key
                    evidence = inventory.setdefault(layer, {}).setdefault(path, Counter())
                    evidence[type(value).__name__] += 1
                    if isinstance(value, dict):
                        pending.append((path + '.', value))
                    elif METRIC.search(path) and not IDENTIFIER.search(path):
                        number = numeric(value)
                        if number is not None:
                            projected[path] = number
            samples.append(dict(row=index + 1, probe_session=session, layer=layer,
                                report_event=row['value'].get('event_name') if row.get('event') == 'telemetry_report'
                                and re.fullmatch(r'livesdk_[a-z_]{1,100}', row['value'].get('event_name', '')) else None,
                                monitor_event=data.get('event_key') if isinstance(data.get('event_key'), str)
                                and re.fullmatch(r'[a-z_]{1,60}', data['event_key']) else None,
                                monotonic_ns=row.get('monotonic_ns'), metrics=projected))
    return dict(schema_version=1, field_inventory=inventory, samples=samples, hook_coverage=coverage,
                capture_errors=errors,
                scope='Numeric projections only; all observed field names/types are inventoried. No backend mapping, automatic counter summing, A/V sync inference, or performance conclusion.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = extract([json.loads(s) for s in args.capture.read_text(encoding='utf-8-sig').splitlines() if s.strip()])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'samples': len(result['samples']), 'field_counts_by_layer':
                      {k: len(v) for k, v in result['field_inventory'].items()},
                      'capture_error_count': len(result['capture_errors']),
                      'hook_install_failures': sum(not v.get('installed') for v in result['hook_coverage'])}))
