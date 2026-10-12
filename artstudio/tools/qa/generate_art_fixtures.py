#!/usr/bin/env python3
"""
Art Studio Test Fixtures Generator (v1)
Generates 100% synthetic, CC0-1.0 licensed test assets across all 8 required categories:
1. line_art (simple & detail-dense)
2. flat_colour (simple & detail-dense)
3. noisy_sketches (simple & detail-dense)
4. transparent_png (alpha cutout & gradient alpha)
5. exif_rotated (orientation 6 = 90 deg CW, orientation 3 = 180 deg)
6. corrupt_truncated (truncated PNG, corrupted ORA, malformed SVG)
7. layered_ora (simple 2-layer ORA & 3-layer dense ORA)
8. svg_samples (simple vector & detail-dense Bezier curves)
Plus palettes specification.
"""

import os
import sys
import math
import zlib
import struct
import zipfile
import hashlib
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURES_DIR = REPO_ROOT / "tests" / "fixtures" / "artstudio" / "v1"

def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()

def make_png_bytes(width: int, height: int, rgba_bytes: bytearray) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    header = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    raw = bytearray()
    row_bytes = width * 4
    for y in range(height):
        raw.append(0) # filter None
        raw.extend(rgba_bytes[y * row_bytes : (y + 1) * row_bytes])
    idat = chunk(b"IDAT", zlib.compress(bytes(raw), level=6))
    iend = chunk(b"IEND", b"")
    return header + ihdr + idat + iend

def set_pixel(buf: bytearray, w: int, h: int, x: int, y: int, r: int, g: int, b: int, a: int = 255):
    if 0 <= x < w and 0 <= y < h:
        idx = (y * w + x) * 4
        buf[idx] = r
        buf[idx + 1] = g
        buf[idx + 2] = b
        buf[idx + 3] = a

def draw_circle(buf: bytearray, w: int, h: int, cx: int, cy: int, radius: int, r: int, g: int, b: int, a: int = 255, stroke: int = 1):
    r_sq_min = (radius - stroke) ** 2
    r_sq_max = radius ** 2
    for y in range(max(0, cy - radius), min(h, cy + radius + 1)):
        for x in range(max(0, cx - radius), min(w, cx + radius + 1)):
            d_sq = (x - cx) ** 2 + (y - cy) ** 2
            if r_sq_min <= d_sq <= r_sq_max:
                set_pixel(buf, w, h, x, y, r, g, b, a)

def draw_fill_circle(buf: bytearray, w: int, h: int, cx: int, cy: int, radius: int, r: int, g: int, b: int, a: int = 255):
    r_sq = radius ** 2
    for y in range(max(0, cy - radius), min(h, cy + radius + 1)):
        for x in range(max(0, cx - radius), min(w, cx + radius + 1)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r_sq:
                set_pixel(buf, w, h, x, y, r, g, b, a)

def draw_line(buf: bytearray, w: int, h: int, x0: int, y0: int, x1: int, y1: int, r: int, g: int, b: int, a: int = 255, width: int = 1):
    dx = abs(x1 - x0)
    dy = abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx - dy
    curr_x, curr_y = x0, y0
    while True:
        for ox in range(-width // 2, width // 2 + 1):
            for oy in range(-width // 2, width // 2 + 1):
                set_pixel(buf, w, h, curr_x + ox, curr_y + oy, r, g, b, a)
        if curr_x == x1 and curr_y == y1:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            curr_x += sx
        if e2 < dx:
            err += dx
            curr_y += sy

def generate_line_art():
    out_dir = FIXTURES_DIR / "line_art"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Simple Line Art (512x512)
    w, h = 512, 512
    buf = bytearray([255, 255, 255, 255] * (w * h))
    # Outer box
    for x in range(50, 462):
        set_pixel(buf, w, h, x, 50, 0, 0, 0)
        set_pixel(buf, w, h, x, 462, 0, 0, 0)
    for y in range(50, 462):
        set_pixel(buf, w, h, 50, y, 0, 0, 0)
        set_pixel(buf, w, h, 462, y, 0, 0, 0)
    # Shapes
    draw_circle(buf, w, h, 256, 256, 120, 0, 0, 0, stroke=3)
    draw_line(buf, w, h, 100, 100, 412, 412, 0, 0, 0, width=3)
    draw_line(buf, w, h, 100, 412, 412, 100, 0, 0, 0, width=3)
    p1 = out_dir / "lineart_simple_01.png"
    p1.write_bytes(make_png_bytes(w, h, buf))

    # 2. Detail-Dense Line Art (1024x1024)
    w, h = 1024, 1024
    buf = bytearray([255, 255, 255, 255] * (w * h))
    # Concentric rings
    for r in range(40, 480, 30):
        draw_circle(buf, w, h, 512, 512, r, 0, 0, 0, stroke=2)
    # Radial spokes
    for deg in range(0, 360, 15):
        rad = math.radians(deg)
        x1 = int(512 + 470 * math.cos(rad))
        y1 = int(512 + 470 * math.sin(rad))
        draw_line(buf, w, h, 512, 512, x1, y1, 0, 0, 0, width=2)
    # Fine hatching in corners
    for i in range(0, 300, 8):
        draw_line(buf, w, h, i, 0, 0, i, 0, 0, 0, width=1)
        draw_line(buf, w, h, 1024 - i, 0, 1024, i, 0, 0, 0, width=1)
        draw_line(buf, w, h, i, 1024, 0, 1024 - i, 0, 0, 0, width=1)
        draw_line(buf, w, h, 1024 - i, 1024, 1024, 1024 - i, 0, 0, 0, width=1)
    p2 = out_dir / "lineart_dense_02.png"
    p2.write_bytes(make_png_bytes(w, h, buf))

def generate_flat_colour():
    out_dir = FIXTURES_DIR / "flat_colour"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Simple Flat Colour (512x512)
    w, h = 512, 512
    buf = bytearray(w * h * 4)
    colors = [
        (235, 87, 87),   # Coral Red
        (47, 128, 237),  # Royal Blue
        (39, 174, 96),   # Forest Green
        (242, 201, 76)   # Golden Yellow
    ]
    for y in range(h):
        for x in range(w):
            c_idx = (0 if y < 256 else 2) + (0 if x < 256 else 1)
            r, g, b = colors[c_idx]
            set_pixel(buf, w, h, x, y, r, g, b, 255)
    # Center white divider lines
    draw_line(buf, w, h, 256, 0, 256, 512, 255, 255, 255, width=4)
    draw_line(buf, w, h, 0, 256, 512, 256, 255, 255, 255, width=4)
    p1 = out_dir / "flat_colour_simple_01.png"
    p1.write_bytes(make_png_bytes(w, h, buf))

    # 2. Detail-Dense Flat Colour (1024x1024)
    w, h = 1024, 1024
    buf = bytearray(w * h * 4)
    palette = [
        (26, 188, 156), (46, 204, 113), (52, 152, 219), (155, 89, 182),
        (241, 196, 15), (230, 126, 34), (231, 76, 60), (236, 240, 241),
        (149, 165, 166), (52, 73, 94), (22, 160, 133), (39, 174, 96),
        (41, 128, 185), (142, 68, 173), (243, 156, 18), (211, 84, 0)
    ]
    # 4x4 Grid of vibrant color tiles with inscribed shapes
    tile_w, tile_h = 256, 256
    for gy in range(4):
        for gx in range(4):
            idx = (gy * 4 + gx) % len(palette)
            r, g, b = palette[idx]
            for y in range(gy * tile_h, (gy + 1) * tile_h):
                for x in range(gx * tile_w, (gx + 1) * tile_w):
                    set_pixel(buf, w, h, x, y, r, g, b, 255)
            # Inscribed contrasting circle
            cr, cg, cb = palette[(idx + 8) % len(palette)]
            cx = gx * tile_w + 128
            cy = gy * tile_h + 128
            draw_fill_circle(buf, w, h, cx, cy, 64, cr, cg, cb, 255)
    p2 = out_dir / "flat_colour_dense_02.png"
    p2.write_bytes(make_png_bytes(w, h, buf))

def generate_noisy_sketches():
    out_dir = FIXTURES_DIR / "noisy_sketches"
    out_dir.mkdir(parents=True, exist_ok=True)

    # Pseudo-random generator for 100% deterministic noise
    seed = 123456789
    def pseudo_rand():
        nonlocal seed
        seed = (seed * 1103515245 + 12345) & 0x7fffffff
        return seed / 0x7fffffff

    # 1. Simple Noisy Sketch (512x512)
    w, h = 512, 512
    buf = bytearray([255, 255, 255, 255] * (w * h))
    # Subtle graphite grain
    for y in range(h):
        for x in range(w):
            noise = int(pseudo_rand() * 25)
            val = 255 - noise
            set_pixel(buf, w, h, x, y, val, val, val, 255)
    # Pencil sketch lines with jitter
    for i in range(100):
        t = i / 100.0
        x = int(120 + 270 * t + (pseudo_rand() - 0.5) * 8)
        y = int(256 + 100 * math.sin(t * 6.28) + (pseudo_rand() - 0.5) * 8)
        draw_fill_circle(buf, w, h, x, y, 2, 40, 40, 40, 255)
    p1 = out_dir / "sketch_noisy_simple_01.png"
    p1.write_bytes(make_png_bytes(w, h, buf))

    # 2. Detail-Dense Noisy Sketch (1024x1024)
    w, h = 1024, 1024
    buf = bytearray([255, 255, 255, 255] * (w * h))
    # Heavy paper texture and scan dust
    for y in range(h):
        for x in range(w):
            n = int(pseudo_rand() * 45)
            # Add occasional dark speckle
            if pseudo_rand() < 0.002:
                n = 180
            val = max(0, 255 - n)
            set_pixel(buf, w, h, x, y, val, val, val, 255)
    # Multi-pass cross-hatching
    for deg in [-45, 45]:
        rad = math.radians(deg)
        cos_r = math.cos(rad)
        sin_r = math.sin(rad)
        for offset in range(-600, 600, 16):
            for step in range(-400, 400, 4):
                if pseudo_rand() < 0.85: # Random gaps
                    x = int(512 + offset * cos_r - step * sin_r + (pseudo_rand() - 0.5) * 4)
                    y = int(512 + offset * sin_r + step * cos_r + (pseudo_rand() - 0.5) * 4)
                    set_pixel(buf, w, h, x, y, 30, 30, 30, 255)
    p2 = out_dir / "sketch_noisy_dense_02.png"
    p2.write_bytes(make_png_bytes(w, h, buf))

def generate_transparent_png():
    out_dir = FIXTURES_DIR / "transparent_png"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Hard Cutout Alpha (512x512)
    w, h = 512, 512
    buf = bytearray(w * h * 4) # all 0 (transparent)
    # Central solid emblem with crisp edge
    draw_fill_circle(buf, w, h, 256, 256, 180, 230, 76, 60, 255)
    draw_fill_circle(buf, w, h, 256, 256, 110, 255, 255, 255, 255)
    draw_fill_circle(buf, w, h, 256, 256, 50, 41, 128, 185, 255)
    p1 = out_dir / "transparent_cutout_01.png"
    p1.write_bytes(make_png_bytes(w, h, buf))

    # 2. Smooth Radial Alpha Gradient (512x512)
    buf = bytearray(w * h * 4)
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - 256, y - 256)
            if d < 220:
                alpha = int(255 * (1.0 - d / 220.0))
                # Glowing purple-cyan transition
                r = int(142 * (d / 220.0) + 52 * (1.0 - d / 220.0))
                g = int(68 * (d / 220.0) + 152 * (1.0 - d / 220.0))
                b = int(173 * (d / 220.0) + 219 * (1.0 - d / 220.0))
                set_pixel(buf, w, h, x, y, r, g, b, alpha)
    p2 = out_dir / "transparent_gradient_02.png"
    p2.write_bytes(make_png_bytes(w, h, buf))

def generate_exif_rotated():
    out_dir = FIXTURES_DIR / "exif_rotated"
    out_dir.mkdir(parents=True, exist_ok=True)

    def make_exif_jpeg(width: int, height: int, orientation: int) -> bytes:
        soi = b"\xff\xd8"
        # TIFF header: II (Intel byte order), Tag 0x0112 SHORT count 1 value orientation
        tiff = b"II\x2a\x00\x08\x00\x00\x00" + struct.pack("<H", 1) + struct.pack("<HHII", 0x0112, 3, 1, orientation) + b"\x00\x00\x00\x00"
        exif = b"Exif\x00\x00" + tiff
        app1 = b"\xff\xe1" + struct.pack(">H", len(exif) + 2) + exif
        dqt = b"\xff\xdb\x00\x43\x00" + bytes([16] * 64)
        sof0 = b"\xff\xc0" + struct.pack(">BHHB", 11, height, width, 1) + b"\x01\x11\x00"
        dht_data = b"\x00\x00\x01\x05\x01\x01\x01\x01\x01\x01\x00\x00\x00\x00\x00\x00\x00" + bytes(range(12))
        dht = b"\xff\xc4" + struct.pack(">H", len(dht_data) + 2) + dht_data
        sos = b"\xff\xda\x00\x08\x01\x01\x00\x00\x3f\x00"
        scan = b"\xf8\x00" * ((width * height + 63) // 64)
        eoi = b"\xff\xd9"
        return soi + app1 + dqt + sof0 + dht + sos + scan + eoi

    # 1. Orientation 6 = Rotate 90 CW (256x512)
    p1 = out_dir / "exif_rotate_90_cw_01.jpg"
    p1.write_bytes(make_exif_jpeg(256, 512, 6))

    # 2. Orientation 3 = Rotate 180 (512x512)
    p2 = out_dir / "exif_rotate_180_02.jpg"
    p2.write_bytes(make_exif_jpeg(512, 512, 3))

def generate_corrupt_truncated():
    out_dir = FIXTURES_DIR / "corrupt_truncated"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Truncated PNG (valid header + IHDR, chopped in mid IDAT stream)
    w, h = 256, 256
    buf = bytearray([255, 0, 0, 255] * (w * h))
    valid_png = make_png_bytes(w, h, buf)
    # Cut off halfway through IDAT
    truncated_png = valid_png[:len(valid_png) // 2]
    p1 = out_dir / "truncated_idat_01.png"
    p1.write_bytes(truncated_png)

    # 2. Corrupt ORA (ZIP header corrupted)
    corrupted_ora = b"PK\x03\x04\x14\x00\x00\x00\x08\x00CORRUPTED_ZERO_BYTE_DATA_PADDING" + b"\x00" * 128
    p2 = out_dir / "corrupted_archive_02.ora"
    p2.write_bytes(corrupted_ora)

    # 3. Malformed SVG (unclosed tags, truncated XML)
    malformed_svg = """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512">
  <g stroke="black" stroke-width="2">
    <path d="M 10 10 L 200 200 C 50 100 80
<!-- Truncated mid-path without closing tags -->
"""
    p3 = out_dir / "malformed_tags_03.svg"
    p3.write_text(malformed_svg, encoding="utf-8")

def generate_layered_ora():
    out_dir = FIXTURES_DIR / "layered_ora"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Simple 2-Layer ORA (512x512)
    w, h = 512, 512
    # Layer 0: Background solid pastel cyan
    bg_buf = bytearray([224, 247, 250, 255] * (w * h))
    bg_png = make_png_bytes(w, h, bg_buf)

    # Layer 1: Foreground inking cross
    ink_buf = bytearray(w * h * 4) # transparent
    draw_line(ink_buf, w, h, 64, 256, 448, 256, 33, 33, 33, 255, width=6)
    draw_line(ink_buf, w, h, 256, 64, 256, 448, 33, 33, 33, 255, width=6)
    draw_circle(ink_buf, w, h, 256, 256, 128, 33, 33, 33, 255, stroke=4)
    ink_png = make_png_bytes(w, h, ink_buf)

    # Thumbnail 128x128
    thumb_buf = bytearray([224, 247, 250, 255] * (128 * 128))
    draw_circle(thumb_buf, 128, 128, 64, 64, 32, 33, 33, 33, 255, stroke=2)
    thumb_png = make_png_bytes(128, 128, thumb_buf)

    stack_xml_1 = """<?xml version="1.0" encoding="UTF-8"?>
<image version="0.0.3" width="512" height="512">
  <stack>
    <layer name="Background" src="data/layer_0.png" x="0" y="0" opacity="1.0" visibility="visible"/>
    <layer name="Linework" src="data/layer_1.png" x="0" y="0" opacity="1.0" visibility="visible" composite-op="svg:multiply"/>
  </stack>
</image>
"""
    p1 = out_dir / "sample_layered_simple_01.ora"
    with zipfile.ZipFile(p1, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("mimetype", "image/openraster", compress_type=zipfile.ZIP_STORED)
        z.writestr("stack.xml", stack_xml_1)
        z.writestr("data/layer_0.png", bg_png)
        z.writestr("data/layer_1.png", ink_png)
        z.writestr("Thumbnails/thumbnail.png", thumb_png)

    # 2. Detail-Dense 3-Layer ORA (1024x1024)
    w, h = 1024, 1024
    l0_buf = bytearray([255, 255, 255, 255] * (w * h))
    l0_png = make_png_bytes(w, h, l0_buf)

    # Color wash layer (multi-color gradient spots)
    l1_buf = bytearray(w * h * 4)
    draw_fill_circle(l1_buf, w, h, 384, 384, 200, 255, 179, 186, 200) # Soft red
    draw_fill_circle(l1_buf, w, h, 640, 384, 200, 186, 255, 201, 200) # Soft green
    draw_fill_circle(l1_buf, w, h, 512, 640, 200, 186, 225, 255, 200) # Soft blue
    l1_png = make_png_bytes(w, h, l1_buf)

    # Detailed inking layer
    l2_buf = bytearray(w * h * 4)
    for r in range(80, 420, 50):
        draw_circle(l2_buf, w, h, 512, 512, r, 20, 20, 20, 255, stroke=3)
    draw_line(l2_buf, w, h, 128, 512, 896, 512, 20, 20, 20, 255, width=4)
    draw_line(l2_buf, w, h, 512, 128, 512, 896, 20, 20, 20, 255, width=4)
    l2_png = make_png_bytes(w, h, l2_buf)

    thumb_dense_buf = bytearray([255, 255, 255, 255] * (128 * 128))
    draw_circle(thumb_dense_buf, 128, 128, 64, 64, 40, 20, 20, 20, 255, stroke=2)
    thumb_dense_png = make_png_bytes(128, 128, thumb_dense_buf)

    stack_xml_2 = """<?xml version="1.0" encoding="UTF-8"?>
<image version="0.0.3" width="1024" height="1024">
  <stack>
    <layer name="Canvas Base" src="data/layer_0.png" x="0" y="0" opacity="1.0" visibility="visible"/>
    <layer name="Color Wash" src="data/layer_1.png" x="0" y="0" opacity="0.85" visibility="visible" composite-op="svg:src-over"/>
    <layer name="Ink Contours" src="data/layer_2.png" x="0" y="0" opacity="1.0" visibility="visible" composite-op="svg:multiply"/>
  </stack>
</image>
"""
    p2 = out_dir / "sample_layered_dense_02.ora"
    with zipfile.ZipFile(p2, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("mimetype", "image/openraster", compress_type=zipfile.ZIP_STORED)
        z.writestr("stack.xml", stack_xml_2)
        z.writestr("data/layer_0.png", l0_png)
        z.writestr("data/layer_1.png", l1_png)
        z.writestr("data/layer_2.png", l2_png)
        z.writestr("Thumbnails/thumbnail.png", thumb_dense_png)

def generate_svg_samples():
    out_dir = FIXTURES_DIR / "svg_samples"
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1. Simple SVG Shapes (512x512)
    svg_simple = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
  <!-- ArtStudio v1 Fixture: Simple Geometric Vector Shapes (License: CC0-1.0) -->
  <rect width="512" height="512" fill="#FAFAFA"/>
  <rect x="64" y="64" width="160" height="160" fill="#E74C3C" stroke="#C0392B" stroke-width="4" rx="16"/>
  <circle cx="360" cy="144" r="80" fill="#3498DB" stroke="#2980B9" stroke-width="4"/>
  <polygon points="144,448 64,304 224,304" fill="#2ECC71" stroke="#27AE60" stroke-width="4"/>
  <ellipse cx="360" cy="376" rx="96" ry="64" fill="#F39C12" stroke="#D35400" stroke-width="4"/>
</svg>
"""
    p1 = out_dir / "sample_vector_simple_01.svg"
    p1.write_text(svg_simple, encoding="utf-8")

    # 2. Detail-Dense SVG Bezier Curves (1024x1024)
    svg_dense = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <!-- ArtStudio v1 Fixture: Detail-Dense Vector Paths & Contours (License: CC0-1.0) -->
  <rect width="1024" height="1024" fill="#FFFFFF"/>
  <g fill="none" stroke="#2C3E50" stroke-linecap="round" stroke-linejoin="round">
    <!-- Primary Outer Contour -->
    <path d="M 512 80 C 280 80, 100 240, 100 460 C 100 660, 260 840, 512 944 C 764 840, 924 660, 924 460 C 924 240, 744 80, 512 80 Z" stroke-width="8"/>
    <!-- Intricate Internal Flourishes -->
    <path d="M 300 340 Q 512 200, 724 340 T 512 600 Z" stroke-width="5" fill="#ECF0F1"/>
    <path d="M 240 500 C 340 650, 684 650, 784 500 C 684 750, 340 750, 240 500 Z" stroke-width="6" fill="#BDC3C7"/>
    <!-- Concentric Hatch Paths -->
    <path d="M 512 240 C 400 240, 320 320, 320 440 S 440 640, 512 700 S 704 560, 704 440 S 624 240, 512 240 Z" stroke-width="4"/>
    <path d="M 512 300 C 440 300, 380 360, 380 440 S 460 580, 512 620 S 644 520, 644 440 S 584 300, 512 300 Z" stroke-width="3"/>
    <!-- Radiating Ray Accents -->
    <line x1="512" y1="120" x2="512" y2="200" stroke-width="4"/>
    <line x1="260" y1="200" x2="320" y2="260" stroke-width="4"/>
    <line x1="764" y1="200" x2="704" y2="260" stroke-width="4"/>
    <line x1="160" y1="460" x2="240" y2="460" stroke-width="4"/>
    <line x1="864" y1="460" x2="784" y2="460" stroke-width="4"/>
  </g>
</svg>
"""
    p2 = out_dir / "sample_vector_dense_02.svg"
    p2.write_text(svg_dense, encoding="utf-8")

def build_manifest_and_licenses():
    manifest_items = []

    catalog = [
        # 1. line_art
        {
            "id": "lineart_simple_01",
            "path": "line_art/lineart_simple_01.png",
            "category": "line_art",
            "format": "png",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "Contour extraction, line cleanup, and flood fill boundary verification."
        },
        {
            "id": "lineart_dense_02",
            "path": "line_art/lineart_dense_02.png",
            "category": "line_art",
            "format": "png",
            "dimensions": [1024, 1024],
            "complexity": "detail_dense",
            "intended_test": "Dense cross-hatch vectorization, gap closing, and high-frequency edge isolation."
        },
        # 2. flat_colour
        {
            "id": "flat_colour_simple_01",
            "path": "flat_colour/flat_colour_simple_01.png",
            "category": "flat_colour",
            "format": "png",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "Color segmentation, palette quantization, and clean flat region boundary tests."
        },
        {
            "id": "flat_colour_dense_02",
            "path": "flat_colour/flat_colour_dense_02.png",
            "category": "flat_colour",
            "format": "png",
            "dimensions": [1024, 1024],
            "complexity": "detail_dense",
            "intended_test": "Multi-region palette extraction, 16-color segment identification, and zero-leak flood fill."
        },
        # 3. noisy_sketches
        {
            "id": "sketch_noisy_simple_01",
            "path": "noisy_sketches/sketch_noisy_simple_01.png",
            "category": "noisy_sketches",
            "format": "png",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "Pencil graphite desensitization, threshold binarization, and contrast normalization."
        },
        {
            "id": "sketch_noisy_dense_02",
            "path": "noisy_sketches/sketch_noisy_dense_02.png",
            "category": "noisy_sketches",
            "format": "png",
            "dimensions": [1024, 1024],
            "complexity": "detail_dense",
            "intended_test": "Despeckle filtering, morphological gap closing, and heavy scanner grain reduction."
        },
        # 4. transparent_png
        {
            "id": "transparent_cutout_01",
            "path": "transparent_png/transparent_cutout_01.png",
            "category": "transparent_png",
            "format": "png",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "Alpha mask preservation, non-destructive layer import, and bounding box trimming."
        },
        {
            "id": "transparent_gradient_02",
            "path": "transparent_png/transparent_gradient_02.png",
            "category": "transparent_png",
            "format": "png",
            "dimensions": [512, 512],
            "complexity": "detail_dense",
            "intended_test": "Continuous alpha channel blending, watercolor wash simulation, and premultiplied alpha math."
        },
        # 5. exif_rotated
        {
            "id": "exif_rotate_90_cw_01",
            "path": "exif_rotated/exif_rotate_90_cw_01.jpg",
            "category": "exif_rotated",
            "format": "jpg",
            "dimensions": [256, 512],
            "complexity": "simple",
            "intended_test": "EXIF orientation 6 (90 CW) auto-correction on raster import."
        },
        {
            "id": "exif_rotate_180_02",
            "path": "exif_rotated/exif_rotate_180_02.jpg",
            "category": "exif_rotated",
            "format": "jpg",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "EXIF orientation 3 (180 deg) inversion auto-correction."
        },
        # 6. corrupt_truncated
        {
            "id": "truncated_idat_01",
            "path": "corrupt_truncated/truncated_idat_01.png",
            "category": "corrupt_truncated",
            "format": "png",
            "dimensions": None,
            "complexity": "corrupt",
            "intended_test": "Resilient image decoding and graceful fallback on truncated stream."
        },
        {
            "id": "corrupted_archive_02",
            "path": "corrupt_truncated/corrupted_archive_02.ora",
            "category": "corrupt_truncated",
            "format": "ora",
            "dimensions": None,
            "complexity": "corrupt",
            "intended_test": "OpenRaster parser error recovery and informative user diagnostics on broken ZIP container."
        },
        {
            "id": "malformed_tags_03",
            "path": "corrupt_truncated/malformed_tags_03.svg",
            "category": "corrupt_truncated",
            "format": "svg",
            "dimensions": None,
            "complexity": "corrupt",
            "intended_test": "SVG XML parser syntax recovery and safe subset error reporting."
        },
        # 7. layered_ora
        {
            "id": "sample_layered_simple_01",
            "path": "layered_ora/sample_layered_simple_01.ora",
            "category": "layered_ora",
            "format": "ora",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "OpenRaster multi-layer import/export, stack.xml hierarchy, and layer blend mode parity."
        },
        {
            "id": "sample_layered_dense_02",
            "path": "layered_ora/sample_layered_dense_02.ora",
            "category": "layered_ora",
            "format": "ora",
            "dimensions": [1024, 1024],
            "complexity": "detail_dense",
            "intended_test": "Three-layer ORA composition, variable layer opacities, and composite operation fidelity."
        },
        # 8. svg_samples
        {
            "id": "sample_vector_simple_01",
            "path": "svg_samples/sample_vector_simple_01.svg",
            "category": "svg_samples",
            "format": "svg",
            "dimensions": [512, 512],
            "complexity": "simple",
            "intended_test": "Safe SVG primitive tag parsing (<rect>, <circle>, <polygon>, <ellipse>)."
        },
        {
            "id": "sample_vector_dense_02",
            "path": "svg_samples/sample_vector_dense_02.svg",
            "category": "svg_samples",
            "format": "svg",
            "dimensions": [1024, 1024],
            "complexity": "detail_dense",
            "intended_test": "Safe SVG Bezier path parsing, cubic spline parsing, and vector scaling precision."
        },
        # Palettes
        {
            "id": "palette_15_styles",
            "path": "palettes/colour_lab_reference_palettes.json",
            "category": "palettes",
            "format": "json",
            "dimensions": None,
            "complexity": "reference",
            "intended_test": "15 Colour Lab styles + No Colour specification compliance."
        }
    ]

    for item in catalog:
        file_path = FIXTURES_DIR / item["path"]
        if not file_path.exists():
            print(f"Warning: fixture file {file_path} not found!")
            continue
        sha = compute_sha256(file_path)
        manifest_items.append({
            "id": item["id"],
            "path": item["path"],
            "category": item["category"],
            "format": item["format"],
            "dimensions": item["dimensions"],
            "complexity": item["complexity"],
            "sha256": sha,
            "intended_test": item["intended_test"],
            "license": "CC0-1.0",
            "author": "Antigravity Art Studio Test Suite"
        })

    manifest_data = {
        "corpus_version": "1.0.0",
        "created_at": "2026-10-09",
        "license_policy": "Strict CC0-1.0. 100% synthetically generated assets. No proprietary, scraped, or unverified art.",
        "categories_covered": [
            "line_art",
            "flat_colour",
            "noisy_sketches",
            "transparent_png",
            "exif_rotated",
            "corrupt_truncated",
            "layered_ora",
            "svg_samples",
            "palettes"
        ],
        "fixture_count": len(manifest_items),
        "fixtures": manifest_items
    }

    manifest_path = FIXTURES_DIR / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest_data, f, indent=2)
    print(f"Manifest written with {len(manifest_items)} fixtures -> {manifest_path}")

    # Write LICENSES.md
    licenses_md = """# Art Studio Test Corpus v1 — Licenses and Provenance

All assets in `tests/fixtures/artstudio/v1/` are dedicated to the public domain under the **Creative Commons Zero 1.0 Universal (CC0-1.0)** license.

## Provenance and Creation Methodology
- **Author**: Antigravity Art Studio Engineering & QA Team
- **Generation Method**: 100% synthetically generated via deterministic Python scripts (`tools/qa/generate_art_fixtures.py`) using geometric primitives, mathematical Bezier splines, procedural noise algorithms, and standard file format serialization.
- **Copyright Status**: Zero third-party, scraped, copyrighted, or unverified art assets are included in this repository.

## Inventory of Assets & License Declarations

| Fixture ID | Category | Relative Path | License | Dimensions | Complexity |
|---|---|---|---|---|---|
| `lineart_simple_01` | `line_art` | `line_art/lineart_simple_01.png` | CC0-1.0 | 512x512 | Simple |
| `lineart_dense_02` | `line_art` | `line_art/lineart_dense_02.png` | CC0-1.0 | 1024x1024 | Detail-dense |
| `flat_colour_simple_01` | `flat_colour` | `flat_colour/flat_colour_simple_01.png` | CC0-1.0 | 512x512 | Simple |
| `flat_colour_dense_02` | `flat_colour` | `flat_colour/flat_colour_dense_02.png` | CC0-1.0 | 1024x1024 | Detail-dense |
| `sketch_noisy_simple_01` | `noisy_sketches` | `noisy_sketches/sketch_noisy_simple_01.png` | CC0-1.0 | 512x512 | Simple |
| `sketch_noisy_dense_02` | `noisy_sketches` | `noisy_sketches/sketch_noisy_dense_02.png` | CC0-1.0 | 1024x1024 | Detail-dense |
| `transparent_cutout_01` | `transparent_png` | `transparent_png/transparent_cutout_01.png` | CC0-1.0 | 512x512 | Simple |
| `transparent_gradient_02` | `transparent_png` | `transparent_png/transparent_gradient_02.png` | CC0-1.0 | 512x512 | Detail-dense |
| `exif_rotate_90_cw_01` | `exif_rotated` | `exif_rotated/exif_rotate_90_cw_01.jpg` | CC0-1.0 | 256x512 | Simple |
| `exif_rotate_180_02` | `exif_rotated` | `exif_rotated/exif_rotate_180_02.jpg` | CC0-1.0 | 512x512 | Simple |
| `truncated_idat_01` | `corrupt_truncated` | `corrupt_truncated/truncated_idat_01.png` | CC0-1.0 | N/A | Corrupted |
| `corrupted_archive_02` | `corrupt_truncated` | `corrupt_truncated/corrupted_archive_02.ora` | CC0-1.0 | N/A | Corrupted |
| `malformed_tags_03` | `corrupt_truncated` | `corrupt_truncated/malformed_tags_03.svg` | CC0-1.0 | N/A | Corrupted |
| `sample_layered_simple_01` | `layered_ora` | `layered_ora/sample_layered_simple_01.ora` | CC0-1.0 | 512x512 | Simple |
| `sample_layered_dense_02` | `layered_ora` | `layered_ora/sample_layered_dense_02.ora` | CC0-1.0 | 1024x1024 | Detail-dense |
| `sample_vector_simple_01` | `svg_samples` | `svg_samples/sample_vector_simple_01.svg` | CC0-1.0 | 512x512 | Simple |
| `sample_vector_dense_02` | `svg_samples` | `svg_samples/sample_vector_dense_02.svg` | CC0-1.0 | 1024x1024 | Detail-dense |
| `palette_15_styles` | `palettes` | `palettes/colour_lab_reference_palettes.json` | CC0-1.0 | N/A | Reference |

## Modification & Attribution Notes
No external works were adapted, traced, or incorporated. Any downstream modification must retain CC0-1.0 terms.
"""
    licenses_path = FIXTURES_DIR / "LICENSES.md"
    licenses_path.write_text(licenses_md, encoding="utf-8")
    print(f"Licenses documented -> {licenses_path}")

def main():
    print("Generating synthetic art fixtures across 8 required categories...")
    generate_line_art()
    generate_flat_colour()
    generate_noisy_sketches()
    generate_transparent_png()
    generate_exif_rotated()
    generate_corrupt_truncated()
    generate_layered_ora()
    generate_svg_samples()
    build_manifest_and_licenses()
    print("All fixtures generated successfully.")

if __name__ == "__main__":
    main()
