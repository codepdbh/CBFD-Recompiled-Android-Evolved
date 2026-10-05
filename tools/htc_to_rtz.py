#!/usr/bin/env python3
"""Turns a GLideN64 hi-res texture cache (.htc) into a texture pack the game loads (.rtz).

GLideN64 keeps a pack's textures in its .htc cache by their Rice hash (texture and palette CRCs,
format and size). The .rtz names each one after that hash, Rice style, and RT64 matches them by
the Rice hashes it computes for the textures as the game loads them (common/rt64_rice_hash.h).

    python tools/htc_to_rtz.py CONKER.BFD_HIRESTEXTURES.htc conker_hires_textures.rtz --max-size 1024

--max-size shrinks bigger textures (0 keeps them as they are): phones have far less memory for
textures than PCs.
"""

import argparse
import concurrent.futures
import gzip
import io
import json
import struct
import sys
import zipfile

from PIL import Image

HEADER = struct.Struct("<QIIIHHBHI")  # key, width, height, format, texture format, pixel type, hires, N64 format/size, data size
GL_RGBA8 = 0x8058


def entries(path):
    """The cache's textures: (key, width, height, N64 format/size, RGBA8 bytes)."""
    with gzip.open(path, "rb") as htc:
        version, config = struct.unpack("<iI", htc.read(8))
        if version != 0x08000000:
            sys.exit(f"Unknown .htc version 0x{version:08X} (only GLideN64's version 8 is known).")
        while True:
            header = htc.read(HEADER.size)
            if len(header) < HEADER.size:
                return
            key, width, height, gl_format, _, _, _, format_size, size = HEADER.unpack(header)
            data = htc.read(size)
            if gl_format != GL_RGBA8 or size != width * height * 4:
                print(f"Skipped {key:016x}: format 0x{gl_format:X}, {size} bytes for {width}x{height}.")
                continue
            yield key, width, height, format_size, data


def name(key, format_size):
    """Rice style: <game>#<texture crc>#<format>#<size>[#<palette crc>]_all.png"""
    texture_crc = key & 0xFFFFFFFF
    palette_crc = key >> 32
    fmt = format_size & 0xFF
    siz = format_size >> 8
    rice = f"{texture_crc:08X}#{fmt}#{siz}"
    if palette_crc:
        rice += f"#{palette_crc:08X}"
    return f"textures/CONKER#{rice}_all.png"


def encode(args):
    path, width, height, data, max_size = args
    image = Image.frombytes("RGBA", (width, height), data)
    if max_size and max(width, height) > max_size:
        scale = max_size / max(width, height)
        image = image.resize((max(1, round(width * scale)), max(1, round(height * scale))), Image.LANCZOS)
    out = io.BytesIO()
    image.save(out, "PNG", compress_level=6)
    return path, out.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("htc")
    parser.add_argument("rtz")
    parser.add_argument("--max-size", type=int, default=1024, help="largest side, in pixels (0: no limit)")
    args = parser.parse_args()

    database = {
        "configuration": {"autoPath": "rice", "configurationVersion": 2, "hashVersion": 5},
        "textures": [],
        "operationFilters": [],
        "shiftFilters": [],
        "extraFiles": [],
    }

    count = 0
    with zipfile.ZipFile(args.rtz, "w", zipfile.ZIP_STORED) as rtz, concurrent.futures.ProcessPoolExecutor() as pool:
        rtz.writestr("rt64.json", json.dumps(database, indent=4))
        pending = set()
        for key, width, height, format_size, data in entries(args.htc):
            pending.add(pool.submit(encode, (name(key, format_size), width, height, data, args.max_size)))
            # Not too many textures in memory at once.
            if len(pending) >= 32:
                done, pending = concurrent.futures.wait(pending, return_when=concurrent.futures.FIRST_COMPLETED)
                for future in done:
                    rtz.writestr(*future.result())
                    count += 1
        for future in concurrent.futures.as_completed(pending):
            rtz.writestr(*future.result())
            count += 1

    print(f"{count} textures in {args.rtz}.")


if __name__ == "__main__":
    main()
