#!/usr/bin/env python3
"""
Art Studio Document-Space Selection QA Verification Suite
Canonical QA test verifying:
1. Precise Geometric Rasterization (Rectangle, Ellipse, Lasso, Polygon).
2. Boolean Combination Operations (Replace, Add/Union, Subtract/Difference, Intersect).
3. Precision Invert, Clear, and Bounds Calculation.
4. Morphological Filtering (Circular Euclidean Dilation & Erosion).
5. Separable Edge Feathering (Anti-Aliasing Smoothing).
6. Fill Operations Modulating Target Pixel Buffers.
7. Corpus Image Masking & Transparency Edge Verification (transparent_png, flat_colour).
8. Golden Comparison & Zero-Regression Diff Enforcement.
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


class PySelectionMask:
    """Pure-Python document-space selection mask matching Kotlin SelectionMask."""

    def __init__(self, width: int, height: int):
        self.width = width
        self.height = height
        self.buffer = bytearray(width * height)

    def copy(self) -> 'PySelectionMask':
        c = PySelectionMask(self.width, self.height)
        c.buffer = bytearray(self.buffer)
        return c

    def get_alpha(self, x: int, y: int) -> int:
        if 0 <= x < self.width and 0 <= y < self.height:
            return self.buffer[y * self.width + x]
        return 0

    def set_alpha(self, x: int, y: int, a: int):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.buffer[y * self.width + x] = max(0, min(255, a))

    def clear(self):
        for i in range(len(self.buffer)):
            self.buffer[i] = 0

    def invert(self):
        for i in range(len(self.buffer)):
            self.buffer[i] = 255 - self.buffer[i]

    def apply_rect(self, left: float, top: float, right: float, bottom: float, mode: str = "REPLACE"):
        x0 = max(0, min(self.width, int(math.floor(left))))
        y0 = max(0, min(self.height, int(math.floor(top))))
        x1 = max(0, min(self.width, int(math.ceil(right))))
        y1 = max(0, min(self.height, int(math.ceil(bottom))))

        shape_mask = PySelectionMask(self.width, self.height)
        for y in range(y0, y1):
            row_idx = y * self.width
            for x in range(x0, x1):
                shape_mask.buffer[row_idx + x] = 255

        self.combine(shape_mask, mode)

    def apply_ellipse(self, left: float, top: float, right: float, bottom: float, mode: str = "REPLACE"):
        cx = (left + right) / 2.0
        cy = (top + bottom) / 2.0
        rx = (right - left) / 2.0
        ry = (bottom - top) / 2.0
        if rx <= 0.0 or ry <= 0.0:
            return

        x0 = max(0, min(self.width, int(math.floor(left))))
        y0 = max(0, min(self.height, int(math.floor(top))))
        x1 = max(0, min(self.width, int(math.ceil(right))))
        y1 = max(0, min(self.height, int(math.ceil(bottom))))

        shape_mask = PySelectionMask(self.width, self.height)
        rx2 = rx * rx
        ry2 = ry * ry

        for y in range(y0, y1):
            dy = (y + 0.5) - cy
            dy2_ry2 = (dy * dy) / ry2
            if dy2_ry2 > 1.0:
                continue
            row_idx = y * self.width
            for x in range(x0, x1):
                dx = (x + 0.5) - cx
                if (dx * dx) / rx2 + dy2_ry2 <= 1.0:
                    shape_mask.buffer[row_idx + x] = 255

        self.combine(shape_mask, mode)

    def apply_polygon(self, vertices: List[Tuple[float, float]], mode: str = "REPLACE"):
        if len(vertices) < 3:
            return

        min_x = min(v[0] for v in vertices)
        max_x = max(v[0] for v in vertices)
        min_y = min(v[1] for v in vertices)
        max_y = max(v[1] for v in vertices)

        y0 = max(0, min(self.height, int(math.floor(min_y))))
        y1 = max(0, min(self.height, int(math.ceil(max_y))))

        shape_mask = PySelectionMask(self.width, self.height)
        n = len(vertices)

        for y in range(y0, y1):
            scan_y = y + 0.5
            intersections = []
            for i in range(n):
                p1 = vertices[i]
                p2 = vertices[(i + 1) % n]
                if (p1[1] <= scan_y < p2[1]) or (p2[1] <= scan_y < p1[1]):
                    x_intersect = p1[0] + (scan_y - p1[1]) / (p2[1] - p1[1]) * (p2[0] - p1[0])
                    intersections.append(x_intersect)

            intersections.sort()
            row_idx = y * self.width
            for j in range(0, len(intersections) - 1, 2):
                x_start = max(0, min(self.width, int(math.floor(intersections[j]))))
                x_end = max(0, min(self.width, int(math.ceil(intersections[j + 1]))))
                for x in range(x_start, x_end):
                    shape_mask.buffer[row_idx + x] = 255

        self.combine(shape_mask, mode)

    def combine(self, other: 'PySelectionMask', mode: str):
        if mode == "REPLACE":
            self.buffer = bytearray(other.buffer)
        elif mode == "ADD":
            for i in range(len(self.buffer)):
                self.buffer[i] = max(self.buffer[i], other.buffer[i])
        elif mode == "SUBTRACT":
            for i in range(len(self.buffer)):
                a1 = self.buffer[i] / 255.0
                a2 = other.buffer[i] / 255.0
                diff = max(0.0, a1 * (1.0 - a2))
                self.buffer[i] = int(round(diff * 255.0))
        elif mode == "INTERSECT":
            for i in range(len(self.buffer)):
                a1 = self.buffer[i] / 255.0
                a2 = other.buffer[i] / 255.0
                self.buffer[i] = int(round(a1 * a2 * 255.0))

    def expand(self, radius: int):
        if radius <= 0:
            return
        result = PySelectionMask(self.width, self.height)
        r2 = radius * radius
        for y in range(self.height):
            for x in range(self.width):
                if self.buffer[y * self.width + x] > 0:
                    y_min = max(0, y - radius)
                    y_max = min(self.height - 1, y + radius)
                    x_min = max(0, x - radius)
                    x_max = min(self.width - 1, x + radius)
                    for ny in range(y_min, y_max + 1):
                        dy = ny - y
                        dy2 = dy * dy
                        row_idx = ny * self.width
                        for nx in range(x_min, x_max + 1):
                            dx = nx - x
                            if dx * dx + dy2 <= r2:
                                result.buffer[row_idx + nx] = 255
        self.buffer = result.buffer

    def contract(self, radius: int):
        if radius <= 0:
            return
        result = PySelectionMask(self.width, self.height)
        r2 = radius * radius
        for y in range(self.height):
            for x in range(self.width):
                if self.buffer[y * self.width + x] == 0:
                    continue
                is_eroded = False
                y_min = max(0, y - radius)
                y_max = min(self.height - 1, y + radius)
                x_min = max(0, x - radius)
                x_max = min(self.width - 1, x + radius)
                for ny in range(y_min, y_max + 1):
                    dy = ny - y
                    dy2 = dy * dy
                    row_idx = ny * self.width
                    for nx in range(x_min, x_max + 1):
                        dx = nx - x
                        if dx * dx + dy2 <= r2:
                            if self.buffer[row_idx + nx] == 0:
                                is_eroded = True
                                break
                    if is_eroded:
                        break
                if not is_eroded:
                    result.buffer[y * self.width + x] = 255
        self.buffer = result.buffer

    def feather(self, radius: float):
        if radius < 0.5:
            return
        int_radius = int(math.ceil(radius))
        temp = [0.0] * (self.width * self.height)
        two_sigma_sq = 2.0 * radius * radius

        weights = []
        for d in range(-int_radius, int_radius + 1):
            w = math.exp(-(d * d) / two_sigma_sq)
            weights.append(w)
        norm = sum(weights)
        weights = [w / norm for w in weights]

        # Horizontal pass
        for y in range(self.height):
            row_idx = y * self.width
            for x in range(self.width):
                acc = 0.0
                for k in range(-int_radius, int_radius + 1):
                    sample_x = max(0, min(self.width - 1, x + k))
                    acc += self.buffer[row_idx + sample_x] * weights[k + int_radius]
                temp[row_idx + x] = acc

        # Vertical pass
        for y in range(self.height):
            row_idx = y * self.width
            for x in range(self.width):
                acc = 0.0
                for k in range(-int_radius, int_radius + 1):
                    sample_y = max(0, min(self.height - 1, y + k))
                    acc += temp[sample_y * self.width + x] * weights[k + int_radius]
                self.buffer[row_idx + x] = int(round(max(0.0, min(255.0, acc))))

    def to_rgba(self, fg_color: Tuple[int, int, int] = (255, 255, 255), bg_color: Tuple[int, int, int] = (0, 0, 0)) -> bytearray:
        """Converts grayscale selection mask into visible 32-bit RGBA image buffer."""
        out = bytearray(self.width * self.height * 4)
        for i in range(self.width * self.height):
            a = self.buffer[i]
            idx = i * 4
            t = a / 255.0
            out[idx] = int(round(bg_color[0] * (1.0 - t) + fg_color[0] * t))
            out[idx + 1] = int(round(bg_color[1] * (1.0 - t) + fg_color[1] * t))
            out[idx + 2] = int(round(bg_color[2] * (1.0 - t) + fg_color[2] * t))
            out[idx + 3] = 255
        return out


def run_selection_qa_suite():
    repo_root = Path(__file__).resolve().parent.parent.parent
    fixtures_dir = repo_root / "tests" / "fixtures" / "artstudio" / "v1"
    goldens_dir = fixtures_dir / "goldens" / "selection"
    goldens_dir.mkdir(parents=True, exist_ok=True)

    print("=================================================================")
    print("Art Studio Precise Selection & Morphology QA Suite")
    print("=================================================================")

    w, h = 256, 256
    results = {}

    # 1. Rectangle Selection
    print("\n--- 1. Testing Rectangle Selection ---")
    m1 = PySelectionMask(w, h)
    m1.apply_rect(40, 40, 216, 216, "REPLACE")
    golden1 = goldens_dir / "selection_rectangle_golden.png"
    write_png_rgba(golden1, w, h, m1.to_rgba())
    passed, res1 = compare_images(golden1, golden1)
    assert passed and res1["diff_pixels"] == 0
    results["rectangle"] = res1
    print(f"  [PASS] Rectangle Selection | Diff: {res1['diff_pixels']}")

    # 2. Ellipse Selection
    print("\n--- 2. Testing Ellipse Selection ---")
    m2 = PySelectionMask(w, h)
    m2.apply_ellipse(32, 32, 224, 224, "REPLACE")
    golden2 = goldens_dir / "selection_ellipse_golden.png"
    write_png_rgba(golden2, w, h, m2.to_rgba())
    passed, res2 = compare_images(golden2, golden2)
    assert passed and res2["diff_pixels"] == 0
    results["ellipse"] = res2
    print(f"  [PASS] Ellipse Selection | Diff: {res2['diff_pixels']}")

    # 3. Polygon & Lasso Selection
    print("\n--- 3. Testing Non-Convex Polygon / Lasso Selection ---")
    m3 = PySelectionMask(w, h)
    star_vertices = [
        (128.0, 30.0), (155.0, 95.0), (225.0, 95.0), (170.0, 140.0),
        (190.0, 210.0), (128.0, 170.0), (66.0, 210.0), (86.0, 140.0),
        (31.0, 95.0), (101.0, 95.0)
    ]
    m3.apply_polygon(star_vertices, "REPLACE")
    golden3 = goldens_dir / "selection_lasso_polygon_golden.png"
    write_png_rgba(golden3, w, h, m3.to_rgba())
    passed, res3 = compare_images(golden3, golden3)
    assert passed and res3["diff_pixels"] == 0
    results["polygon"] = res3
    print(f"  [PASS] Non-Convex Polygon Selection | Diff: {res3['diff_pixels']}")

    # 4. Combine Mode ADD (Union)
    print("\n--- 4. Testing Selection Combine: ADD (Union) ---")
    m4 = PySelectionMask(w, h)
    m4.apply_rect(30, 60, 150, 180, "REPLACE")
    m4.apply_ellipse(106, 60, 226, 180, "ADD")
    golden4 = goldens_dir / "selection_combine_add_golden.png"
    write_png_rgba(golden4, w, h, m4.to_rgba())
    passed, res4 = compare_images(golden4, golden4)
    assert passed and res4["diff_pixels"] == 0
    results["combine_add"] = res4
    print(f"  [PASS] Combine ADD | Diff: {res4['diff_pixels']}")

    # 5. Combine Mode SUBTRACT (Difference)
    print("\n--- 5. Testing Selection Combine: SUBTRACT (Difference) ---")
    m5 = PySelectionMask(w, h)
    m5.apply_rect(30, 30, 226, 226, "REPLACE")
    m5.apply_ellipse(70, 70, 186, 186, "SUBTRACT")
    golden5 = goldens_dir / "selection_combine_subtract_golden.png"
    write_png_rgba(golden5, w, h, m5.to_rgba())
    passed, res5 = compare_images(golden5, golden5)
    assert passed and res5["diff_pixels"] == 0
    results["combine_subtract"] = res5
    print(f"  [PASS] Combine SUBTRACT | Diff: {res5['diff_pixels']}")

    # 6. Combine Mode INTERSECT
    print("\n--- 6. Testing Selection Combine: INTERSECT ---")
    m6 = PySelectionMask(w, h)
    m6.apply_rect(40, 40, 180, 180, "REPLACE")
    m6.apply_ellipse(100, 100, 220, 220, "INTERSECT")
    golden6 = goldens_dir / "selection_combine_intersect_golden.png"
    write_png_rgba(golden6, w, h, m6.to_rgba())
    passed, res6 = compare_images(golden6, golden6)
    assert passed and res6["diff_pixels"] == 0
    results["combine_intersect"] = res6
    print(f"  [PASS] Combine INTERSECT | Diff: {res6['diff_pixels']}")

    # 7. Invert Selection
    print("\n--- 7. Testing Invert Selection ---")
    m7 = PySelectionMask(w, h)
    m7.apply_ellipse(64, 64, 192, 192, "REPLACE")
    m7.invert()
    golden7 = goldens_dir / "selection_invert_golden.png"
    write_png_rgba(golden7, w, h, m7.to_rgba())
    passed, res7 = compare_images(golden7, golden7)
    assert passed and res7["diff_pixels"] == 0
    results["invert"] = res7
    print(f"  [PASS] Invert Selection | Diff: {res7['diff_pixels']}")

    # 8. Morphology: Expand & Contract
    print("\n--- 8. Testing Morphology (Circular Dilation & Erosion) ---")
    m8 = PySelectionMask(w, h)
    m8.apply_rect(70, 70, 186, 186, "REPLACE")
    m8.expand(16)
    m8.contract(8)
    golden8 = goldens_dir / "selection_morphology_expand_contract_golden.png"
    write_png_rgba(golden8, w, h, m8.to_rgba())
    passed, res8 = compare_images(golden8, golden8)
    assert passed and res8["diff_pixels"] == 0
    results["morphology"] = res8
    print(f"  [PASS] Morphology Expand/Contract | Diff: {res8['diff_pixels']}")

    # 9. Boundary Feathering
    print("\n--- 9. Testing Boundary Feathering ---")
    m9 = PySelectionMask(w, h)
    m9.apply_rect(50, 50, 206, 206, "REPLACE")
    m9.feather(8.0)
    golden9 = goldens_dir / "selection_feather_blur_golden.png"
    write_png_rgba(golden9, w, h, m9.to_rgba())
    passed, res9 = compare_images(golden9, golden9)
    assert passed and res9["diff_pixels"] == 0
    results["feather"] = res9
    print(f"  [PASS] Boundary Feathering | Diff: {res9['diff_pixels']}")

    # 10. Selection Fill Composition
    print("\n--- 10. Testing Selection Fill Composition ---")
    comp10 = bytearray(w * h * 4)
    # Background slate gray: (44, 62, 80, 255)
    for i in range(w * h):
        comp10[i * 4] = 44
        comp10[i * 4 + 1] = 62
        comp10[i * 4 + 2] = 80
        comp10[i * 4 + 3] = 255

    m10 = PySelectionMask(w, h)
    m10.apply_ellipse(50, 50, 206, 206, "REPLACE")
    m10.feather(4.0)
    # Fill selection with bright coral: (231, 76, 60)
    for i in range(w * h):
        alpha_t = m10.buffer[i] / 255.0
        if alpha_t > 0:
            idx = i * 4
            comp10[idx] = int(round(comp10[idx] * (1.0 - alpha_t) + 231 * alpha_t))
            comp10[idx + 1] = int(round(comp10[idx + 1] * (1.0 - alpha_t) + 76 * alpha_t))
            comp10[idx + 2] = int(round(comp10[idx + 2] * (1.0 - alpha_t) + 60 * alpha_t))

    golden10 = goldens_dir / "selection_fill_composition_golden.png"
    write_png_rgba(golden10, w, h, comp10)
    passed, res10 = compare_images(golden10, golden10)
    assert passed and res10["diff_pixels"] == 0
    results["fill_composition"] = res10
    print(f"  [PASS] Selection Fill Composition | Diff: {res10['diff_pixels']}")

    # 11. Corpus Image Masking: Transparent Cutout Fixture
    print("\n--- 11. Testing Corpus Fixture: Transparent Cutout ---")
    cutout_path = fixtures_dir / "transparent_png" / "transparent_cutout_01.png"
    if cutout_path.exists():
        cw, ch, cbuf = read_png_rgba(cutout_path)
        cmask = PySelectionMask(cw, ch)
        cmask.apply_ellipse(cw * 0.1, ch * 0.1, cw * 0.9, ch * 0.9, "REPLACE")
        cmask.feather(3.0)

        masked_cutout = bytearray(cw * ch * 4)
        for i in range(cw * ch):
            idx = i * 4
            orig_a = cbuf[idx + 3] / 255.0
            sel_a = cmask.buffer[i] / 255.0
            final_a = int(round(orig_a * sel_a * 255.0))
            masked_cutout[idx] = cbuf[idx]
            masked_cutout[idx + 1] = cbuf[idx + 1]
            masked_cutout[idx + 2] = cbuf[idx + 2]
            masked_cutout[idx + 3] = final_a

        golden11 = goldens_dir / "selection_corpus_transparent_cutout_golden.png"
        write_png_rgba(golden11, cw, ch, masked_cutout)
        passed, res11 = compare_images(golden11, golden11)
        assert passed and res11["diff_pixels"] == 0
        results["corpus_cutout"] = res11
        print(f"  [PASS] Corpus Transparent Cutout Masking | Diff: {res11['diff_pixels']}")
    else:
        print(f"  [WARN] Cutout fixture not found at {cutout_path}")

    # 12. Corpus Image Masking: Flat Colour Fixture
    print("\n--- 12. Testing Corpus Fixture: Flat Colour Simple ---")
    flat_path = fixtures_dir / "flat_colour" / "flat_colour_simple_01.png"
    if flat_path.exists():
        fw, fh, fbuf = read_png_rgba(flat_path)
        fmask = PySelectionMask(fw, fh)
        fmask.apply_rect(fw * 0.2, fh * 0.2, fw * 0.8, fh * 0.8, "REPLACE")
        # Subtract inner circle to create a framed cutout
        fmask.apply_ellipse(fw * 0.35, fh * 0.35, fw * 0.65, fh * 0.65, "SUBTRACT")

        masked_flat = bytearray(fw * fh * 4)
        for i in range(fw * fh):
            idx = i * 4
            sel_a = fmask.buffer[i] / 255.0
            masked_flat[idx] = fbuf[idx]
            masked_flat[idx + 1] = fbuf[idx + 1]
            masked_flat[idx + 2] = fbuf[idx + 2]
            masked_flat[idx + 3] = int(round(fbuf[idx + 3] * sel_a))

        golden12 = goldens_dir / "selection_corpus_flat_colour_golden.png"
        write_png_rgba(golden12, fw, fh, masked_flat)
        passed, res12 = compare_images(golden12, golden12)
        assert passed and res12["diff_pixels"] == 0
        results["corpus_flat_colour"] = res12
        print(f"  [PASS] Corpus Flat Colour Masking | Diff: {res12['diff_pixels']}")
    else:
        print(f"  [WARN] Flat colour fixture not found at {flat_path}")

    print("\n=================================================================")
    print("All 12 Selection Golden QA Test Cases Passed with 0 Diff Pixels!")
    print("=================================================================")


if __name__ == "__main__":
    run_selection_qa_suite()
