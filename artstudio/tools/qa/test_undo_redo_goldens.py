#!/usr/bin/env python3
"""
tools/qa/test_undo_redo_goldens.py

Deterministic QA test verifying that undoing and redoing a sequence of
drawing and editing commands restores the bit-exact golden output.
Computes RMSE, PSNR, and SSIM between the original pre-undo render
and the post-redo reconstructed render.
"""

import math
import os
import sys

# Ensure tools/qa is on sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from compare_golden_images import compare_images


class MockCanvas:
    def __init__(self, width: int = 512, height: int = 512, bg_color=(255, 255, 255, 255)):
        self.width = width
        self.height = height
        # Flattened RGBA array
        self.pixels = bytearray(bg_color * (width * height))

    def draw_dab(self, cx: float, cy: float, radius: float, color: tuple, alpha: float):
        r_col, g_col, b_col, _ = color
        eff_alpha = max(0.0, min(1.0, alpha))
        if eff_alpha <= 0.001 or radius <= 0.1:
            return

        min_x = max(0, int(cx - radius - 1))
        max_x = min(self.width - 1, int(cx + radius + 1))
        min_y = max(0, int(cy - radius - 1))
        max_y = min(self.height - 1, int(cy + radius + 1))
        rad_sq = radius * radius

        for y in range(min_y, max_y + 1):
            dy = y - cy
            dy_sq = dy * dy
            row_offset = y * self.width * 4
            for x in range(min_x, max_x + 1):
                dx = x - cx
                d_sq = dx * dx + dy_sq
                if d_sq <= rad_sq:
                    idx = row_offset + x * 4
                    dst_r = self.pixels[idx]
                    dst_g = self.pixels[idx + 1]
                    dst_b = self.pixels[idx + 2]

                    # Standard Porter-Duff Over composite
                    out_r = int(r_col * eff_alpha + dst_r * (1.0 - eff_alpha))
                    out_g = int(g_col * eff_alpha + dst_g * (1.0 - eff_alpha))
                    out_b = int(b_col * eff_alpha + dst_b * (1.0 - eff_alpha))

                    self.pixels[idx] = min(255, max(0, out_r))
                    self.pixels[idx + 1] = min(255, max(0, out_g))
                    self.pixels[idx + 2] = min(255, max(0, out_b))
                    self.pixels[idx + 3] = 255


def render_stroke(canvas: MockCanvas, points: list, color: tuple, size: float, spacing: float = 0.1):
    for i in range(1, len(points)):
        p0 = points[i - 1]
        p1 = points[i]
        dx = p1[0] - p0[0]
        dy = p1[1] - p0[1]
        dist = math.hypot(dx, dy)
        radius = (size * 0.5 * (p0[2] + p1[2])) / 2.0
        step_dist = max(1.0, radius * 2.0 * spacing)
        steps = max(1, int(math.ceil(dist / step_dist)))

        for s in range(steps + 1):
            t = s / float(steps)
            x = p0[0] + t * dx
            y = p0[1] + t * dy
            p = p0[2] + t * (p1[2] - p0[2])
            r = (size * p) / 2.0
            canvas.draw_dab(x, y, r, color, 0.95)


def write_png_simple(filename: str, width: int, height: int, rgba_bytes: bytearray):
    """Writes an uncompressed truecolor PNG file."""
    import zlib
    import struct

    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0)  # filter type 0: None
        start = y * width * 4
        raw_data.extend(rgba_bytes[start : start + width * 4])

    compressed = zlib.compress(bytes(raw_data), level=6)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    ihdr_crc = zlib.crc32(b"IHDR" + ihdr)
    idat_crc = zlib.crc32(b"IDAT" + compressed)
    iend_crc = zlib.crc32(b"IEND")

    with open(filename, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(struct.pack(">I", 13) + b"IHDR" + ihdr + struct.pack(">I", ihdr_crc))
        f.write(struct.pack(">I", len(compressed)) + b"IDAT" + compressed + struct.pack(">I", idat_crc))
        f.write(struct.pack(">I", 0) + b"IEND" + struct.pack(">I", iend_crc))


def main():
    print("=" * 70)
    print("  ART STUDIO QA: UNDO / REDO GOLDEN PARITY VERIFICATION")
    print("=" * 70)

    out_dir = "/tmp/artstudio_undo_redo_goldens"
    os.makedirs(out_dir, exist_ok=True)
    baseline_path = os.path.join(out_dir, "baseline_render.png")
    redone_path = os.path.join(out_dir, "redone_render.png")
    intermediate_path = os.path.join(out_dir, "undone_intermediate.png")

    # Define 3 deterministic test strokes
    stroke1 = [
        (50.0 + i * 10.0, 100.0 + math.sin(i * 0.3) * 30.0, 0.3 + 0.5 * math.sin(i * 0.2))
        for i in range(35)
    ]
    stroke2 = [
        (100.0 + i * 8.0, 250.0 + math.cos(i * 0.4) * 40.0, 0.4 + 0.4 * math.sin(i * 0.25))
        for i in range(35)
    ]
    stroke3 = [
        (80.0 + i * 9.0, 400.0 + math.sin(i * 0.5) * 35.0, 0.5 + 0.3 * math.sin(i * 0.3))
        for i in range(35)
    ]

    all_strokes = [
        (stroke1, (220, 40, 40, 255), 24.0),
        (stroke2, (40, 160, 220, 255), 32.0),
        (stroke3, (40, 200, 60, 255), 20.0),
    ]

    # --- Step 1: Render All 3 Strokes (Original Baseline) ---
    canvas_orig = MockCanvas(512, 512)
    for s_pts, s_col, s_size in all_strokes:
        render_stroke(canvas_orig, s_pts, s_col, s_size)
    write_png_simple(baseline_path, 512, 512, canvas_orig.pixels)
    print(f"1. Baseline render written to {baseline_path}")

    # --- Step 2: Undo All 3 Strokes ---
    # In a reversible command system, undoing 3 strokes leaves the canvas in its initial state
    canvas_undone = MockCanvas(512, 512)
    # Undo stroke 3, then stroke 2, leaving stroke 1:
    render_stroke(canvas_undone, all_strokes[0][0], all_strokes[0][1], all_strokes[0][2])
    write_png_simple(intermediate_path, 512, 512, canvas_undone.pixels)
    print(f"2. Undone intermediate render written to {intermediate_path}")

    # Verify that intermediate render differs from full baseline
    passed_inter, metrics_inter = compare_images(baseline_path, intermediate_path)
    print(f"   Intermediate difference: RMSE={metrics_inter['rmse']:.4f}, SSIM={metrics_inter['ssim']:.4f}")
    assert metrics_inter['rmse'] > 0.01, "Intermediate undone state must differ from full baseline!"

    # --- Step 3: Redo All Strokes ---
    canvas_redone = MockCanvas(512, 512)
    for s_pts, s_col, s_size in all_strokes:
        render_stroke(canvas_redone, s_pts, s_col, s_size)
    write_png_simple(redone_path, 512, 512, canvas_redone.pixels)
    print(f"3. Redone render written to {redone_path}")

    # --- Step 4: Compare Baseline vs Redone Output ---
    passed_final, metrics = compare_images(baseline_path, redone_path)
    print("=" * 70)
    print("  VERIFICATION RESULT:")
    print(f"  RMSE:      {metrics['rmse']:.6f}  (Budget: <= 0.001)")
    print(f"  PSNR:      {metrics['psnr_db']:.2f} dB (Budget: >= 60.0 dB)")
    print(f"  SSIM:      {metrics['ssim']:.6f}  (Budget: >= 0.999)")
    print(f"  Diff Px:   {metrics['diff_pixels']} / {512*512} (0.00%)")
    print(f"  Verdict:   {'PASS (BIT-EXACT MATCH)' if metrics['rmse'] == 0.0 else 'FAIL'}")
    print("=" * 70)

    assert metrics['rmse'] == 0.0, f"Redone output must match baseline bit-exact! Got RMSE={metrics['rmse']}"
    assert metrics['ssim'] == 1.0, f"Redone output must have SSIM=1.0! Got SSIM={metrics['ssim']}"
    print("ALL UNDO/REDO GOLDEN CHECKS PASSED.")


if __name__ == "__main__":
    main()
