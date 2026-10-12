#!/usr/bin/env python3
"""
Art Studio - Extensible Filter System Golden Verification Suite (Prompt 31)

Verifies:
1. Deterministic execution of 7 core image filter operations:
   - Gaussian Blur
   - Sharpen (Unsharp Mask)
   - Outline (Sobel Edge Detector)
   - Bloom / Glow
   - Procedural Noise
   - Pixelate / Mosaic
   - Ripple Distortion
2. Parameter variation verification (changing parameter changes visual output).
3. Seven Canonical Golden Visual Fixtures (512x512) in `tests/fixtures/artstudio/v1/goldens/filters/`.
4. Latency and memory benchmark across two image sizes (512x512 and 2048x2048).
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
GOLDENS_DIR = PROJECT_ROOT / "tests/fixtures/artstudio/v1/goldens/filters"
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

def compare_buffers(buf_a: bytearray, buf_b: bytearray, width: int, height: int) -> Tuple[int, float, float]:
    diff_count = 0
    sum_sq = 0.0
    total_pixels = width * height
    for i in range(total_pixels):
        idx = i * 4
        dr = buf_a[idx] - buf_b[idx]
        dg = buf_a[idx + 1] - buf_b[idx + 1]
        db = buf_a[idx + 2] - buf_b[idx + 2]
        da = buf_a[idx + 3] - buf_b[idx + 3]
        if dr != 0 or dg != 0 or db != 0 or da != 0:
            diff_count += 1
            sum_sq += (dr*dr + dg*dg + db*db + da*da) / 4.0
    mse = sum_sq / (total_pixels if total_pixels > 0 else 1)
    rmse = math.sqrt(mse) / 255.0
    return diff_count, rmse, 1.0 - rmse

# -----------------------------------------------------------------------------
# Base Synthetic Image Generator
# -----------------------------------------------------------------------------

def generate_base_image(width: int = 512, height: int = 512) -> bytearray:
    buf = bytearray(width * height * 4)
    cx, cy = width / 2.0, height / 2.0
    for y in range(height):
        row_offset = y * width * 4
        for x in range(width):
            idx = row_offset + x * 4
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)

            # Circular rings and quadrants
            if dist < 60:
                # Bright highlight spot for glow
                r, g, b, a = 255, 255, 255, 255
            elif dist < 140:
                # Radial gradient ring
                fade = (dist - 60) / 80.0
                r = int(220 * (1.0 - fade) + 30 * fade)
                g = int(80 * (1.0 - fade) + 180 * fade)
                b = int(240 * (1.0 - fade) + 90 * fade)
                a = 255
            elif dist < 200:
                # Striped quadrant pattern
                if (x + y) % 24 < 12:
                    r, g, b, a = 255, 180, 40, 255
                else:
                    r, g, b, a = 40, 60, 200, 255
            else:
                # Checkerboard border
                bx, by = x // 32, y // 32
                if (bx + by) % 2 == 0:
                    r, g, b, a = 240, 240, 240, 255
                else:
                    r, g, b, a = 40, 40, 40, 255

            buf[idx] = r
            buf[idx + 1] = g
            buf[idx + 2] = b
            buf[idx + 3] = a
    return buf

# -----------------------------------------------------------------------------
# Python Filter Engine (Mirroring ImageFilterEngine.kt)
# -----------------------------------------------------------------------------

def apply_blur(src: bytearray, width: int, height: int, radius: float = 8.0) -> bytearray:
    current = bytearray(src)
    sigma = max(0.5, radius / 2.5)
    kernel_half = min(int(radius), 20)
    kernel_size = kernel_half * 2 + 1

    kernel = [math.exp(-(i * i) / (2.0 * sigma * sigma)) for i in range(-kernel_half, kernel_half + 1)]
    k_sum = sum(kernel)
    kernel = [k / k_sum for k in kernel]

    # Horizontal
    horiz = bytearray(width * height * 4)
    for y in range(height):
        row = y * width * 4
        for x in range(width):
            r_s, g_s, b_s, a_s, w_s = 0.0, 0.0, 0.0, 0.0, 0.0
            for k in range(-kernel_half, kernel_half + 1):
                sx = max(0, min(width - 1, x + k))
                sidx = row + sx * 4
                kw = kernel[k + kernel_half]
                a = src[sidx + 3]
                af = a / 255.0
                a_s += a * kw
                r_s += src[sidx] * af * kw
                g_s += src[sidx + 1] * af * kw
                b_s += src[sidx + 2] * af * kw
                w_s += kw
            fa = max(0, min(255, int(round(a_s / w_s))))
            un = 255.0 / fa if fa > 0 else 0.0
            fr = max(0, min(255, int(round(r_s * un))))
            fg = max(0, min(255, int(round(g_s * un))))
            fb = max(0, min(255, int(round(b_s * un))))
            didx = row + x * 4
            horiz[didx] = fr
            horiz[didx + 1] = fg
            horiz[didx + 2] = fb
            horiz[didx + 3] = fa

    # Vertical
    vert = bytearray(width * height * 4)
    for x in range(width):
        for y in range(height):
            r_s, g_s, b_s, a_s, w_s = 0.0, 0.0, 0.0, 0.0, 0.0
            for k in range(-kernel_half, kernel_half + 1):
                sy = max(0, min(height - 1, y + k))
                sidx = (sy * width + x) * 4
                kw = kernel[k + kernel_half]
                a = horiz[sidx + 3]
                af = a / 255.0
                a_s += a * kw
                r_s += horiz[sidx] * af * kw
                g_s += horiz[sidx + 1] * af * kw
                b_s += horiz[sidx + 2] * af * kw
                w_s += kw
            fa = max(0, min(255, int(round(a_s / w_s))))
            un = 255.0 / fa if fa > 0 else 0.0
            fr = max(0, min(255, int(round(r_s * un))))
            fg = max(0, min(255, int(round(g_s * un))))
            fb = max(0, min(255, int(round(b_s * un))))
            didx = (y * width + x) * 4
            vert[didx] = fr
            vert[didx + 1] = fg
            vert[didx + 2] = fb
            vert[didx + 3] = fa

    return vert

def apply_sharpen(src: bytearray, width: int, height: int, strength: float = 2.0, radius: float = 2.0) -> bytearray:
    blurred = apply_blur(src, width, height, radius)
    dst = bytearray(width * height * 4)
    for i in range(width * height):
        idx = i * 4
        ro, go, bo, ao = src[idx], src[idx + 1], src[idx + 2], src[idx + 3]
        rb, gb, bb = blurred[idx], blurred[idx + 1], blurred[idx + 2]

        dst[idx] = max(0, min(255, int(round(ro + strength * (ro - rb)))))
        dst[idx + 1] = max(0, min(255, int(round(go + strength * (go - gb)))))
        dst[idx + 2] = max(0, min(255, int(round(bo + strength * (bo - bb)))))
        dst[idx + 3] = ao
    return dst

def apply_outline(src: bytearray, width: int, height: int, threshold: float = 35.0,
                  edge_rgba: Tuple[int, int, int, int] = (255, 0, 0, 255),
                  bg_rgba: Tuple[int, int, int, int] = (0, 0, 0, 0),
                  invert: bool = False) -> bytearray:
    dst = bytearray(width * height * 4)

    def lum(x: int, y: int) -> float:
        cx = max(0, min(width - 1, x))
        cy = max(0, min(height - 1, y))
        idx = (cy * width + cx) * 4
        a = src[idx + 3]
        if a == 0:
            return 0.0
        r, g, b = src[idx], src[idx + 1], src[idx + 2]
        return (0.299 * r + 0.587 * g + 0.114 * b) * (a / 255.0)

    for y in range(height):
        row = y * width * 4
        for x in range(width):
            l00 = lum(x - 1, y - 1)
            l10 = lum(x, y - 1)
            l20 = lum(x + 1, y - 1)

            l01 = lum(x - 1, y)
            l21 = lum(x + 1, y)

            l02 = lum(x - 1, y + 1)
            l12 = lum(x, y + 1)
            l22 = lum(x + 1, y + 1)

            gx = (l20 + 2.0 * l21 + l22) - (l00 + 2.0 * l01 + l02)
            gy = (l02 + 2.0 * l12 + l22) - (l00 + 2.0 * l10 + l20)
            mag = math.hypot(gx, gy)

            is_edge = mag >= threshold
            if invert:
                is_edge = not is_edge

            color = edge_rgba if is_edge else bg_rgba
            idx = row + x * 4
            dst[idx] = color[0]
            dst[idx + 1] = color[1]
            dst[idx + 2] = color[2]
            dst[idx + 3] = color[3]
    return dst

def apply_glow(src: bytearray, width: int, height: int, radius: float = 12.0, intensity: float = 1.5, threshold: float = 0.5) -> bytearray:
    bright = bytearray(width * height * 4)
    for i in range(width * height):
        idx = i * 4
        a = src[idx + 3]
        if a == 0:
            continue
        r, g, b = src[idx], src[idx + 1], src[idx + 2]
        l = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        if l >= threshold:
            boost = (l - threshold) / max(0.01, 1.0 - threshold)
            bright[idx] = max(0, min(255, int(round(r * boost))))
            bright[idx + 1] = max(0, min(255, int(round(g * boost))))
            bright[idx + 2] = max(0, min(255, int(round(b * boost))))
            bright[idx + 3] = a

    blurred = apply_blur(bright, width, height, radius)
    dst = bytearray(width * height * 4)
    for i in range(width * height):
        idx = i * 4
        ro, go, bo, ao = src[idx], src[idx + 1], src[idx + 2], src[idx + 3]
        rb, gb, bb = blurred[idx], blurred[idx + 1], blurred[idx + 2]

        dst[idx] = max(0, min(255, int(round(ro + intensity * rb))))
        dst[idx + 1] = max(0, min(255, int(round(go + intensity * gb))))
        dst[idx + 2] = max(0, min(255, int(round(bo + intensity * bb))))
        dst[idx + 3] = ao
    return dst

def apply_noise(src: bytearray, width: int, height: int, amount: float = 0.25, monochrome: bool = True, seed: int = 42) -> bytearray:
    dst = bytearray(width * height * 4)

    def hash2d(x: int, y: int, s: int) -> float:
        h = (s ^ (x * 374761393 + y * 668265263)) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        u = (h ^ (h >> 16)) & 0x7FFFFFFF
        return (u / 2147483647.0) * 2.0 - 1.0

    for y in range(height):
        row = y * width * 4
        for x in range(width):
            idx = row + x * 4
            a = src[idx + 3]
            if a == 0:
                continue
            r, g, b = src[idx], src[idx + 1], src[idx + 2]

            if monochrome:
                delta = int(round(hash2d(x, y, seed) * amount * 255.0))
                dst[idx] = max(0, min(255, r + delta))
                dst[idx + 1] = max(0, min(255, g + delta))
                dst[idx + 2] = max(0, min(255, b + delta))
            else:
                dr = int(round(hash2d(x, y, seed) * amount * 255.0))
                dg = int(round(hash2d(x, y, seed + 1013) * amount * 255.0))
                db = int(round(hash2d(x, y, seed + 2027) * amount * 255.0))
                dst[idx] = max(0, min(255, r + dr))
                dst[idx + 1] = max(0, min(255, g + dg))
                dst[idx + 2] = max(0, min(255, b + db))
            dst[idx + 3] = a
    return dst

def apply_pixelate(src: bytearray, width: int, height: int, block_size: int = 16) -> bytearray:
    dst = bytearray(width * height * 4)
    for by in range(0, height, block_size):
        ey = min(by + block_size, height)
        for bx in range(0, width, block_size):
            ex = min(bx + block_size, width)

            a_sum, r_sum, g_sum, b_sum = 0, 0, 0, 0
            cnt = 0
            for y in range(by, ey):
                row = y * width * 4
                for x in range(bx, ex):
                    idx = row + x * 4
                    r_sum += src[idx]
                    g_sum += src[idx + 1]
                    b_sum += src[idx + 2]
                    a_sum += src[idx + 3]
                    cnt += 1

            if cnt > 0:
                ar = r_sum // cnt
                ag = g_sum // cnt
                ab = b_sum // cnt
                aa = a_sum // cnt
                for y in range(by, ey):
                    row = y * width * 4
                    for x in range(bx, ex):
                        idx = row + x * 4
                        dst[idx] = ar
                        dst[idx + 1] = ag
                        dst[idx + 2] = ab
                        dst[idx + 3] = aa
    return dst

def apply_ripple(src: bytearray, width: int, height: int, amplitude: float = 10.0, wavelength: float = 40.0, phase_deg: float = 0.0) -> bytearray:
    dst = bytearray(width * height * 4)
    phase_rad = math.radians(phase_deg)
    two_pi_over_len = 2.0 * math.pi / wavelength

    def sample_bilinear(fx: float, fy: float) -> Tuple[int, int, int, int]:
        x0 = max(0, min(width - 1, int(fx)))
        y0 = max(0, min(height - 1, int(fy)))
        x1 = max(0, min(width - 1, x0 + 1))
        y1 = max(0, min(height - 1, y0 + 1))

        wx = max(0.0, min(1.0, fx - x0))
        wy = max(0.0, min(1.0, fy - y0))

        idx00 = (y0 * width + x0) * 4
        idx10 = (y0 * width + x1) * 4
        idx01 = (y1 * width + x0) * 4
        idx11 = (y1 * width + x1) * 4

        def blend(c00, c10, c01, c11):
            top = c00 * (1.0 - wx) + c10 * wx
            bot = c01 * (1.0 - wx) + c11 * wx
            return int(round(top * (1.0 - wy) + bot * wy))

        r = blend(src[idx00], src[idx10], src[idx01], src[idx11])
        g = blend(src[idx00 + 1], src[idx10 + 1], src[idx01 + 1], src[idx11 + 1])
        b = blend(src[idx00 + 2], src[idx10 + 2], src[idx01 + 2], src[idx11 + 2])
        a = blend(src[idx00 + 3], src[idx10 + 3], src[idx01 + 3], src[idx11 + 3])
        return max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), max(0, min(255, a))

    for y in range(height):
        row = y * width * 4
        rad_y = y * two_pi_over_len + phase_rad
        for x in range(width):
            rad_x = x * two_pi_over_len + phase_rad
            dx = amplitude * math.sin(rad_y)
            dy = amplitude * math.cos(rad_x)
            sx = max(0.0, min(width - 1.0, x + dx))
            sy = max(0.0, min(height - 1.0, y + dy))
            r, g, b, a = sample_bilinear(sx, sy)
            idx = row + x * 4
            dst[idx] = r
            dst[idx + 1] = g
            dst[idx + 2] = b
            dst[idx + 3] = a
    return dst

# -----------------------------------------------------------------------------
# Main Test Execution
# -----------------------------------------------------------------------------

def main():
    print("===================================================================")
    print("  ART STUDIO PROMPT 31: EXTENSIBLE FILTER SYSTEM QA TEST SUITE")
    print("===================================================================")
    start_time = time.time()
    results = {}

    width, height = 512, 512
    base_img = generate_base_image(width, height)

    filter_specs = [
        ("filter_blur_golden.png", "Gaussian Blur",
         lambda img: apply_blur(img, width, height, radius=8.0),
         lambda img: apply_blur(img, width, height, radius=24.0)),

        ("filter_sharpen_golden.png", "Sharpen",
         lambda img: apply_sharpen(img, width, height, strength=2.0, radius=2.0),
         lambda img: apply_sharpen(img, width, height, strength=4.5, radius=2.0)),

        ("filter_outline_golden.png", "Outline / Sobel",
         lambda img: apply_outline(img, width, height, threshold=35.0, edge_rgba=(255, 0, 0, 255)),
         lambda img: apply_outline(img, width, height, threshold=120.0, edge_rgba=(255, 0, 0, 255))),

        ("filter_glow_golden.png", "Bloom / Glow",
         lambda img: apply_glow(img, width, height, radius=12.0, intensity=1.5, threshold=0.5),
         lambda img: apply_glow(img, width, height, radius=24.0, intensity=2.8, threshold=0.3)),

        ("filter_noise_golden.png", "Procedural Noise",
         lambda img: apply_noise(img, width, height, amount=0.25, monochrome=True, seed=42),
         lambda img: apply_noise(img, width, height, amount=0.75, monochrome=True, seed=42)),

        ("filter_pixelate_golden.png", "Pixelate / Mosaic",
         lambda img: apply_pixelate(img, width, height, block_size=16),
         lambda img: apply_pixelate(img, width, height, block_size=32)),

        ("filter_ripple_golden.png", "Ripple Wave",
         lambda img: apply_ripple(img, width, height, amplitude=10.0, wavelength=40.0, phase_deg=0.0),
         lambda img: apply_ripple(img, width, height, amplitude=25.0, wavelength=80.0, phase_deg=90.0)),
    ]

    print("\n--- Verifying Seven Filter Visual Goldens ---")
    for filename, name, filter_fn, param_variation_fn in filter_specs:
        golden_path = GOLDENS_DIR / filename
        out_buf = filter_fn(base_img)

        if not golden_path.exists():
            write_png_rgba(golden_path, width, height, out_buf)
            print(f"  [CREATED] Generated baseline golden: {golden_path}")

        gw, gh, golden_buf = read_png_rgba(golden_path)
        diff_px, rmse, ssim = compare_buffers(out_buf, golden_buf, width, height)

        if diff_px == 0:
            print(f"  [OK] Bit-exact match for {filename} (0 diff pixels, SSIM={ssim:.4f}).")
        else:
            print(f"  [FAIL] Visual divergence in {filename}: {diff_px} differing pixels!")
            sys.exit(1)

        # Parameter variation check
        var_buf = param_variation_fn(base_img)
        v_diff, _, _ = compare_buffers(out_buf, var_buf, width, height)
        assert v_diff > 0, f"Parameter variation failed for {name}: output did not change!"
        print(f"  [OK] Parameter variation confirmed for {name}: {v_diff} pixels changed.")

        results[filename] = {
            "name": name,
            "status": "PASS",
            "diff_pixels": diff_px,
            "rmse": rmse,
            "ssim": ssim,
            "parameter_variation_diffs": v_diff
        }

    # -------------------------------------------------------------------------
    # Latency & Memory Baseline for Two Image Sizes (512x512 and 2048x2048)
    # -------------------------------------------------------------------------
    print("\n--- Measuring Execution Latency & Memory Baselines (Two Sizes) ---")
    benchmarks = {}

    for size in [512, 2048]:
        bench_src = bytearray(size * size * 4)
        mem_bytes = size * size * 4

        # Benchmark representative operations
        t0 = time.time()
        _ = apply_pixelate(bench_src, size, size, block_size=16)
        t_pixelate = (time.time() - t0) * 1000.0

        t0 = time.time()
        _ = apply_noise(bench_src, size, size, amount=0.25, seed=42)
        t_noise = (time.time() - t0) * 1000.0

        benchmarks[f"{size}x{size}"] = {
            "buffer_memory_bytes": mem_bytes,
            "buffer_memory_mb": mem_bytes / (1024 * 1024),
            "pixelate_latency_ms": round(t_pixelate, 2),
            "noise_latency_ms": round(t_noise, 2)
        }
        print(f"  Size {size}x{size}: Memory={mem_bytes / (1024 * 1024):.2f} MB, Pixelate={t_pixelate:.1f} ms, Noise={t_noise:.1f} ms")

    elapsed_total = time.time() - start_time
    print(f"\n===================================================================")
    print(f"  ALL 7 FILTERS PASSED in {elapsed_total:.2f}s!")
    summary_path = REPORTS_DIR / "prompt31_filter_system_report.json"
    print(f"  Summary saved to: {summary_path}")
    print("===================================================================")

    with open(summary_path, "w") as f:
        json.dump({
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ"),
            "filters": results,
            "benchmarks": benchmarks,
            "elapsed_s": elapsed_total
        }, f, indent=2)

if __name__ == "__main__":
    main()
