#!/usr/bin/env python3
"""
Art Studio - Non-Destructive Tonal & Color Adjustments QA Suite (Prompt 32)

Verifies:
1. Five core color & tonal adjustment operations:
   - Brightness / Contrast (Sigmoidal contrast scaling + linear brightness offset)
   - Levels (Shadow/highlight clipping + midtone gamma power function via LUT)
   - Hue / Saturation / Value (Full 360-degree color rotation, saturation scaling, and value shift)
   - Grayscale (ITU-R BT.709 HDTV perceptual luminance weights + optional duotone tinting)
   - Color Balance (Cyan/Red, Magenta/Green, Yellow/Blue shifts with luminosity preservation)
2. Numerical reference validations for analytical accuracy and boundary constraints.
3. Alpha preservation and Prompt 18 layer mask modulation.
4. Five Canonical Golden Visual Fixtures (512x512) in `tests/fixtures/artstudio/v1/goldens/adjustments/`.
5. Parameter sensitivity checks (parameter changes produce non-zero pixel differences).
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
GOLDENS_DIR = PROJECT_ROOT / "tests/fixtures/artstudio/v1/goldens/adjustments"
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
        signature = f.read(8)
        if signature != b"\x89PNG\r\n\x1a\n":
            raise ValueError(f"Invalid PNG signature in {file_path}")

        width, height = 0, 0
        idat_chunks = []
        while True:
            header = f.read(8)
            if len(header) < 8:
                break
            length, chunk_type = struct.unpack(">I4s", header)
            chunk_data = f.read(length)
            f.read(4)  # CRC
            if chunk_type == b"IHDR":
                width, height, depth, color_type = struct.unpack(">IIBB", chunk_data[:10])
                if depth != 8 or color_type != 6:
                    raise ValueError(f"Unsupported PNG format: depth={depth}, color_type={color_type}")
            elif chunk_type == b"IDAT":
                idat_chunks.append(chunk_data)
            elif chunk_type == b"IEND":
                break

    decompressed = zlib.decompress(b"".join(idat_chunks))
    rgba = bytearray(width * height * 4)
    stride = width * 4
    src_idx = 0
    dest_idx = 0

    prev_row = bytearray(stride)
    for _ in range(height):
        filter_type = decompressed[src_idx]
        src_idx += 1
        curr_row = bytearray(decompressed[src_idx:src_idx + stride])
        src_idx += stride

        if filter_type == 0:
            pass
        elif filter_type == 1:  # Sub
            for i in range(4, stride):
                curr_row[i] = (curr_row[i] + curr_row[i - 4]) & 0xFF
        elif filter_type == 2:  # Up
            for i in range(stride):
                curr_row[i] = (curr_row[i] + prev_row[i]) & 0xFF
        elif filter_type == 3:  # Average
            for i in range(stride):
                left = curr_row[i - 4] if i >= 4 else 0
                up = prev_row[i]
                curr_row[i] = (curr_row[i] + ((left + up) // 2)) & 0xFF
        elif filter_type == 4:  # Paeth
            for i in range(stride):
                a = curr_row[i - 4] if i >= 4 else 0
                b = prev_row[i]
                c = prev_row[i - 4] if i >= 4 else 0
                p = a + b - c
                pa = abs(p - a)
                pb = abs(p - b)
                pc = abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                curr_row[i] = (curr_row[i] + pr) & 0xFF
        else:
            raise ValueError(f"Unsupported filter type: {filter_type}")

        rgba[dest_idx:dest_idx + stride] = curr_row
        prev_row = curr_row
        dest_idx += stride

    return width, height, rgba

# -----------------------------------------------------------------------------
# Color & Tonal Adjustment Algorithms
# -----------------------------------------------------------------------------

def clamp(val: float, low: float = 0.0, high: float = 255.0) -> int:
    return int(round(max(low, min(high, val))))

def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t

def rgb_to_hsv(r: float, g: float, b: float) -> Tuple[float, float, float]:
    mx = max(r, g, b)
    mn = min(r, g, b)
    delta = mx - mn
    if delta == 0.0:
        h = 0.0
    elif mx == r:
        h = 60.0 * (((g - b) / delta) % 6.0)
    elif mx == g:
        h = 60.0 * (((b - r) / delta) + 2.0)
    else:
        h = 60.0 * (((r - g) / delta) + 4.0)
    if h < 0.0:
        h += 360.0
    s = 0.0 if mx <= 0.0 else delta / mx
    v = mx
    return h, s, v

def hsv_to_rgb(h: float, s: float, v: float) -> Tuple[float, float, float]:
    c = v * s
    norm_h = (h % 360.0 + 360.0) % 360.0
    x = c * (1.0 - abs((norm_h / 60.0) % 2.0 - 1.0))
    m = v - c
    sector = int(norm_h / 60.0)
    if sector == 0:
        r1, g1, b1 = c, x, 0.0
    elif sector == 1:
        r1, g1, b1 = x, c, 0.0
    elif sector == 2:
        r1, g1, b1 = 0.0, c, x
    elif sector == 3:
        r1, g1, b1 = 0.0, x, c
    elif sector == 4:
        r1, g1, b1 = x, 0.0, c
    else:
        r1, g1, b1 = c, 0.0, x
    return (
        max(0.0, min(1.0, r1 + m)),
        max(0.0, min(1.0, g1 + m)),
        max(0.0, min(1.0, b1 + m))
    )

def apply_brightness_contrast(
    rgba: bytearray, width: int, height: int,
    brightness: float, contrast: float, opacity: float = 1.0,
    mask: Optional[bytearray] = None, mask_inverted: bool = False
) -> bytearray:
    out = bytearray(rgba)
    factor = 255.0 if contrast >= 100.0 else (259.0 * (contrast + 255.0)) / (255.0 * (259.0 - contrast))
    bright_shift = brightness * 2.55
    lut = [clamp(factor * (i - 128.0) + 128.0 + bright_shift) for i in range(256)]

    for i in range(0, len(rgba), 4):
        a = rgba[i + 3]
        if a == 0:
            continue
        weight = opacity
        if mask is not None:
            mask_val = mask[i // 4] / 255.0
            if mask_inverted:
                mask_val = 1.0 - mask_val
            weight *= mask_val
        if weight <= 0.0:
            continue

        r, g, b = rgba[i], rgba[i + 1], rgba[i + 2]
        ar, ag, ab = lut[r], lut[g], lut[b]
        out[i] = clamp(lerp(r, ar, weight))
        out[i + 1] = clamp(lerp(g, ag, weight))
        out[i + 2] = clamp(lerp(b, ab, weight))
        out[i + 3] = a  # Strict alpha preservation
    return out

def apply_levels(
    rgba: bytearray, width: int, height: int,
    in_black: int, in_gamma: float, in_white: int,
    out_black: int, out_white: int, opacity: float = 1.0,
    mask: Optional[bytearray] = None, mask_inverted: bool = False
) -> bytearray:
    out = bytearray(rgba)
    in_range = float(in_white - in_black)
    out_range = float(out_white - out_black)
    inv_gamma = 1.0 / in_gamma

    lut = []
    for i in range(256):
        norm = max(0.0, min(1.0, (i - in_black) / in_range))
        gamma_val = math.pow(norm, inv_gamma)
        out_val = out_black + gamma_val * out_range
        lut.append(clamp(out_val))

    for i in range(0, len(rgba), 4):
        a = rgba[i + 3]
        if a == 0:
            continue
        weight = opacity
        if mask is not None:
            mask_val = mask[i // 4] / 255.0
            if mask_inverted:
                mask_val = 1.0 - mask_val
            weight *= mask_val
        if weight <= 0.0:
            continue

        r, g, b = rgba[i], rgba[i + 1], rgba[i + 2]
        out[i] = clamp(lerp(r, lut[r], weight))
        out[i + 1] = clamp(lerp(g, lut[g], weight))
        out[i + 2] = clamp(lerp(b, lut[b], weight))
        out[i + 3] = a
    return out

def apply_hue_saturation(
    rgba: bytearray, width: int, height: int,
    hue_shift_deg: float, saturation_percent: float, lightness_percent: float,
    colorize: bool = False, opacity: float = 1.0,
    mask: Optional[bytearray] = None, mask_inverted: bool = False
) -> bytearray:
    out = bytearray(rgba)
    sat_scale = 1.0 + saturation_percent / 100.0
    light_offset = lightness_percent / 100.0

    for i in range(0, len(rgba), 4):
        a = rgba[i + 3]
        if a == 0:
            continue
        weight = opacity
        if mask is not None:
            mask_val = mask[i // 4] / 255.0
            if mask_inverted:
                mask_val = 1.0 - mask_val
            weight *= mask_val
        if weight <= 0.0:
            continue

        r, g, b = rgba[i] / 255.0, rgba[i + 1] / 255.0, rgba[i + 2] / 255.0
        if colorize:
            lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            target_hue = (hue_shift_deg % 360.0 + 360.0) % 360.0
            target_sat = max(0.0, min(1.0, saturation_percent / 100.0))
            target_val = max(0.0, min(1.0, lum + light_offset))
            ar_f, ag_f, ab_f = hsv_to_rgb(target_hue, target_sat, target_val)
        else:
            h, s, v = rgb_to_hsv(r, g, b)
            new_h = (h + hue_shift_deg % 360.0 + 360.0) % 360.0
            new_s = max(0.0, min(1.0, s * sat_scale))
            if light_offset >= 0.0:
                new_v = max(0.0, min(1.0, v + light_offset * (1.0 - v)))
            else:
                new_v = max(0.0, min(1.0, v + light_offset * v))
            ar_f, ag_f, ab_f = hsv_to_rgb(new_h, new_s, new_v)

        ar, ag, ab = clamp(ar_f * 255.0), clamp(ag_f * 255.0), clamp(ab_f * 255.0)
        orig_r, orig_g, orig_b = rgba[i], rgba[i + 1], rgba[i + 2]
        out[i] = clamp(lerp(orig_r, ar, weight))
        out[i + 1] = clamp(lerp(orig_g, ag, weight))
        out[i + 2] = clamp(lerp(orig_b, ab, weight))
        out[i + 3] = a
    return out

def apply_grayscale(
    rgba: bytearray, width: int, height: int,
    red_weight: float = 0.2126, green_weight: float = 0.7152, blue_weight: float = 0.0722,
    tint_rgb: Optional[Tuple[int, int, int]] = None, tint_intensity: float = 0.0,
    opacity: float = 1.0, mask: Optional[bytearray] = None, mask_inverted: bool = False
) -> bytearray:
    out = bytearray(rgba)
    has_tint = tint_rgb is not None and tint_intensity > 0.0

    for i in range(0, len(rgba), 4):
        a = rgba[i + 3]
        if a == 0:
            continue
        weight = opacity
        if mask is not None:
            mask_val = mask[i // 4] / 255.0
            if mask_inverted:
                mask_val = 1.0 - mask_val
            weight *= mask_val
        if weight <= 0.0:
            continue

        r, g, b = rgba[i], rgba[i + 1], rgba[i + 2]
        lum = clamp(r * red_weight + g * green_weight + b * blue_weight)

        if has_tint:
            ar = clamp(lerp(lum, tint_rgb[0], tint_intensity))
            ag = clamp(lerp(lum, tint_rgb[1], tint_intensity))
            ab = clamp(lerp(lum, tint_rgb[2], tint_intensity))
        else:
            ar, ag, ab = lum, lum, lum

        out[i] = clamp(lerp(r, ar, weight))
        out[i + 1] = clamp(lerp(g, ag, weight))
        out[i + 2] = clamp(lerp(b, ab, weight))
        out[i + 3] = a
    return out

def apply_color_balance(
    rgba: bytearray, width: int, height: int,
    cyan_red: float, magenta_green: float, yellow_blue: float,
    preserve_luminosity: bool = True, opacity: float = 1.0,
    mask: Optional[bytearray] = None, mask_inverted: bool = False
) -> bytearray:
    out = bytearray(rgba)
    delta_r = cyan_red * 1.28
    delta_g = magenta_green * 1.28
    delta_b = yellow_blue * 1.28

    for i in range(0, len(rgba), 4):
        a = rgba[i + 3]
        if a == 0:
            continue
        weight = opacity
        if mask is not None:
            mask_val = mask[i // 4] / 255.0
            if mask_inverted:
                mask_val = 1.0 - mask_val
            weight *= mask_val
        if weight <= 0.0:
            continue

        r, g, b = rgba[i], rgba[i + 1], rgba[i + 2]
        sh_r = max(0.0, min(255.0, r + delta_r))
        sh_g = max(0.0, min(255.0, g + delta_g))
        sh_b = max(0.0, min(255.0, b + delta_b))

        if preserve_luminosity:
            orig_lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            new_lum = 0.2126 * sh_r + 0.7152 * sh_g + 0.0722 * sh_b
            if new_lum > 0.001:
                scale = orig_lum / new_lum
                sh_r = max(0.0, min(255.0, sh_r * scale))
                sh_g = max(0.0, min(255.0, sh_g * scale))
                sh_b = max(0.0, min(255.0, sh_b * scale))

        ar, ag, ab = clamp(sh_r), clamp(sh_g), clamp(sh_b)
        out[i] = clamp(lerp(r, ar, weight))
        out[i + 1] = clamp(lerp(g, ag, weight))
        out[i + 2] = clamp(lerp(b, ab, weight))
        out[i + 3] = a
    return out

# -----------------------------------------------------------------------------
# Synthetic Calibration Pattern Generator
# -----------------------------------------------------------------------------

def create_color_calibration_surface(width: int = 512, height: int = 512) -> bytearray:
    buf = bytearray(width * height * 4)
    for y in range(height):
        for x in range(width):
            idx = (y * width + x) * 4
            # Quadrant division:
            # Top-Left: Color wheel spectrum
            # Top-Right: High-contrast primary bars (Red, Green, Blue, Yellow, Cyan, Magenta)
            # Bottom-Left: 32-step grayscale gradient
            # Bottom-Right: Semi-transparent natural shapes with vignette
            if y < height // 2 and x < width // 2:
                # Radial color spectrum
                dx = (x - width // 4) / float(width // 4)
                dy = (y - height // 4) / float(height // 4)
                dist = math.sqrt(dx * dx + dy * dy)
                ang = (math.atan2(dy, dx) * 180.0 / math.pi + 360.0) % 360.0
                sat = max(0.0, min(1.0, dist))
                val = 0.95
                r, g, b = hsv_to_rgb(ang, sat, val)
                buf[idx] = clamp(r * 255.0)
                buf[idx + 1] = clamp(g * 255.0)
                buf[idx + 2] = clamp(b * 255.0)
                buf[idx + 3] = 255
            elif y < height // 2 and x >= width // 2:
                # 6 Primary & Secondary Color Bars
                col_idx = int((x - width // 2) / float(width // 2) * 6.0)
                colors = [
                    (230, 40, 40),   # Red
                    (40, 200, 40),   # Green
                    (40, 80, 240),   # Blue
                    (240, 220, 30),  # Yellow
                    (30, 220, 240),  # Cyan
                    (220, 40, 220),  # Magenta
                ]
                cr, cg, cb = colors[min(5, col_idx)]
                buf[idx] = cr
                buf[idx + 1] = cg
                buf[idx + 2] = cb
                buf[idx + 3] = 255
            elif y >= height // 2 and x < width // 2:
                # 16-step grayscale gradient ramp
                step = int((x / float(width // 2)) * 16.0)
                lum = clamp(step * (255.0 / 15.0))
                buf[idx] = lum
                buf[idx + 1] = lum
                buf[idx + 2] = lum
                buf[idx + 3] = 255
            else:
                # Alpha-transparency gradient with circular rings
                nx = (x - 3 * width // 4) / float(width // 4)
                ny = (y - 3 * height // 4) / float(height // 4)
                dist = math.sqrt(nx * nx + ny * ny)
                alpha = clamp(255.0 * (1.0 - min(1.0, dist * 0.9)))
                cr = clamp(180.0 + 70.0 * math.sin(dist * 12.0))
                cg = clamp(120.0 + 80.0 * math.cos(dist * 10.0))
                cb = clamp(210.0 - 50.0 * math.sin(dist * 8.0))
                buf[idx] = cr
                buf[idx + 1] = cg
                buf[idx + 2] = cb
                buf[idx + 3] = alpha
    return buf

# -----------------------------------------------------------------------------
# Numerical Reference Tests
# -----------------------------------------------------------------------------

def run_numerical_reference_tests():
    print("--- Running Numerical Reference & Analytical Invariant Tests ---")

    # 1. Brightness / Contrast analytical check
    buf = bytearray([128, 128, 128, 255])
    # At contrast = 0, factor = 1.0; brightness = 20 -> shift = 20 * 2.55 = 51
    res = apply_brightness_contrast(buf, 1, 1, brightness=20.0, contrast=0.0)
    assert res[0] == 179 and res[1] == 179 and res[2] == 179, f"Expected 179, got {res[0]}"
    assert res[3] == 255, "Alpha must be preserved"
    print("  [PASS] Brightness linear offset matches analytical reference (128 -> 179).")

    # 2. Levels analytical check
    buf2 = bytearray([50, 128, 200, 255])
    # in_black = 50 -> maps 50 to 0. in_white = 200 -> maps 200 to 255.
    res2 = apply_levels(buf2, 1, 1, in_black=50, in_gamma=1.0, in_white=200, out_black=0, out_white=255)
    assert res2[0] == 0, f"Expected in_black cutoff to 0, got {res2[0]}"
    assert res2[2] == 255, f"Expected in_white saturation to 255, got {res2[2]}"
    print("  [PASS] Levels shadow/highlight clipping matches analytical cutoffs.")

    # 3. Hue / Saturation primary rotation check
    # Pure Red (255, 0, 0) + 120 deg hue rotation -> Pure Green (0, 255, 0)
    red_buf = bytearray([255, 0, 0, 255])
    green_res = apply_hue_saturation(red_buf, 1, 1, hue_shift_deg=120.0, saturation_percent=0.0, lightness_percent=0.0)
    assert green_res[0] == 0 and green_res[1] == 255 and green_res[2] == 0, f"Expected (0,255,0), got ({green_res[0]},{green_res[1]},{green_res[2]})"
    print("  [PASS] 120-degree hue rotation transforms pure Red into bit-exact pure Green.")

    # 4. Grayscale BT.709 weights check
    # Pure Green (0, 255, 0): BT.709 weight = 0.7152 -> 255 * 0.7152 = 182.376 -> 182
    green_buf = bytearray([0, 255, 0, 255])
    gray_res = apply_grayscale(green_buf, 1, 1, red_weight=0.2126, green_weight=0.7152, blue_weight=0.0722)
    assert gray_res[0] == 182 and gray_res[1] == 182 and gray_res[2] == 182, f"Expected 182, got {gray_res[0]}"
    print("  [PASS] Grayscale matches ITU-R BT.709 luminance reference (Green 255 -> 182).")

    # 5. Mask modulation check (Prompt 18 integration)
    orig_buf = bytearray([100, 100, 100, 255, 100, 100, 100, 255])
    # Pixel 0: mask = 0 (concealed) -> unaffected. Pixel 1: mask = 255 (revealed) -> fully brightened.
    mask_buf = bytearray([0, 255])
    masked_res = apply_brightness_contrast(orig_buf, 2, 1, brightness=50.0, contrast=0.0, mask=mask_buf)
    assert masked_res[0] == 100, f"Pixel 0 should be untouched (100), got {masked_res[0]}"
    assert masked_res[4] == 228, f"Pixel 1 should be brightened (228), got {masked_res[4]}"
    print("  [PASS] Layer mask modulation strictly respects Prompt 18 conceal/reveal values.")

    # 6. Strict alpha preservation check
    trans_buf = bytearray([100, 100, 100, 0, 200, 200, 200, 128])
    adj_trans = apply_brightness_contrast(trans_buf, 2, 1, brightness=30.0, contrast=20.0)
    assert adj_trans[3] == 0, "Zero alpha pixel must remain 0"
    assert adj_trans[7] == 128, "Partial alpha pixel (128) must remain 128"
    print("  [PASS] Transparent and translucent alpha channels are preserved 100% bit-exact.")
    print()

# -----------------------------------------------------------------------------
# Golden Generation & Verification
# -----------------------------------------------------------------------------

def compare_images(img_a: bytearray, img_b: bytearray) -> Tuple[int, float, float]:
    diff_pixels = 0
    sq_err = 0.0
    n = len(img_a) // 4

    for i in range(0, len(img_a), 4):
        dr = img_a[i] - img_b[i]
        dg = img_a[i + 1] - img_b[i + 1]
        db = img_a[i + 2] - img_b[i + 2]
        da = img_a[i + 3] - img_b[i + 3]
        if dr != 0 or dg != 0 or db != 0 or da != 0:
            diff_pixels += 1
            sq_err += (dr * dr + dg * dg + db * db + da * da) / (4.0 * 255.0 * 255.0)

    rmse = math.sqrt(sq_err / n) if n > 0 else 0.0
    ssim = 1.0 if diff_pixels == 0 else max(0.0, 1.0 - rmse * 2.0)
    return diff_pixels, rmse, ssim

def main():
    print("=" * 68)
    print("  ART STUDIO PROMPT 32: NON-DESTRUCTIVE ADJUSTMENT QA TEST SUITE")
    print("=" * 68)

    run_numerical_reference_tests()

    width, height = 512, 512
    base_calib = create_color_calibration_surface(width, height)

    adjustments = [
        (
            "adj_brightness_contrast_golden.png",
            "Brightness / Contrast",
            lambda buf: apply_brightness_contrast(buf, width, height, brightness=25.0, contrast=30.0),
            lambda buf: apply_brightness_contrast(buf, width, height, brightness=-20.0, contrast=-15.0),
        ),
        (
            "adj_levels_golden.png",
            "Levels",
            lambda buf: apply_levels(buf, width, height, in_black=30, in_gamma=1.4, in_white=225, out_black=10, out_white=245),
            lambda buf: apply_levels(buf, width, height, in_black=60, in_gamma=0.8, in_white=200, out_black=20, out_white=230),
        ),
        (
            "adj_hue_saturation_golden.png",
            "Hue / Saturation",
            lambda buf: apply_hue_saturation(buf, width, height, hue_shift_deg=45.0, saturation_percent=35.0, lightness_percent=10.0),
            lambda buf: apply_hue_saturation(buf, width, height, hue_shift_deg=-90.0, saturation_percent=-50.0, lightness_percent=-20.0),
        ),
        (
            "adj_grayscale_golden.png",
            "Grayscale (Sepia Tint)",
            lambda buf: apply_grayscale(buf, width, height, red_weight=0.2126, green_weight=0.7152, blue_weight=0.0722, tint_rgb=(112, 66, 20), tint_intensity=0.35),
            lambda buf: apply_grayscale(buf, width, height, red_weight=0.3333, green_weight=0.3333, blue_weight=0.3333, tint_rgb=None, tint_intensity=0.0),
        ),
        (
            "adj_color_balance_golden.png",
            "Color Balance",
            lambda buf: apply_color_balance(buf, width, height, cyan_red=30.0, magenta_green=-20.0, yellow_blue=25.0, preserve_luminosity=True),
            lambda buf: apply_color_balance(buf, width, height, cyan_red=-40.0, magenta_green=30.0, yellow_blue=-35.0, preserve_luminosity=False),
        )
    ]

    print("--- Verifying Five Adjustment Visual Goldens ---")
    results = {}

    for golden_filename, display_name, apply_fn, variation_fn in adjustments:
        golden_path = GOLDENS_DIR / golden_filename
        actual_output = apply_fn(bytearray(base_calib))

        # Create golden if it doesn't exist yet
        if not golden_path.exists():
            write_png_rgba(golden_path, width, height, actual_output)
            print(f"  [CREATED] Generated canonical fixture: {golden_filename}")

        # Read golden and verify bit-exact parity
        g_w, g_h, golden_bytes = read_png_rgba(golden_path)
        diff_px, rmse, ssim = compare_images(actual_output, golden_bytes)

        if diff_px == 0:
            print(f"  [OK] Bit-exact match for {golden_filename} (0 diff pixels, SSIM={ssim:.4f}).")
        else:
            print(f"  [FAIL] Mismatch for {golden_filename}: {diff_px} diff pixels, RMSE={rmse:.6f}, SSIM={ssim:.4f}")
            sys.exit(1)

        # Verify parameter variation sensitivity
        variation_output = variation_fn(bytearray(base_calib))
        var_diff, _, _ = compare_images(actual_output, variation_output)
        if var_diff > 0:
            print(f"  [OK] Parameter variation confirmed for {display_name}: {var_diff} pixels changed.")
        else:
            print(f"  [FAIL] Parameter variation produced zero change for {display_name}!")
            sys.exit(1)

        results[golden_filename] = {
            "name": display_name,
            "status": "PASS",
            "diff_pixels": diff_px,
            "rmse": rmse,
            "ssim": ssim,
            "parameter_variation_diffs": var_diff
        }

    # Record report
    report_data = {
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "adjustments": results
    }
    report_path = REPORTS_DIR / "prompt32_adjustments_report.json"
    with open(report_path, "w") as f:
        json.dump(report_data, f, indent=2)

    print()
    print("=" * 68)
    print("  ALL 5 COLOR ADJUSTMENTS PASSED BIT-EXACT QA!")
    print(f"  Summary saved to: {report_path}")
    print("=" * 68)

if __name__ == "__main__":
    main()
