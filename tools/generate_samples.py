"""Generate owned pattern/tone inputs. Requires FFmpeg with libx264 and libx265."""
import argparse
from pathlib import Path
import subprocess


def legacy_hevc(data):
    """Repackage enhanced hvc1 tags as experimental FLV CodecID 12; no transcoding.

    This is a compatibility test variant, not evidence of an online service's format.
    """
    result = bytearray(data[:13])
    pos = 13
    while pos + 15 <= len(data):
        header = bytearray(data[pos:pos + 11])
        size = int.from_bytes(header[1:4], 'big')
        if pos + 15 + size > len(data):
            raise ValueError('Truncated FLV tag')
        payload = data[pos + 11:pos + 11 + size]
        if header[0] == 9 and payload[0] & 128:
            packet = payload[0] & 15
            if payload[1:5] != b'hvc1':
                raise ValueError('Expected hvc1 fourcc')
            if packet == 4:
                # Enhanced color metadata has no legacy equivalent in this SDR test.
                pos += 15 + size
                continue
            frame = (payload[0] >> 4) & 7
            if packet in (0, 2):
                payload = bytes([frame << 4 | 12, packet, 0, 0, 0]) + payload[5:]
            elif packet == 1:
                payload = bytes([frame << 4 | 12, 1]) + payload[5:]
            elif packet == 3:
                payload = bytes([frame << 4 | 12, 1, 0, 0, 0]) + payload[5:]
            else:
                raise ValueError(f'Unsupported enhanced packet {packet}')
        header[1:4] = len(payload).to_bytes(3, 'big')
        result += header + payload + (11 + len(payload)).to_bytes(4, 'big')
        pos += 15 + size
    if pos != len(data):
        raise ValueError('Trailing partial FLV tag')
    return bytes(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--output', type=Path, default=Path('samples/generated'))
    parser.add_argument('--vvc', action='store_true', help='Also generate standard vvc1 enhanced-FLV compatibility input with libvvenc')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    for codec, encoder, preset in [('h264', 'libx264', 'veryfast'), ('h265', 'libx265', 'fast')]:
        target = args.output / (codec + '.flv')
        extra = ['-tune', 'zerolatency'] if codec == 'h264' else ['-x265-params', 'log-level=error:repeat-headers=1']
        subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'warning', '-y',
                        '-f', 'lavfi', '-i', 'testsrc2=size=640x360:rate=30',
                        '-f', 'lavfi', '-i', 'sine=frequency=1000:sample_rate=48000',
                        '-t', '10', '-c:v', encoder, '-preset', preset, *extra,
                        '-b:v', '800k', '-maxrate', '800k', '-bufsize', '800k',
                        '-g', '30', '-bf', '0', '-pix_fmt', 'yuv420p',
                        '-c:a', 'aac', '-b:a', '64k', '-f', 'flv', str(target)], check=True)
        if codec == 'h265':
            enhanced = args.output / 'h265-enhanced.flv'
            enhanced.write_bytes(target.read_bytes())
            target.write_bytes(legacy_hevc(enhanced.read_bytes()))
    if args.vvc:
        subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'warning', '-y',
                        '-f', 'lavfi', '-i', 'testsrc2=size=640x360:rate=30',
                        '-f', 'lavfi', '-i', 'sine=frequency=1000:sample_rate=48000',
                        '-t', '5', '-c:v', 'libvvenc', '-preset', 'faster', '-b:v', '800k',
                        '-g', '30', '-pix_fmt', 'yuv420p10le', '-c:a', 'aac', '-b:a', '64k',
                        '-f', 'flv', str(args.output / 'h266-enhanced.flv')], check=True)
    print('Generated test inputs. These are compatibility controls, not online experiment streams.')


if __name__ == '__main__':
    main()
