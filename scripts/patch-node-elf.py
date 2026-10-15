#!/usr/bin/env python3
"""Rename the Termux Node executable and its ELF dependencies for jniLibs."""

import os
import shutil
import struct
import sys

SONAME_RENAMES = {
    "libz.so.1": "libz.so",
    "libicui18n.so.78": "libicui18n.so",
    "libicuuc.so.78": "libicuuc.so",
    "libicudata.so.78": "libicudata.so",
    "libssl.so.3": "libssl.so",
    "libcrypto.so.3": "libcrypto.so",
}

FILE_RENAMES = {
    "libz.so.1.3.2": "libz.so",
    "libsqlite3.so.3.54.0": "libsqlite3.so",
    "libicui18n.so.78.3": "libicui18n.so",
    "libicuuc.so.78.3": "libicuuc.so",
    "libicudata.so.78.3": "libicudata.so",
    "libssl.so.3": "libssl.so",
    "libcrypto.so.3": "libcrypto.so",
}


def patch_dynamic_strings(path):
    with open(path, "rb") as source:
        data = bytearray(source.read())
    if data[:4] != b"\x7fELF":
        return 0
    is_64_bit = data[4] == 2
    endian = "<" if data[5] == 1 else ">"
    if is_64_bit:
        program_offset, = struct.unpack_from(endian + "Q", data, 0x20)
        entry_size, entry_count = struct.unpack_from(endian + "HH", data, 0x36)
    else:
        program_offset, = struct.unpack_from(endian + "I", data, 0x1C)
        entry_size, entry_count = struct.unpack_from(endian + "HH", data, 0x2A)

    load_segments = []
    dynamic_segment = None
    for index in range(entry_count):
        offset = program_offset + index * entry_size
        segment_type, = struct.unpack_from(endian + "I", data, offset)
        if is_64_bit:
            file_offset, = struct.unpack_from(endian + "Q", data, offset + 0x08)
            virtual_address, = struct.unpack_from(endian + "Q", data, offset + 0x10)
            file_size, = struct.unpack_from(endian + "Q", data, offset + 0x20)
        else:
            file_offset, = struct.unpack_from(endian + "I", data, offset + 0x04)
            virtual_address, = struct.unpack_from(endian + "I", data, offset + 0x08)
            file_size, = struct.unpack_from(endian + "I", data, offset + 0x10)
        if segment_type == 1:
            load_segments.append((virtual_address, file_offset, file_size))
        elif segment_type == 2:
            dynamic_segment = (file_offset, file_size)

    if dynamic_segment is None:
        return 0

    def file_offset_for(virtual_address):
        for start, file_offset, file_size in load_segments:
            if start <= virtual_address < start + file_size:
                return file_offset + virtual_address - start
        return None

    string_table = None
    string_table_size = None
    step = 16 if is_64_bit else 8
    for offset in range(dynamic_segment[0], sum(dynamic_segment), step):
        tag, value = (
            struct.unpack_from(endian + "qQ", data, offset)
            if is_64_bit
            else struct.unpack_from(endian + "iI", data, offset)
        )
        if tag == 0:
            break
        if tag == 5:
            string_table = value
        elif tag == 10:
            string_table_size = value

    if string_table is None or string_table_size is None:
        return 0
    start = file_offset_for(string_table)
    if start is None:
        return 0
    region = bytes(data[start : start + string_table_size])
    replacement_count = 0
    for old_name, new_name in SONAME_RENAMES.items():
        needle = old_name.encode() + b"\x00"
        replacement = new_name.encode() + b"\x00" * (len(old_name) - len(new_name) + 1)
        count = region.count(needle)
        region = region.replace(needle, replacement)
        replacement_count += count
    if replacement_count:
        data[start : start + string_table_size] = region
        with open(path, "wb") as destination:
            destination.write(data)
    return replacement_count


def main():
    source_root, target_root = sys.argv[1:3]
    os.makedirs(target_root, exist_ok=True)
    artifacts = [("node", "libnodebin.so"), *FILE_RENAMES.items()]
    artifacts.extend((name, name) for name in ("libcares.so", "libc++_shared.so"))
    for source_name, target_name in artifacts:
        source = os.path.join(source_root, "lib", source_name)
        if source_name == "node":
            source = os.path.join(source_root, source_name)
        if not os.path.isfile(source):
            raise SystemExit(f"missing required Node runtime artifact: {source}")
        target = os.path.join(target_root, target_name)
        shutil.copy2(source, target)
        print(f"{source_name} -> {target_name} ({os.path.getsize(target) / 1024 / 1024:.1f} MB)")
    for name in sorted(os.listdir(target_root)):
        replacements = patch_dynamic_strings(os.path.join(target_root, name))
        if replacements:
            print(f"patched {name}: {replacements} dynamic string entries")


if __name__ == "__main__":
    main()
