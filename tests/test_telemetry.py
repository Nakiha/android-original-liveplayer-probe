import json
from pathlib import Path
import sys
import threading
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from extract_telemetry import extract
from capture_cleanup import detach_bounded
from telemetry_metrics import battery_temperature_c, duration_rates, legacy_sei_delay_ms, reference_seconds_per_100s, ui_histogram


class MetricSemantics(unittest.TestCase):
    def test_histogram_boundaries_and_units(self):
        value = ui_histogram({'0': 100, '1': 1, '2': 1, '3': 1, '6': 1, '7': 1, '13': 1, '14': 1}, 100)
        self.assertEqual(value['durations_ms'], dict(minor=30, slight=90, moderate=200, serious=140))
        self.assertEqual(value['ui_stall_count'], 5)
        self.assertEqual(value['ui_stall_duration_ms'], 430)
        self.assertAlmostEqual(duration_rates(1000, 430, 100)['video_ui_sum_rate'], 0.53)
        with self.assertRaises(ValueError):
            ui_histogram({'3': -1}, 60)

    def test_missing_sentinels_and_zero_window_are_not_zero_measurements(self):
        self.assertIsNone(battery_temperature_c(None))
        self.assertIsNone(duration_rates(0, 20, 30))
        self.assertIsNone(duration_rates(1000, -1, 0))
        self.assertIsNone(reference_seconds_per_100s(-1, 1000))
        self.assertEqual(reference_seconds_per_100s(1000, 100000), 1)
        self.assertEqual(battery_temperature_c(332), 33.2)

    def test_clock_offset_sign_and_negative_delay_are_preserved(self):
        self.assertEqual(legacy_sei_delay_ms(2000, 1500, 50), 550)
        self.assertEqual(legacy_sei_delay_ms(1000, 1500, 50), -450)

    def test_histogram_is_not_merged_with_video_interval_union(self):
        self.assertEqual(duration_rates(1000, 800, 800)['video_ui_sum_rate'], 1.6)


class CaptureProjection(unittest.TestCase):
    def test_blocked_frida_cleanup_has_a_deadline(self):
        release = threading.Event()
        class BlockedSession:
            def detach(self):
                release.wait(1)
        try:
            completed, errors = detach_bounded(BlockedSession(), timeout=0.01)
            self.assertFalse(completed)
            self.assertEqual(errors, [])
        finally:
            release.set()

    def test_layers_and_counter_windows_are_kept_separate_and_urls_removed(self):
        payload = {'event_key': 'playing', 'stall_time': 10, 'play_time': 20000,
                   'url': 'https://private.invalid/live?secret=1', 'room_id': '123456',
                   'live_sei_delay_error_code': -1, 'live_sei_delay_samples': -1,
                   'fps': 'NaN',
                   'live_sei_delay': [0, 0, 0]}
        rows = [{'event': 'telemetry_monitor', 'value': {'layer': layer, 'payload': payload}}
                for layer in ('native_observer', 'business_client_entry')]
        result = extract(rows)
        self.assertEqual(len(result['samples']), 2)
        self.assertEqual([r['metrics']['stall_time'] for r in result['samples']], [10, 10])
        self.assertEqual(result['samples'][0]['metrics']['live_sei_delay_error_code'], -1)
        self.assertNotIn('secret=1', json.dumps(result))
        self.assertNotIn('123456', json.dumps(result))
        self.assertNotIn('average_delay', json.dumps(result))
        self.assertNotIn('fps', result['samples'][0]['metrics'])

    def test_old_source_boxed_payload_and_probe_session_boundaries(self):
        payload = {'event_key': 'first_frame', 'total_cost': 15}
        rows = [{'event': 'official_callback', 'value': {'method': 'onMonitorLog', 'args':
                 [{'java_type': 'java.lang.String', 'value': json.dumps(payload)}, 'tag']}},
                {'event': 'probe_begin', 'value': ''},
                {'event': 'original_callback.onStatistics', 'value': [{'fps': 30, 'videoBufferMs': 200}]}]
        result = extract(rows)
        self.assertEqual(result['samples'][0]['metrics'], {'total_cost': 15})
        self.assertEqual(result['samples'][1]['probe_session'], 1)
        self.assertNotIn('audioBufferMs', result['samples'][1]['metrics'])


if __name__ == '__main__':
    unittest.main()
