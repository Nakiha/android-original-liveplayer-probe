"""Validate original-player reflective contracts without executing Android code."""
import argparse
import hashlib
import json
import struct
import zipfile
from pathlib import Path

from inspect_apk import Elf64, dex_inventory

PACKAGE = "Lcom/ss/videoarch/liveplayer2/"
ENGINE = PACKAGE + "VeLivePlayer;"
CONFIG = PACKAGE + "VeLivePlayerConfiguration;"
OBSERVER = PACKAGE + "VeLivePlayerObserver;"
CONTRACT = {
    ENGINE: [
        ("<init>", "(Landroid/content/Context;)V"),
        ("loadSystemLivePlayer", "()Z"),
        ("getVersion", "()Ljava/lang/String;"),
        ("getPlayerId", "()I"),
        ("setConfig", "(" + CONFIG + ")V"),
        ("setObserver", "(" + OBSERVER + ")V"),
        ("setSurface", "(Landroid/view/Surface;Z)V"),
        ("setPlayUrl", "(Ljava/lang/String;)V"),
        ("play", "()V"),
        ("stop", "()V"),
        ("destroy", "()V"),
        ("cpp_initWithContext", "(ILandroid/content/Context;)I"),
        ("cpp_setPlayUrl", "(ILjava/lang/String;)V"),
    ],
    CONFIG: [("<init>", "()V")],
    OBSERVER: [("onStatistics", "(" + ENGINE + PACKAGE + "VeLivePlayerStatistics;)V"),
               ("onMonitorLog", "(" + ENGINE + "Ljava/lang/String;Ljava/lang/String;)V")],
}


def declared_methods(data, interested):
    nstrings, strings_off = struct.unpack_from("<II", data, 56)
    _, types_off = struct.unpack_from("<II", data, 64)
    _, protos_off = struct.unpack_from("<II", data, 72)
    _, methods_off = struct.unpack_from("<II", data, 88)
    nclasses, classes_off = struct.unpack_from("<II", data, 96)

    def uleb(pos):
        value, shift = 0, 0
        while True:
            byte = data[pos]
            pos += 1
            value |= (byte & 127) << shift
            if not byte & 128:
                return value, pos
            shift += 7

    strings = []
    for i in range(nstrings):
        pos = struct.unpack_from("<I", data, strings_off + 4 * i)[0]
        _, pos = uleb(pos)
        strings.append(data[pos:data.index(0, pos)].decode("utf-8", "replace"))

    def typename(idx):
        return strings[struct.unpack_from("<I", data, types_off + 4 * idx)[0]]

    result = {}
    for i in range(nclasses):
        start = classes_off + 32 * i
        class_idx = struct.unpack_from("<I", data, start)[0]
        owner = typename(class_idx)
        if owner not in interested:
            continue
        pos = struct.unpack_from("<I", data, start + 24)[0]
        found = set()
        result[owner] = found
        if not pos:
            continue
        counts = []
        for _ in range(4):
            count, pos = uleb(pos)
            counts.append(count)
        for _ in range(counts[0] + counts[1]):
            _, pos = uleb(pos)
            _, pos = uleb(pos)
        for count in counts[2:]:
            method_idx = 0
            for _ in range(count):
                delta, pos = uleb(pos)
                method_idx += delta
                flags, pos = uleb(pos)
                _, pos = uleb(pos)
                _, proto_idx, name_idx = struct.unpack_from("<HHI", data, methods_off + 8 * method_idx)
                _, return_idx, params_off = struct.unpack_from("<III", data, protos_off + 12 * proto_idx)
                params = []
                if params_off:
                    size = struct.unpack_from("<I", data, params_off)[0]
                    params = [typename(struct.unpack_from("<H", data, params_off + 4 + 2 * j)[0]) for j in range(size)]
                found.add((strings[name_idx], "(" + "".join(params) + ")" + typename(return_idx)))
    return result


def run(apk):
    declarations = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.startswith("classes") and name.endswith(".dex"):
                data = archive.read(name)
                classes, _ = dex_inventory(data)
                if set(CONTRACT).intersection(classes):
                    declarations.update(declared_methods(data, CONTRACT))
        checks = []
        for owner, methods in CONTRACT.items():
            for name, signature in methods:
                checks.append(dict(owner=owner, method=name, signature=signature,
                                   present=(name, signature) in declarations.get(owner, set())))
        native_checks = []
        for filename, expected in {
            "libliveplayer2.so": [("cpp_initWithContext", "(ILandroid/content/Context;)I"),
                                  ("cpp_setPlayUrl", "(ILjava/lang/String;)V"),
                                  ("cpp_setObserver", "(IL" + OBSERVER[1:] + ")V")],
        }.items():
            path = "lib/arm64-v8a/" + filename
            if path not in archive.namelist():
                entries = set()
            else:
                elf = Elf64(archive.read(path))
                entries = {(x['name'], x['signature']) for x in elf.jni_methods()}
            for method, signature in expected:
                native_checks.append(dict(library=filename, method=method, signature=signature,
                                          present=(method, signature) in entries))
        digest = hashlib.file_digest(apk.open("rb"), "sha256").hexdigest()
        return dict(apk_sha256=digest, checks=checks, jni_checks=native_checks,
                    passed=all(x['present'] for x in checks + native_checks),
                    scope="Static DEX declarations and ELF JNI candidates only. No Java initialization, dlopen, decoding, or network playback executed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = run(args.apk)
    output = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    print(output)
    raise SystemExit(0 if report['passed'] else 1)
