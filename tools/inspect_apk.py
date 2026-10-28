"""Reproducible, read-only APK media inventory. No application code is executed."""
import argparse
import hashlib
import json
import re
import struct
import zipfile
from pathlib import Path


class Elf64:
    def __init__(self, data):
        self.data = data
        if data[:6] != b"\x7fELF\x02\x01":
            raise ValueError("Expected little-endian ELF64")
        phoff, shoff = struct.unpack_from("<QQ", data, 32)
        phsize, phcount, shsize, shcount, shstrings = struct.unpack_from("<HHHHH", data, 54)
        self.loads = []
        for i in range(phcount):
            kind, flags, off, va, pa, size, memsize, align = struct.unpack_from("<IIQQQQQQ", data, phoff + i * phsize)
            if kind == 1:
                self.loads.append((va, off, size))
        raw_sections = [struct.unpack_from("<IIQQQQIIQQ", data, shoff + i * shsize) for i in range(shcount)]
        h = raw_sections[shstrings]
        names = data[h[4]:h[4] + h[5]]
        self.sections = {}
        self.relative = {}
        for h in raw_sections:
            name = names[h[0]:names.index(0, h[0])].decode()
            self.sections[name] = h
            if h[1] == 4 and h[9]:
                for off in range(h[4], h[4] + h[5], h[9]):
                    target, info, addend = struct.unpack_from("<QQq", data, off)
                    if info & 0xffffffff == 1027:  # R_AARCH64_RELATIVE
                        self.relative[target] = addend

    def offset(self, address):
        for va, off, size in self.loads:
            if va <= address < va + size:
                return off + address - va
        raise ValueError(hex(address))

    def cstring(self, address):
        try:
            off = self.offset(address)
            return self.data[off:self.data.index(0, off)].decode("ascii")
        except (ValueError, UnicodeDecodeError):
            return ""

    def needed(self):
        dyn = self.sections.get(".dynamic")
        strings = self.sections.get(".dynstr")
        if not dyn or not strings:
            return []
        result = []
        for off in range(dyn[4], dyn[4] + dyn[5], 16):
            tag, value = struct.unpack_from("<qQ", self.data, off)
            if tag == 1:
                pos = strings[4] + value
                result.append(self.data[pos:self.data.index(0, pos)].decode())
        return result

    def jni_methods(self):
        result = []
        for target, addend in self.relative.items():
            name = self.cstring(addend)
            if not re.fullmatch(r"_?[A-Za-z][A-Za-z0-9_]*", name):
                continue
            signature = self.cstring(self.relative.get(target + 8, 0))
            function = self.relative.get(target + 16, 0)
            if signature.startswith("(") and function:
                result.append(dict(name=name, signature=signature,
                                   function_va=hex(function), table_entry_va=hex(target)))
        return result


def dex_inventory(data):
    if not data.startswith(b"dex\n"):
        raise ValueError("Expected ordinary DEX")
    nstrings, strings_off = struct.unpack_from("<II", data, 56)
    ntypes, types_off = struct.unpack_from("<II", data, 64)
    nclasses, classes_off = struct.unpack_from("<II", data, 96)
    strings = []
    for i in range(nstrings):
        pos = struct.unpack_from("<I", data, strings_off + i * 4)[0]
        while data[pos] & 128:
            pos += 1
        pos += 1
        strings.append(data[pos:data.index(0, pos)].decode("utf-8", "replace"))
    classes = []
    for i in range(nclasses):
        type_idx = struct.unpack_from("<I", data, classes_off + i * 32)[0]
        string_idx = struct.unpack_from("<I", data, types_off + type_idx * 4)[0]
        classes.append(strings[string_idx])
    return classes, strings


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = args.output
    root.mkdir(parents=True, exist_ok=True)
    (root / "native").mkdir(exist_ok=True)
    (root / "dex").mkdir(exist_ok=True)
    keywords = re.compile(r"vvc|266|flv|rtmp|quic|buffer|avsync|stall|video.clock|audio.clock", re.I)
    media_names = re.compile(r"ttmplayer|ttffmpeg|liveplayer|livestrategy|bytevc2|bvcparser", re.I)
    hashes = {}
    dependencies = {}
    classes = []
    findings = {}
    non_elf = {}
    with zipfile.ZipFile(args.apk) as archive:
        names = archive.namelist()
        (root / "apk-file-list.txt").write_text("\n".join(names), encoding="utf-8")
        for name in names:
            if name.startswith("lib/arm64-v8a/") and name.endswith(".so"):
                data = archive.read(name)
                filename = name.rsplit("/", 1)[-1]
                hashes[filename] = hashlib.sha256(data).hexdigest()
                if data[:6] != b"\x7fELF\x02\x01":
                    non_elf[filename] = dict(size=len(data), first_16_bytes_hex=data[:16].hex())
                    continue
                elf = Elf64(data)
                dependencies[filename] = elf.needed()
                if media_names.search(filename):
                    (root / "native" / filename).write_bytes(data)
                    evidence = [dict(offset_hex=hex(m.start()), text=m.group().decode("ascii"))
                                for m in re.finditer(rb"[\x20-\x7e]{5,}", data)
                                if keywords.search(m.group().decode("ascii"))]
                    findings[filename] = evidence
                    (root / (filename + ".jni.json")).write_text(json.dumps(elf.jni_methods(), indent=2), encoding="utf-8")
            elif re.fullmatch(r"classes\d*\.dex", name):
                data = archive.read(name)
                defined, strings = dex_inventory(data)
                media = [c for c in defined if c.startswith(("Lcom/ss/videoarch/liveplayer/",
                         "Lcom/ss/videoarch/liveplayer2/", "Lcom/ss/videoarch/nliveplayer/",
                         "Lcom/ss/ttm/player/"))]
                if media:
                    (root / "dex" / name).write_bytes(data)
                    classes.extend(dict(dex=name, name=c) for c in media)
        archive_libs = set(dependencies)
        seeds = [n for n in archive_libs if media_names.search(n)]
        closure, pending = set(), list(seeds)
        while pending:
            name = pending.pop()
            if name in closure:
                continue
            closure.add(name)
            pending.extend(n for n in dependencies[name] if n in archive_libs)
        for name in closure:
            path = root / "native" / name
            if not path.exists():
                path.write_bytes(archive.read("lib/arm64-v8a/" + name))
    result = dict(apk_sha256=hashlib.file_digest(args.apk.open("rb"), "sha256").hexdigest(),
                  native_sha256=hashes, needed=dependencies,
                  non_elf_so_entries=non_elf,
                  media_seed=sorted(seeds), media_dependency_closure=sorted(closure),
                  media_java_classes=classes,
                  limitations=["Static evidence only; dynamic dlopen dependencies can be absent from DT_NEEDED.",
                               "JNI table entries are candidates inferred from ARM64 relocations.",
                               "No claim of runtime codec selection or playback equivalence."])
    (root / "inventory.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    (root / "native-strings.json").write_text(json.dumps(findings, indent=2), encoding="utf-8")
    print(json.dumps(dict(media_dependency_closure=sorted(closure), media_class_count=len(classes),
                          media_dex=sorted({c['dex'] for c in classes})), indent=2))


if __name__ == "__main__":
    main()
