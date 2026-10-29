"""Independent reference calculations, with evidence/scope in telemetry/README.md.

No backend/CUPED implementation is inferred. Missing/invalid inputs remain None.
"""
import math
import re


def valid_number(value):
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def battery_temperature_c(raw):
    return raw / 10.0 if valid_number(raw) else None


def legacy_sei_delay_ms(now_ms, timestamp_ms, active_clock_offset_ms=0):
    values = (now_ms, timestamp_ms, active_clock_offset_ms)
    return now_ms - timestamp_ms + active_clock_offset_ms if all(map(valid_number, values)) else None


def ui_histogram(histogram, refresh_hz=None):
    """40.7 Java cache: bucket k contributes k * count * frame_period_ms.

    k=1..2 is minor; k=3..6 slight, 7..13 moderate, >=14 serious.
    UI stall duration/count sum the latter three groups. The original cache
    also stores an all-bucket duration. A histogram is NOT a series of frame PTS.
    """
    period = 1000.0 / refresh_hz if valid_number(refresh_hz) and refresh_hz > 0 else 16.666668
    duration = dict(minor=0.0, slight=0.0, moderate=0.0, serious=0.0)
    counts = dict(minor=0, slight=0, moderate=0, serious=0)
    for key, count in histogram.items():
        if not re.fullmatch(r'\d+', str(key)):
            raise ValueError('Histogram buckets must be nonnegative integers')
        bucket = int(key)
        if bucket < 0 or not isinstance(count, int) or isinstance(count, bool) or count < 0:
            raise ValueError('Histogram buckets/counts must be nonnegative integers')
        if bucket == 0:
            continue
        group = 'minor' if bucket < 3 else 'slight' if bucket < 7 else 'moderate' if bucket < 14 else 'serious'
        duration[group] += bucket * count * period
        counts[group] += count
    return dict(frame_period_ms=period, durations_ms=duration, counts=counts,
                ui_stall_duration_ms=sum(duration[k] for k in ('slight', 'moderate', 'serious')),
                ui_stall_count=sum(counts[k] for k in ('slight', 'moderate', 'serious')),
                all_bucket_duration_ms=sum(duration.values()))


def duration_rates(window_ms, ui_ms, video_ms):
    """Reference ratios; do not clamp overlapping video/UI intervals to 1."""
    if not all(map(valid_number, (window_ms, ui_ms, video_ms))) or window_ms <= 0 or min(ui_ms, video_ms) < 0:
        return None
    return dict(ui_rate=ui_ms / window_ms, video_rate=video_ms / window_ms,
                video_ui_sum_rate=(ui_ms + video_ms) / window_ms)


def reference_seconds_per_100s(stall_ms, watch_ms):
    """Conventional normalized duration, NOT the customer's verified formula.

    Aggregate comparable, nonoverlapping raw numerators/denominators BEFORE
    calling this. Do not average session ratios or sum cumulative snapshots.
    """
    if not all(map(valid_number, (stall_ms, watch_ms))) or watch_ms <= 0 or stall_ms < 0:
        return None
    return 100.0 * stall_ms / watch_ms
