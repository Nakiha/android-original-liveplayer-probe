"""Emit metadata/hash evidence for the locally supplied APK, never its code.

This checks declarations and hashes instruction bytes. It does not prove method
semantics, runtime branch coverage, native algorithms or dashboard definitions.
"""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


TARGETS = {
    'Lcom/ss/videoarch/liveplayer2/VeLivePlayerStatistics;': None,
    'Lcom/bytedance/android/livesdk/player/LiveSdkObserverClientImplProxy2;': ['onStatistics', 'onMonitorLog', 'onAudioRenderStall', 'onVideoRenderStall'],
    'Lcom/bytedance/android/livesdk/player/LivePlayerAdapter$setAppInfoFetcher$appInfoFetcher$2;': ['getAppInfo'],
    'Lcom/bytedance/android/livesdk/player/LivePlayerClient;': ['e', 'n'],
    'LX/1dAZ;': ['LIZ'],
    'LX/1dpf;': ['LIZ', '<init>'],
    'LX/1dj7;': ['LJI', 'LJII', 'LJIIIIZZ'],
    'LX/1djI;': ['LIZ'],
    'LX/1dzu;': ['LIZ'],
    'LX/1e3W;': ['LIZ'],
    'LX/1dj9;': ['run'],
    'LX/1dlH;': ['LJJLJ'],
    'LX/0BL2;': ['LLII', 'LJJIIJZLJL', 'LJJIJIIJI', 'LJLLI', 'LJJZZIII', 'LLIIIJ'],
}
KNOWN_APK = '86ee431640c9fe5035135092079af2dcddf947feb7e02cb78aaf459c821f80c0'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def inspect_dex(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected ordinary DEX')

    def uleb(offset):
        value, shift = 0, 0
        while True:
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if not byte & 128:
                return value, offset
            shift += 7

    ns, so = struct.unpack_from('<II', data, 56)
    _, to = struct.unpack_from('<II', data, 64)
    _, po = struct.unpack_from('<II', data, 72)
    _, fo = struct.unpack_from('<II', data, 80)
    _, mo = struct.unpack_from('<II', data, 88)
    nc, co = struct.unpack_from('<II', data, 96)
    strings = []
    for i in range(ns):
        start = struct.unpack_from('<I', data, so + 4 * i)[0]
        _, start = uleb(start)
        strings.append(data[start:data.index(0, start)].decode('utf-8', 'replace'))

    def typename(index):
        return strings[struct.unpack_from('<I', data, to + 4 * index)[0]]

    result = {}
    for i in range(nc):
        offset = co + 32 * i
        owner = typename(struct.unpack_from('<I', data, offset)[0])
        if owner not in TARGETS:
            continue
        position = struct.unpack_from('<I', data, offset + 24)[0]
        result[owner] = dict(fields=[], methods=[])
        if not position:
            continue
        counts = []
        for _ in range(4):
            value, position = uleb(position)
            counts.append(value)
        for count in counts[:2]:
            index = 0
            for _ in range(count):
                delta, position = uleb(position)
                index += delta
                flags, position = uleb(position)
                _, type_index, name_index = struct.unpack_from('<HHI', data, fo + 8 * index)
                result[owner]['fields'].append(dict(name=strings[name_index], type=typename(type_index), flags=flags))
        for count in counts[2:]:
            index = 0
            for _ in range(count):
                delta, position = uleb(position)
                index += delta
                flags, position = uleb(position)
                code, position = uleb(position)
                _, proto, name_index = struct.unpack_from('<HHI', data, mo + 8 * index)
                name = strings[name_index]
                if TARGETS[owner] is not None and name not in TARGETS[owner]:
                    continue
                _, return_type, parameters = struct.unpack_from('<III', data, po + 12 * proto)
                args = []
                if parameters:
                    n = struct.unpack_from('<I', data, parameters)[0]
                    args = [typename(struct.unpack_from('<H', data, parameters + 4 + 2 * j)[0]) for j in range(n)]
                method = dict(name=name, signature='(' + ''.join(args) + ')' + typename(return_type), flags=flags)
                if code:
                    n = struct.unpack_from('<I', data, code + 12)[0]
                    method.update(instruction_units=n, instruction_sha256=digest(data[code + 16:code + 16 + 2 * n]))
                result[owner]['methods'].append(method)
    return result


def inspect(apk):
    apk_hash = digest(apk.read_bytes())
    classes, native = {}, {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                data = archive.read(name)
                for owner, evidence in inspect_dex(data).items():
                    classes[owner] = dict(dex=name, dex_sha256=digest(data), **evidence)
            if name in ['lib/arm64-v8a/' + n for n in ('libliveplayer2.so', 'libliveplayer.so', 'libttmplayer.so')]:
                native[name] = digest(archive.read(name))
    return dict(schema_version=1, apk_sha256=apk_hash, matches_verified_profile=apk_hash == KNOWN_APK,
                classes=classes, native_sha256=native,
                missing_classes=sorted(set(TARGETS) - set(classes)),
                scope='Metadata/instruction hashes only. Semantic findings are separately documented and version-bound; native/dashboard formulas remain unverified unless explicitly stated.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = inspect(args.apk)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps({'matches_verified_profile': result['matches_verified_profile'],
                      'classes_found': len(result['classes']), 'missing_classes': result['missing_classes']}))
    if not result['matches_verified_profile'] or result['missing_classes']:
        raise SystemExit(1)
