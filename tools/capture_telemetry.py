"""Capture version-bound original player/business telemetry through Java hooks.

Uses capture_liveplayer's private JSONL transport. Does not change statistics
settings or replace JNI methods; observe callbacks which the app already emits.
"""
from capture_liveplayer import main

if __name__ == '__main__':
    main('observe_telemetry.js')
