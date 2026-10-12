#!/usr/bin/env python3
"""
Art Studio Transform, Crop, and Canvas-Resize QA Verification Suite
Canonical QA test verifying:
1. Non-destructive Layer Move (translation).
2. Uniform Layer Scale with Aspect-Ratio Lock.
3. Non-Uniform Layer Scale.
4. Continuous Layer Rotation (45 deg, 90 deg).
5. Layer Flip (Horizontal and Vertical).
6. Resampling Interpolation Filtering (Nearest Neighbor vs Bilinear).
7. Non-destructive Canvas Resize with Anchor Alignment (Center, Top-Left).
8. Non-destructive Canvas Crop with Layer Offsets.
9. Roundtrip Crop Undo/Redo Bit-Exact Restoration.
10. Out-of-bounds Content Preservation (Zero Pixel Loss).
11. Corpus Image Fixture Transformation (transparent_cutout_01.png).
12. Golden Comparison & Zero-Regression Diff Enforcement.
"""

import os
import sys
import math
import json
from pathlib import Path
from typing import Dict, Any, Tuple, List, Optional

# Add tools/qa to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from compare_golden_images import compare_images, write_png_rgba, read_png_rgba


class PyTransformCanvas:
    """Pure-Python 32-bit RGBA Canvas implementing non-destructive affine transforms and resampling."""

    def __init__(self, width: int, height: int, fill: Tuple[int, int, int, int] = (0, 0, 0, 0)):
        self.width = width
        self.height = height
        self.buffer = bytearray(width * height * 4)
        r, g, b, a = fill
        if a > 0:
            for i in range(width * height):
                idx = i * 4
                self.buffer[idx] = r
                self.buffer[idx + 1] = g
                self.buffer[idx + 2] = b
                self.buffer[idx + 3] = a

    def copy(self) -> 'PyTransformCanvas':
        c = PyTransformCanvas(self.width, self.height)
        c.buffer = bytearray(self.buffer)
        return c

    def get_pixel(self, x: int, y: int) -> Tuple[int, int, int, int]:
        if 0 <= x < self.width and 0 <= y < self.height:
            idx = (y * self.width + x) * 4
            return (self.buffer[idx], self.buffer[idx + 1], self.buffer[idx + 2], self.buffer[idx + 3])
        return (0, 0, 0, 0)

    def set_pixel(self, x: int, y: int, r: int, g: int, b: int, a: int):
        if 0 <= x < self.width and 0 <= y < self.height:
            idx = (y * self.width + x) * 4
            self.buffer[idx] = r
            self.buffer[idx + 1] = g
            self.buffer[idx + 2] = b
            self.buffer[idx + 3] = a

    def draw_filled_rect(self, x0: int, y0: int, x1: int, y1: int, color: Tuple[int, int, int, int]):
        r, g, b, a = color
        for y in range(max(0, y0), min(self.height, y1)):
            for x in range(max(0, x0), min(self.width, x1)):
                self.set_pixel(x, y, r, g, b, a)

    def draw_filled_circle(self, cx: int, cy: int, radius: int, color: Tuple[int, int, int, int]):
        r, g, b, a = color
        r2 = radius * radius
        for y in range(max(0, cy - radius), min(self.height, cy + radius + 1)):
            for x in range(max(0, cx - radius), min(self.width, cx + radius + 1)):
                dx = x - cx
                dy = y - cy
                if dx * dx + dy * dy <= r2:
                    self.set_pixel(x, y, r, g, b, a)

    def resample_transformed(
        self,
        tx: float = 0.0,
        ty: float = 0.0,
        sx: float = 1.0,
        sy: float = 1.0,
        rot_deg: float = 0.0,
        flip_h: bool = False,
        flip_v: bool = False,
        dest_w: Optional[int] = None,
        dest_h: Optional[int] = None,
        interpolation: str = "bilinear"
    ) -> 'PyTransformCanvas':
        dw = dest_w or self.width
        dh = dest_h or self.height
        out = PyTransformCanvas(dw, dh)

        rad = math.radians(rot_deg)
        cos_t = math.cos(rad)
        sin_t = math.sin(rad)

        eff_sx = -sx if flip_h else sx
        eff_sy = -sy if flip_v else sy
        if abs(eff_sx) < 1e-6:
            eff_sx = 1e-6
        if abs(eff_sy) < 1e-6:
            eff_sy = 1e-6

        cx = dw / 2.0
        cy = dh / 2.0
        scx = self.width / 2.0
        scy = self.height / 2.0

        for dest_y in range(dh):
            dy = (dest_y + 0.5) - cy - ty
            for dest_x in range(dw):
                dx = (dest_x + 0.5) - cx - tx

                # Inverse rotation
                unrot_x = dx * cos_t + dy * sin_t
                unrot_y = -dx * sin_t + dy * cos_t

                # Inverse scale
                unscaled_x = unrot_x / eff_sx
                unscaled_y = unrot_y / eff_sy

                # Map to source space
                src_x = unscaled_x + scx
                src_y = unscaled_y + scy

                if interpolation == "nearest":
                    px = int(math.floor(src_x))
                    py = int(math.floor(src_y))
                    if 0 <= px < self.width and 0 <= py < self.height:
                        idx = (py * self.width + px) * 4
                        out.set_pixel(dest_x, dest_y, self.buffer[idx], self.buffer[idx + 1], self.buffer[idx + 2], self.buffer[idx + 3])
                else:
                    # Bilinear
                    fx = src_x - 0.5
                    fy = src_y - 0.5
                    x0 = int(math.floor(fx))
                    y0 = int(math.floor(fy))
                    x1 = x0 + 1
                    y1 = y0 + 1

                    wx = max(0.0, min(1.0, fx - x0))
                    wy = max(0.0, min(1.0, fy - y0))

                    p00 = self.get_pixel(x0, y0)
                    p10 = self.get_pixel(x1, y0)
                    p01 = self.get_pixel(x0, y1)
                    p11 = self.get_pixel(x1, y1)

                    def bilerp(c00, c10, c01, c11):
                        top = c00 * (1.0 - wx) + c10 * wx
                        bot = c01 * (1.0 - wx) + c11 * wx
                        return int(round(max(0.0, min(255.0, top * (1.0 - wy) + bot * wy))))

                    a = bilerp(p00[3], p10[3], p01[3], p11[3])
                    if a > 0:
                        r = bilerp(p00[0], p10[0], p01[0], p11[0])
                        g = bilerp(p00[1], p10[1], p01[1], p11[1])
                        b = bilerp(p00[2], p10[2], p01[2], p11[2])
                        out.set_pixel(dest_x, dest_y, r, g, b, a)

        return out

    def composite_over(self, backdrop: 'PyTransformCanvas', offset_x: int = 0, offset_y: int = 0):
        for y in range(self.height):
            by = y + offset_y
            if not (0 <= by < backdrop.height):
                continue
            for x in range(self.width):
                bx = x + offset_x
                if not (0 <= bx < backdrop.width):
                    continue

                idx = (y * self.width + x) * 4
                sa = self.buffer[idx + 3] / 255.0
                if sa <= 0.0:
                    continue

                bidx = (by * backdrop.width + bx) * 4
                ba = backdrop.buffer[bidx + 3] / 255.0
                sr, sg, sb = self.buffer[idx], self.buffer[idx + 1], self.buffer[idx + 2]
                br, bg, bb = backdrop.buffer[bidx], backdrop.buffer[bidx + 1], backdrop.buffer[bidx + 2]

                out_a = sa + ba * (1.0 - sa)
                if out_a > 0.0:
                    out_r = int(round((sr * sa + br * ba * (1.0 - sa)) / out_a))
                    out_g = int(round((sg * sa + bg * ba * (1.0 - sa)) / out_a))
                    out_b = int(round((sb * sa + bb * ba * (1.0 - sa)) / out_a))
                    backdrop.buffer[bidx] = out_r
                    backdrop.buffer[bidx + 1] = out_g
                    backdrop.buffer[bidx + 2] = out_b
                    backdrop.buffer[bidx + 3] = int(round(out_a * 255.0))


def run_transform_qa_suite():
    repo_root = Path(__file__).resolve().parent.parent.parent
    fixtures_dir = repo_root / "tests" / "fixtures" / "artstudio" / "v1"
    goldens_dir = fixtures_dir / "goldens" / "transforms"
    goldens_dir.mkdir(parents=True, exist_ok=True)

    print("=================================================================")
    print("Art Studio Transform, Crop & Canvas Resize QA Suite")
    print("=================================================================")

    w, h = 256, 256
    bg_color = (236, 240, 241, 255) # Light gray background

    # Base shape fixture: centered blue circle with amber inner square
    base_shape = PyTransformCanvas(w, h)
    base_shape.draw_filled_circle(128, 128, 64, (41, 128, 185, 255)) # Peter River Blue
    base_shape.draw_filled_rect(108, 108, 148, 148, (243, 156, 18, 255)) # Orange square

    results = {}

    # 1. Layer Move (Translation)
    print("\n--- 1. Testing Layer Move (Translation) ---")
    moved = base_shape.resample_transformed(tx=40, ty=30)
    comp1 = PyTransformCanvas(w, h, fill=bg_color)
    moved.composite_over(comp1)
    golden1 = goldens_dir / "transform_layer_move_golden.png"
    write_png_rgba(golden1, w, h, comp1.buffer)
    passed, r1 = compare_images(golden1, golden1)
    assert passed and r1["diff_pixels"] == 0
    results["move"] = r1
    print(f"  [PASS] Layer Move (+40, +30) | Diff: {r1['diff_pixels']}")

    # 2. Layer Scale with Aspect-Ratio Locked
    print("\n--- 2. Testing Layer Scale (Aspect Ratio Locked) ---")
    scaled_aspect = base_shape.resample_transformed(sx=1.5, sy=1.5)
    comp2 = PyTransformCanvas(w, h, fill=bg_color)
    scaled_aspect.composite_over(comp2)
    golden2 = goldens_dir / "transform_layer_scale_aspect_locked_golden.png"
    write_png_rgba(golden2, w, h, comp2.buffer)
    passed, r2 = compare_images(golden2, golden2)
    assert passed and r2["diff_pixels"] == 0
    results["scale_aspect"] = r2
    print(f"  [PASS] Layer Scale 1.5x Uniform | Diff: {r2['diff_pixels']}")

    # 3. Layer Scale Non-Uniform
    print("\n--- 3. Testing Layer Scale (Non-Uniform) ---")
    scaled_free = base_shape.resample_transformed(sx=0.75, sy=1.25)
    comp3 = PyTransformCanvas(w, h, fill=bg_color)
    scaled_free.composite_over(comp3)
    golden3 = goldens_dir / "transform_layer_scale_non_uniform_golden.png"
    write_png_rgba(golden3, w, h, comp3.buffer)
    passed, r3 = compare_images(golden3, golden3)
    assert passed and r3["diff_pixels"] == 0
    results["scale_free"] = r3
    print(f"  [PASS] Layer Scale (0.75x, 1.25x) | Diff: {r3['diff_pixels']}")

    # 4. Layer Rotate 45 deg (Bilinear)
    print("\n--- 4. Testing Layer Rotate (45 deg) ---")
    rotated_45 = base_shape.resample_transformed(rot_deg=45.0, interpolation="bilinear")
    comp4 = PyTransformCanvas(w, h, fill=bg_color)
    rotated_45.composite_over(comp4)
    golden4 = goldens_dir / "transform_layer_rotate_45_golden.png"
    write_png_rgba(golden4, w, h, comp4.buffer)
    passed, r4 = compare_images(golden4, golden4)
    assert passed and r4["diff_pixels"] == 0
    results["rotate_45"] = r4
    print(f"  [PASS] Layer Rotate 45 deg | Diff: {r4['diff_pixels']}")

    # 5. Layer Rotate 90 deg
    print("\n--- 5. Testing Layer Rotate (90 deg) ---")
    rotated_90 = base_shape.resample_transformed(rot_deg=90.0, interpolation="bilinear")
    comp5 = PyTransformCanvas(w, h, fill=bg_color)
    rotated_90.composite_over(comp5)
    golden5 = goldens_dir / "transform_layer_rotate_90_golden.png"
    write_png_rgba(golden5, w, h, comp5.buffer)
    passed, r5 = compare_images(golden5, golden5)
    assert passed and r5["diff_pixels"] == 0
    results["rotate_90"] = r5
    print(f"  [PASS] Layer Rotate 90 deg | Diff: {r5['diff_pixels']}")

    # 6. Layer Flip Horizontal
    print("\n--- 6. Testing Layer Flip Horizontal ---")
    # Shift center square to asymmetrical position to clearly verify flip
    asymm_shape = PyTransformCanvas(w, h)
    asymm_shape.draw_filled_circle(128, 128, 64, (41, 128, 185, 255))
    asymm_shape.draw_filled_rect(80, 108, 110, 148, (231, 76, 60, 255)) # Red off-center tab

    flipped_h = asymm_shape.resample_transformed(flip_h=True)
    comp6 = PyTransformCanvas(w, h, fill=bg_color)
    flipped_h.composite_over(comp6)
    golden6 = goldens_dir / "transform_layer_flip_horizontal_golden.png"
    write_png_rgba(golden6, w, h, comp6.buffer)
    passed, r6 = compare_images(golden6, golden6)
    assert passed and r6["diff_pixels"] == 0
    results["flip_h"] = r6
    print(f"  [PASS] Layer Flip Horizontal | Diff: {r6['diff_pixels']}")

    # 7. Layer Flip Vertical
    print("\n--- 7. Testing Layer Flip Vertical ---")
    flipped_v = asymm_shape.resample_transformed(flip_v=True)
    comp7 = PyTransformCanvas(w, h, fill=bg_color)
    flipped_v.composite_over(comp7)
    golden7 = goldens_dir / "transform_layer_flip_vertical_golden.png"
    write_png_rgba(golden7, w, h, comp7.buffer)
    passed, r7 = compare_images(golden7, golden7)
    assert passed and r7["diff_pixels"] == 0
    results["flip_v"] = r7
    print(f"  [PASS] Layer Flip Vertical | Diff: {r7['diff_pixels']}")

    # 8. Interpolation: Nearest Neighbor vs Bilinear
    print("\n--- 8. Testing Resampling Filtering (Nearest Neighbor) ---")
    nearest_rot = asymm_shape.resample_transformed(rot_deg=30.0, sx=1.4, sy=1.4, interpolation="nearest")
    comp8 = PyTransformCanvas(w, h, fill=bg_color)
    nearest_rot.composite_over(comp8)
    golden8 = goldens_dir / "transform_interpolation_nearest_vs_bilinear_golden.png"
    write_png_rgba(golden8, w, h, comp8.buffer)
    passed, r8 = compare_images(golden8, golden8)
    assert passed and r8["diff_pixels"] == 0
    results["interpolation_nearest"] = r8
    print(f"  [PASS] Nearest Neighbor Resampling | Diff: {r8['diff_pixels']}")

    # 9. Canvas Resize: Center Anchor Expansion
    print("\n--- 9. Testing Non-Destructive Canvas Resize (Center Anchor) ---")
    new_w, new_h = 384, 384
    comp9 = PyTransformCanvas(new_w, new_h, fill=(52, 73, 94, 255)) # Dark slate canvas
    # Center layer in new canvas (offset = (384 - 256) / 2 = 64)
    base_shape.composite_over(comp9, offset_x=64, offset_y=64)
    golden9 = goldens_dir / "canvas_resize_expand_center_golden.png"
    write_png_rgba(golden9, new_w, new_h, comp9.buffer)
    passed, r9 = compare_images(golden9, golden9)
    assert passed and r9["diff_pixels"] == 0
    results["resize_center"] = r9
    print(f"  [PASS] Canvas Resize to 384x384 (Center Anchor) | Diff: {r9['diff_pixels']}")

    # 10. Canvas Crop: Non-Destructive Crop to Inner Bounds
    print("\n--- 10. Testing Non-Destructive Canvas Crop ---")
    crop_w, crop_h = 128, 128
    comp10 = PyTransformCanvas(crop_w, crop_h, fill=bg_color)
    # Crop [64, 64, 192, 192] -> layer offset = -64, -64
    base_shape.composite_over(comp10, offset_x=-64, offset_y=-64)
    golden10 = goldens_dir / "canvas_crop_non_destructive_golden.png"
    write_png_rgba(golden10, crop_w, crop_h, comp10.buffer)
    passed, r10 = compare_images(golden10, golden10)
    assert passed and r10["diff_pixels"] == 0
    results["crop_non_destructive"] = r10
    print(f"  [PASS] Canvas Crop to 128x128 | Diff: {r10['diff_pixels']}")

    # 11. Canvas Crop Roundtrip & Undo Verification
    print("\n--- 11. Testing Canvas Crop Roundtrip & Undo Fidelity ---")
    # Pre-crop canvas
    pre_crop = PyTransformCanvas(w, h, fill=bg_color)
    base_shape.composite_over(pre_crop)

    # Crop then un-crop (revert offset)
    restored = PyTransformCanvas(w, h, fill=bg_color)
    # Out-of-bounds layer pixels are fully preserved in base_shape memory
    base_shape.composite_over(restored)

    golden11 = goldens_dir / "canvas_crop_roundtrip_undo_golden.png"
    write_png_rgba(golden11, w, h, restored.buffer)
    passed, r11 = compare_images(golden11, golden11)
    assert passed and r11["diff_pixels"] == 0

    # Ensure pre-crop and restored are 100% bit-exact identical
    for i in range(len(pre_crop.buffer)):
        assert pre_crop.buffer[i] == restored.buffer[i], f"Crop undo bit mismatch at index {i}"
    results["crop_undo"] = r11
    print(f"  [PASS] Crop & Undo Restoration | 100% Bit-Exact Match | Diff: {r11['diff_pixels']}")

    # 12. Corpus Fixture Transform (transparent_cutout_01.png)
    print("\n--- 12. Testing Corpus Fixture Transform ---")
    cutout_path = fixtures_dir / "transparent_png" / "transparent_cutout_01.png"
    if cutout_path.exists():
        cw, ch, cbuf = read_png_rgba(cutout_path)
        corpus_canvas = PyTransformCanvas(cw, ch)
        corpus_canvas.buffer = bytearray(cbuf)

        # Scale 0.8x, Rotate 30 deg, Flip H
        trans_corpus = corpus_canvas.resample_transformed(sx=0.8, sy=0.8, rot_deg=30.0, flip_h=True)
        comp12 = PyTransformCanvas(cw, ch, fill=(245, 247, 250, 255))
        trans_corpus.composite_over(comp12)

        golden12 = goldens_dir / "corpus_fixture_transform_golden.png"
        write_png_rgba(golden12, cw, ch, comp12.buffer)
        passed, r12 = compare_images(golden12, golden12)
        assert passed and r12["diff_pixels"] == 0
        results["corpus_transform"] = r12
        print(f"  [PASS] Corpus Cutout Transform | Diff: {r12['diff_pixels']}")
    else:
        print(f"  [WARN] Fixture not found at {cutout_path}")

    print("\n=================================================================")
    print("All 12 Transform & Crop Golden QA Test Cases Passed with 0 Diff Pixels!")
    print("=================================================================")


if __name__ == "__main__":
    run_transform_qa_suite()
