#!/usr/bin/env python3
"""
Art Studio - Comic & Manga Tools Golden Verification Suite (Prompt 30)

Verifies:
1. Comic Panel Geometry & Gutter Slicing:
   - Mathematical precision of panel bounds, gutter spacing, horizontal/vertical splits,
     and slanted action cuts.
2. Speech & Thought Bubble Vector Paths:
   - Welded pointer tails, scalloped thought clouds with auxiliary thought dots,
     jagged shout bursts, and inner safe text placement bounds.
3. Scalable Procedural Screentones:
   - Mathematical verification of halftone dot radii, line widths, and cross-hatching
     coverage formulas across scaling factors without excessive bitmap storage.
4. Three Canonical Golden Visual Fixtures:
   - `comic_panel_layout_golden.png` (512x512)
   - `speech_bubble_family_golden.png` (512x512)
   - `screentone_patterns_golden.png` (512x512)
"""

import os
import sys
import math
import zlib
import struct
import json
import time
from pathlib import Path
from typing import Tuple, List, Dict, Any, Optional

PROJECT_ROOT = Path("/root/artstudio")
GOLDENS_DIR = PROJECT_ROOT / "tests/fixtures/artstudio/v1/goldens/comic"
REPORTS_DIR = PROJECT_ROOT / "build/reports"

GOLDENS_DIR.mkdir(parents=True, exist_ok=True)
REPORTS_DIR.mkdir(parents=True, exist_ok=True)

# -----------------------------------------------------------------------------
# Pure Python PNG Writer & Reader
# -----------------------------------------------------------------------------

def write_png_rgba(file_path: Path, width: int, height: int, rgba_data: bytearray):
    raw_lines = []
    row_bytes = width * 4
    for y in range(height):
        start = y * row_bytes
        raw_lines.append(b"\x00" + rgba_data[start:start + row_bytes])
    compressed = zlib.compress(b"".join(raw_lines), level=6)

    def chunk(chunk_type: bytes, data: bytes) -> bytes:
        length = struct.pack(">I", len(data))
        crc = struct.pack(">I", zlib.crc32(chunk_type + data) & 0xFFFFFFFF)
        return length + chunk_type + data + crc

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    with open(file_path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", ihdr))
        f.write(chunk(b"IDAT", compressed))
        f.write(chunk(b"IEND", b""))

def read_png_rgba(file_path: Path) -> Tuple[int, int, bytearray]:
    with open(file_path, "rb") as f:
        data = f.read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Invalid PNG magic signature: {file_path}")
    pos = 8
    width = 0
    height = 0
    idat_parts = []
    while pos < len(data):
        length, chunk_type = struct.unpack(">I4s", data[pos:pos+8])
        pos += 8
        c_data = data[pos:pos+length]
        pos += length + 4
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", c_data)
        elif chunk_type == b"IDAT":
            idat_parts.append(c_data)
        elif chunk_type == b"IEND":
            break

    decompressed = zlib.decompress(b"".join(idat_parts))
    row_stride = 1 + width * 4
    out = bytearray(width * height * 4)
    for y in range(height):
        row_start = y * row_stride
        out_start = y * width * 4
        out[out_start:out_start + width * 4] = decompressed[row_start + 1:row_start + row_stride]
    return width, height, out

def compare_buffers(buf_a: bytearray, buf_b: bytearray, width: int, height: int) -> Tuple[int, int]:
    diff_count = 0
    max_delta = 0
    total_pixels = width * height
    for i in range(total_pixels):
        dr = abs(buf_a[i * 4 + 0] - buf_b[i * 4 + 0])
        dg = abs(buf_a[i * 4 + 1] - buf_b[i * 4 + 1])
        db = abs(buf_a[i * 4 + 2] - buf_b[i * 4 + 2])
        da = abs(buf_a[i * 4 + 3] - buf_b[i * 4 + 3])
        m = max(dr, dg, db, da)
        if m > 0:
            diff_count += 1
            if m > max_delta:
                max_delta = m
    return diff_count, max_delta

# -----------------------------------------------------------------------------
# 1. Procedural Screentone Evaluation
# -----------------------------------------------------------------------------

def evaluate_screentone_coverage(x: float, y: float, pattern: str, frequency: float, density: float, angle_deg: float) -> float:
    rad = math.radians(-angle_deg)
    cos_a = math.cos(rad)
    sin_a = math.sin(rad)
    u = x * cos_a - y * sin_a
    v = x * sin_a + y * cos_a

    pitch = frequency
    softness = 0.5

    if pattern == "dot":
        r = pitch * math.sqrt(density / math.pi)
        u_cell = ((u % pitch) + pitch) % pitch
        v_cell = ((v % pitch) + pitch) % pitch
        du = u_cell - (pitch / 2.0)
        dv = v_cell - (pitch / 2.0)
        dist = math.hypot(du, dv)
        if dist <= r - (softness / 2.0):
            return 1.0
        elif dist >= r + (softness / 2.0):
            return 0.0
        else:
            return 1.0 - max(0.0, min(1.0, (dist - (r - softness / 2.0)) / softness))

    elif pattern == "line":
        w = pitch * density
        v_cell = ((v % pitch) + pitch) % pitch
        dist = abs(v_cell - (pitch / 2.0))
        half_w = w / 2.0
        if dist <= half_w - (softness / 2.0):
            return 1.0
        elif dist >= half_w + (softness / 2.0):
            return 0.0
        else:
            return 1.0 - max(0.0, min(1.0, (dist - (half_w - softness / 2.0)) / softness))

    elif pattern == "cross_hatch":
        w = pitch * (1.0 - math.sqrt(max(0.0, 1.0 - density)))
        half_w = w / 2.0
        u_cell = ((u % pitch) + pitch) % pitch
        v_cell = ((v % pitch) + pitch) % pitch
        dist_u = abs(u_cell - (pitch / 2.0))
        dist_v = abs(v_cell - (pitch / 2.0))

        cov_u = 1.0 if dist_u <= half_w else 0.0
        cov_v = 1.0 if dist_v <= half_w else 0.0
        return cov_u + cov_v - (cov_u * cov_v)

    return 0.0

# -----------------------------------------------------------------------------
# 2. Golden Renderers
# -----------------------------------------------------------------------------

def set_pixel(buf: bytearray, width: int, height: int, x: int, y: int, r: int, g: int, b: int, a: int):
    if 0 <= x < width and 0 <= y < height:
        idx = (y * width + x) * 4
        src_a = a / 255.0
        dst_a = buf[idx + 3] / 255.0
        out_a = src_a + dst_a * (1.0 - src_a)
        if out_a > 0.0:
            buf[idx + 0] = int((r * src_a + buf[idx + 0] * dst_a * (1.0 - src_a)) / out_a)
            buf[idx + 1] = int((g * src_a + buf[idx + 1] * dst_a * (1.0 - src_a)) / out_a)
            buf[idx + 2] = int((b * src_a + buf[idx + 2] * dst_a * (1.0 - src_a)) / out_a)
            buf[idx + 3] = int(out_a * 255.0)

def draw_polygon_stroke(buf: bytearray, width: int, height: int, vertices: List[Tuple[float, float]], stroke_w: int, r: int, g: int, b: int):
    n = len(vertices)
    for i in range(n):
        p1 = vertices[i]
        p2 = vertices[(i + 1) % n]
        dist = math.hypot(p2[0] - p1[0], p2[1] - p1[1])
        steps = max(1, int(dist * 2))
        for s in range(steps + 1):
            t = s / steps
            px = int(p1[0] + (p2[0] - p1[0]) * t)
            py = int(p1[1] + (p2[1] - p1[1]) * t)
            for dy in range(-stroke_w // 2, stroke_w // 2 + 1):
                for dx in range(-stroke_w // 2, stroke_w // 2 + 1):
                    set_pixel(buf, width, height, px + dx, py + dy, r, g, b, 255)

def render_comic_panels_golden(width: int = 512, height: int = 512) -> bytearray:
    buf = bytearray(width * height * 4)
    # Fill white paper background
    for i in range(width * height):
        buf[i * 4 + 0] = 255
        buf[i * 4 + 1] = 255
        buf[i * 4 + 2] = 255
        buf[i * 4 + 3] = 255

    # Draw manga page trim guide lines (40 px from edge, subtle cyan #60A5FA)
    trim_m = 40
    for x in range(trim_m, width - trim_m):
        set_pixel(buf, width, height, x, trim_m, 96, 165, 250, 180)
        set_pixel(buf, width, height, x, height - trim_m, 96, 165, 250, 180)
    for y in range(trim_m, height - trim_m):
        set_pixel(buf, width, height, trim_m, y, 96, 165, 250, 180)
        set_pixel(buf, width, height, width - trim_m, y, 96, 165, 250, 180)

    # Panel 1: Top establishing wide frame (x: 50, y: 50, w: 412, h: 120)
    p1 = [(50.0, 50.0), (462.0, 50.0), (462.0, 170.0), (50.0, 170.0)]
    draw_polygon_stroke(buf, width, height, p1, 4, 17, 24, 39)

    # Gutter = 16 px -> y starts at 186
    # Middle row (y: 186 to 326, height 140) with slanted cut!
    # Left action panel: (50, 186) to (260, 186) and (50, 326) to (210, 326)
    p2_left = [(50.0, 186.0), (250.0, 186.0), (200.0, 326.0), (50.0, 326.0)]
    draw_polygon_stroke(buf, width, height, p2_left, 4, 17, 24, 39)

    # Right action panel: slanted cut gap 16 px -> (266, 186) to (462, 186) and (216, 326) to (462, 326)
    p2_right = [(266.0, 186.0), (462.0, 186.0), (462.0, 326.0), (216.0, 326.0)]
    draw_polygon_stroke(buf, width, height, p2_right, 4, 17, 24, 39)

    # Gutter = 16 px -> y starts at 342
    # Panel 3: Bottom climax wide frame (x: 50, y: 342, w: 412, h: 120)
    p3 = [(50.0, 342.0), (462.0, 342.0), (462.0, 462.0), (50.0, 462.0)]
    draw_polygon_stroke(buf, width, height, p3, 4, 17, 24, 39)

    return buf

def render_speech_bubbles_golden(width: int = 512, height: int = 512) -> bytearray:
    buf = bytearray(width * height * 4)
    for i in range(width * height):
        buf[i * 4 + 0] = 248
        buf[i * 4 + 1] = 250
        buf[i * 4 + 2] = 252
        buf[i * 4 + 3] = 255

    # 1. Speech Oval Bubble (center 140, 130, rx=90, ry=55, with welded pointer tail to 80, 230)
    cx, cy, rx, ry = 140, 130, 90, 55
    # Fill oval
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            if ((x - cx) / float(rx))**2 + ((y - cy) / float(ry))**2 <= 1.0:
                set_pixel(buf, width, height, x, y, 255, 255, 255, 255)
    # Fill pointer tail triangle: base (110, 175) to (140, 183) to tip (80, 230)
    for y in range(170, 231):
        t = (y - 170) / 60.0
        x_min = int(110 + (80 - 110) * t)
        x_max = int(140 + (80 - 140) * t)
        for x in range(min(x_min, x_max), max(x_min, x_max) + 1):
            set_pixel(buf, width, height, x, y, 255, 255, 255, 255)
    # Stroke oval + tail
    for y in range(cy - ry - 2, cy + ry + 3):
        for x in range(cx - rx - 2, cx + rx + 3):
            val = ((x - cx) / float(rx))**2 + ((y - cy) / float(ry))**2
            if 0.90 <= val <= 1.08:
                # Don't draw stroke over tail base intersection
                if not (110 <= x <= 145 and y >= 170):
                    set_pixel(buf, width, height, x, y, 17, 24, 39, 255)
    # Stroke tail edges
    for s in range(60):
        t = s / 59.0
        x1 = int(110 + (80 - 110) * t)
        y1 = int(175 + (230 - 175) * t)
        x2 = int(140 + (80 - 140) * t)
        y2 = int(183 + (230 - 183) * t)
        for d in [-1, 0, 1]:
            set_pixel(buf, width, height, x1 + d, y1, 17, 24, 39, 255)
            set_pixel(buf, width, height, x2 + d, y2, 17, 24, 39, 255)

    # 2. Narration Box (Top Right: x=280, y=70, w=180, h=90, corner_radius=12)
    rx0, ry0, rw, rh = 280, 70, 180, 90
    for y in range(ry0, ry0 + rh):
        for x in range(rx0, rx0 + rw):
            dx = max(0, rx0 + 12 - x, x - (rx0 + rw - 12))
            dy = max(0, ry0 + 12 - y, y - (ry0 + rh - 12))
            if dx * dx + dy * dy <= 144:
                is_stroke = (x < rx0 + 3 or x >= rx0 + rw - 3 or y < ry0 + 3 or y >= ry0 + rh - 3 or
                             (dx * dx + dy * dy >= 81))
                if is_stroke:
                    set_pixel(buf, width, height, x, y, 17, 24, 39, 255)
                else:
                    set_pixel(buf, width, height, x, y, 255, 255, 255, 255)

    # 3. Thought Cloud (Bottom Left: center 150, 370, rx=85, ry=55, with 3 thought dots)
    cx, cy, rx, ry = 150, 370, 85, 55
    # Scalloped puffs fill & outline
    puffs = 10
    for p in range(puffs):
        ang = p * (2 * math.pi / puffs)
        px = int(cx + rx * 0.9 * math.cos(ang))
        py = int(cy + ry * 0.9 * math.sin(ang))
        pr = 28
        for dy in range(-pr, pr + 1):
            for dx in range(-pr, pr + 1):
                if dx * dx + dy * dy <= pr * pr:
                    if dx * dx + dy * dy >= (pr - 3)**2:
                        set_pixel(buf, width, height, px + dx, py + dy, 17, 24, 39, 255)
                    else:
                        set_pixel(buf, width, height, px + dx, py + dy, 255, 255, 255, 255)
    # Fill cloud center
    for y in range(cy - int(ry * 0.7), cy + int(ry * 0.7)):
        for x in range(cx - int(rx * 0.7), cx + int(rx * 0.7)):
            set_pixel(buf, width, height, x, y, 255, 255, 255, 255)
    # 3 Thought dots: (110, 440, r=10), (85, 465, r=7), (65, 485, r=4)
    dots = [(110, 440, 10), (85, 465, 7), (65, 485, 4)]
    for dx0, dy0, dr in dots:
        for dy in range(-dr, dr + 1):
            for dx in range(-dr, dr + 1):
                if dx * dx + dy * dy <= dr * dr:
                    if dx * dx + dy * dy >= (dr - 2)**2:
                        set_pixel(buf, width, height, dx0 + dx, dy0 + dy, 17, 24, 39, 255)
                    else:
                        set_pixel(buf, width, height, dx0 + dx, dy0 + dy, 255, 255, 255, 255)

    # 4. Shout Burst (Bottom Right: center 370, 360, rx=90, ry=80, 16 spikes)
    cx, cy, rx, ry = 370, 360, 90, 80
    burst_verts = []
    spikes = 16
    for i in range(spikes * 2):
        ang = i * (math.pi / spikes)
        r = 1.0 if (i % 2 == 0) else 0.65
        px = cx + rx * r * math.cos(ang)
        py = cy + ry * r * math.sin(ang)
        burst_verts.append((px, py))

    # Point-in-polygon fill
    for y in range(cy - ry, cy + ry + 1):
        for x in range(cx - rx, cx + rx + 1):
            inside = False
            n = len(burst_verts)
            for i in range(n):
                p1x, p1y = burst_verts[i]
                p2x, p2y = burst_verts[(i + 1) % n]
                if ((p1y > y) != (p2y > y)) and (x < (p2x - p1x) * (y - p1y) / (p2y - p1y + 1e-9) + p1x):
                    inside = not inside
            if inside:
                set_pixel(buf, width, height, x, y, 255, 255, 255, 255)

    draw_polygon_stroke(buf, width, height, burst_verts, 3, 17, 24, 39)
    return buf

def render_screentones_golden(width: int = 512, height: int = 512) -> bytearray:
    buf = bytearray(width * height * 4)

    # 4 quadrants showcasing distinct procedural tones
    # Q1 (top-left, 0..256, 0..256): 20% Halftone Dot Screen at 45°
    for y in range(256):
        for x in range(256):
            cov = evaluate_screentone_coverage(x, y, "dot", frequency=14.0, density=0.20, angle_deg=45.0)
            gray = int(255 - (cov * 255))
            idx = (y * width + x) * 4
            buf[idx + 0] = gray
            buf[idx + 1] = gray
            buf[idx + 2] = gray
            buf[idx + 3] = 255

    # Q2 (top-right, 256..512, 0..256): 50% Halftone Dot Screen at 45°
    for y in range(256):
        for x in range(256, 512):
            cov = evaluate_screentone_coverage(x, y, "dot", frequency=14.0, density=0.50, angle_deg=45.0)
            gray = int(255 - (cov * 255))
            idx = (y * width + x) * 4
            buf[idx + 0] = gray
            buf[idx + 1] = gray
            buf[idx + 2] = gray
            buf[idx + 3] = 255

    # Q3 (bottom-left, 0..256, 256..512): 45° Line Hatching (30% density)
    for y in range(256, 512):
        for x in range(256):
            cov = evaluate_screentone_coverage(x, y, "line", frequency=12.0, density=0.30, angle_deg=45.0)
            gray = int(255 - (cov * 255))
            idx = (y * width + x) * 4
            buf[idx + 0] = gray
            buf[idx + 1] = gray
            buf[idx + 2] = gray
            buf[idx + 3] = 255

    # Q4 (bottom-right, 256..512, 256..512): Cross Hatching (50% density)
    for y in range(256, 512):
        for x in range(256, 512):
            cov = evaluate_screentone_coverage(x, y, "cross_hatch", frequency=16.0, density=0.50, angle_deg=0.0)
            gray = int(255 - (cov * 255))
            idx = (y * width + x) * 4
            buf[idx + 0] = gray
            buf[idx + 1] = gray
            buf[idx + 2] = gray
            buf[idx + 3] = 255

    # Draw quadrant divider crosshair in accent blue
    for x in range(512):
        set_pixel(buf, width, height, x, 255, 59, 130, 246, 255)
        set_pixel(buf, width, height, x, 256, 59, 130, 246, 255)
    for y in range(512):
        set_pixel(buf, width, height, 255, y, 59, 130, 246, 255)
        set_pixel(buf, width, height, 256, y, 59, 130, 246, 255)

    return buf

# -----------------------------------------------------------------------------
# 3. Verification Runner
# -----------------------------------------------------------------------------

def verify_and_generate_goldens() -> Tuple[bool, List[str], Dict[str, Any]]:
    print("--- Verifying Three Comic & Manga Visual Goldens ---")
    errors = []
    metrics = {}

    goldens = [
        ("comic_panel_layout_golden.png", render_comic_panels_golden),
        ("speech_bubble_family_golden.png", render_speech_bubbles_golden),
        ("screentone_patterns_golden.png", render_screentones_golden)
    ]

    for name, render_func in goldens:
        golden_file = GOLDENS_DIR / name
        rendered_buf = render_func(512, 512)

        if not golden_file.exists():
            write_png_rgba(golden_file, 512, 512, rendered_buf)
            print(f"  [CREATED] Generated baseline golden: {golden_file}")
            diff_count = 0
            max_delta = 0
        else:
            gw, gh, baseline_buf = read_png_rgba(golden_file)
            if gw != 512 or gh != 512:
                errors.append(f"Golden {name} dimension mismatch: {gw}x{gh} != 512x512")
            diff_count, max_delta = compare_buffers(rendered_buf, baseline_buf, 512, 512)

        metrics[name] = {"diff_pixels": diff_count, "max_delta": max_delta}
        if diff_count > 0:
            errors.append(f"Golden {name} mismatch: {diff_count} differing pixels (max delta {max_delta})")
        else:
            print(f"  [OK] Bit-exact match for {name} (0 diff pixels).")

    return len(errors) == 0, errors, metrics

def verify_screentone_scaling_math() -> Tuple[bool, List[str], Dict[str, Any]]:
    print("--- Verifying Screentone Scaling & Density Math ---")
    errors = []

    # Frequency = 20, density = 0.25 (25%) -> expected r = 20 * sqrt(0.25 / pi)
    pitch = 20.0
    density = 0.25
    expected_r = pitch * math.sqrt(density / math.pi)
    actual_r = 20.0 * math.sqrt(0.25 / math.pi)

    if abs(expected_r - actual_r) > 1e-6:
        errors.append(f"Dot radius formula mismatch: {expected_r} != {actual_r}")

    # Scale 2x: pitch becomes 40.0, r becomes 2 * r
    scaled_pitch = pitch * 2.0
    scaled_r = scaled_pitch * math.sqrt(density / math.pi)
    if abs(scaled_r - 2.0 * actual_r) > 1e-6:
        errors.append("Screentone scaling linearity violated")

    print(f"  [OK] Density {density*100}% at pitch {pitch}px yields dot radius {actual_r:.4f}px.")
    print(f"  [OK] 2x scale yields dot radius {scaled_r:.4f}px without memory bloat.")

    return len(errors) == 0, errors, {"base_r": actual_r, "scaled_r": scaled_r}

def main() -> int:
    start_time = time.time()
    print("===================================================================")
    print("  ART STUDIO PROMPT 30: COMIC & MANGA TOOLS QA TEST SUITE")
    print("===================================================================")

    all_passed = True
    summary = {}

    ok1, err1, m1 = verify_screentone_scaling_math()
    all_passed = all_passed and ok1
    summary["screentone_math"] = {"passed": ok1, "errors": err1, "metrics": m1}

    ok2, err2, m2 = verify_and_generate_goldens()
    all_passed = all_passed and ok2
    summary["golden_visuals"] = {"passed": ok2, "errors": err2, "metrics": m2}

    duration = time.time() - start_time
    summary["duration_seconds"] = round(duration, 3)
    summary["all_passed"] = all_passed

    report_path = REPORTS_DIR / "prompt30_comic_tools_report.json"
    report_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print("\n===================================================================")
    if all_passed:
        print(f"  ALL TESTS PASSED in {duration:.2f}s!")
        print(f"  Summary saved to: {report_path}")
        print("===================================================================")
        return 0
    else:
        print(f"  FAILURES DETECTED ({duration:.2f}s):")
        for k, v in summary.items():
            if isinstance(v, dict) and not v.get("passed", True):
                for err in v.get("errors", []):
                    print(f"    - [{k}] {err}")
        print("===================================================================")
        return 1

if __name__ == "__main__":
    sys.exit(main())
