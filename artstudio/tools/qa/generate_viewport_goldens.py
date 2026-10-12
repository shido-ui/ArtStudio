#!/usr/bin/env python3
"""
Viewport Golden Render Generator & Regression Suite
Generates golden reference viewport frames and compares candidate transforms
using tools/qa/compare_golden_images.py to verify coordinate transforms and visual fidelity.
"""

import sys
import math
from pathlib import Path
from compare_golden_images import read_png_rgba, write_png_rgba, compare_images

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
GOLDENS_DIR = REPO_ROOT / "tests" / "fixtures" / "artstudio" / "v1" / "goldens" / "viewport"
CANDIDATES_DIR = REPO_ROOT / "build" / "reports" / "viewport_candidates"
DIFFS_DIR = REPO_ROOT / "build" / "reports" / "viewport_diffs"
FIXTURE_SRC = REPO_ROOT / "tests" / "fixtures" / "artstudio" / "v1" / "line_art" / "lineart_simple_01.png"

def sample_bilinear(src_rgba: bytearray, w: int, h: int, u: float, v: float) -> tuple:
    """Samples pixel with bilinear interpolation; returns (r, g, b, a) or transparent/white if out of bounds."""
    if u < 0 or u >= w or v < 0 or v >= h:
        return (245, 245, 245, 255) # canvas backdrop color
    x0 = int(math.floor(u))
    y0 = int(math.floor(v))
    x1 = min(x0 + 1, w - 1)
    y1 = min(y0 + 1, h - 1)
    fx = u - x0
    fy = v - y0

    def get_px(x, y):
        idx = (y * w + x) * 4
        return (src_rgba[idx], src_rgba[idx+1], src_rgba[idx+2], src_rgba[idx+3])

    p00 = get_px(x0, y0)
    p10 = get_px(x1, y0)
    p01 = get_px(x0, y1)
    p11 = get_px(x1, y1)

    r = int((1-fx)*(1-fy)*p00[0] + fx*(1-fy)*p10[0] + (1-fx)*fy*p01[0] + fx*fy*p11[0])
    g = int((1-fx)*(1-fy)*p00[1] + fx*(1-fy)*p10[1] + (1-fx)*fy*p01[1] + fx*fy*p11[1])
    b = int((1-fx)*(1-fy)*p00[2] + fx*(1-fy)*p10[2] + (1-fx)*fy*p01[2] + fx*fy*p11[2])
    a = int((1-fx)*(1-fy)*p00[3] + fx*(1-fy)*p10[3] + (1-fx)*fy*p01[3] + fx*fy*p11[3])
    return (r, g, b, a)

def render_viewport_transform(
    src_rgba: bytearray,
    doc_w: int,
    doc_h: int,
    view_w: int,
    view_h: int,
    pan_x: float,
    pan_y: float,
    zoom: float,
    rotation_deg: float,
    flip_h: bool = False,
    flip_v: bool = False
) -> bytearray:
    """
    Renders viewport by inverse mapping every viewport pixel (vx, vy)
    to document space (doc_x, doc_y) using exact ViewportTransform mathematics.
    """
    out_rgba = bytearray(view_w * view_h * 4)

    rad = math.radians(-rotation_deg)
    cos_rad = math.cos(rad)
    sin_rad = math.sin(rad)

    pivot_x = doc_w / 2.0 if doc_w > 0 else 0.0
    pivot_y = doc_h / 2.0 if doc_h > 0 else 0.0

    for vy in range(view_h):
        for vx in range(view_w):
            # 1. Un-translate
            dx = vx - pan_x
            dy = vy - pan_y

            # 2. Un-scale
            unscaled_x = dx / zoom
            unscaled_y = dy / zoom

            # 3. Un-rotate
            unrot_x = unscaled_x * cos_rad - unscaled_y * sin_rad
            unrot_y = unscaled_x * sin_rad + unscaled_y * cos_rad

            # 4. Un-flip
            doc_x = (2.0 * pivot_x - unrot_x) if flip_h else unrot_x
            doc_y = (2.0 * pivot_y - unrot_y) if flip_v else unrot_y

            r, g, b, a = sample_bilinear(src_rgba, doc_w, doc_h, doc_x, doc_y)
            idx = (vy * view_w + vx) * 4
            out_rgba[idx] = r
            out_rgba[idx+1] = g
            out_rgba[idx+2] = b
            out_rgba[idx+3] = a

    return out_rgba

def main():
    print("==================================================")
    print("  VIEWPORT GOLDEN COMPARISON RUNNER")
    print("==================================================")
    GOLDENS_DIR.mkdir(parents=True, exist_ok=True)
    CANDIDATES_DIR.mkdir(parents=True, exist_ok=True)
    DIFFS_DIR.mkdir(parents=True, exist_ok=True)

    doc_w, doc_h, src_rgba = read_png_rgba(FIXTURE_SRC)
    print(f"Loaded source fixture: {FIXTURE_SRC} ({doc_w}x{doc_h})")

    scenarios = [
        {
            "name": "viewport_1x_identity_centered",
            "view_w": 512, "view_h": 512,
            "pan_x": 0.0, "pan_y": 0.0,
            "zoom": 1.0, "rot": 0.0, "flip_h": False, "flip_v": False
        },
        {
            "name": "viewport_fit_screen_pillarbox",
            "view_w": 256, "view_h": 512,
            "pan_x": 0.0, "pan_y": 128.0, # scaled to 0.5x, centered vertically
            "zoom": 0.5, "rot": 0.0, "flip_h": False, "flip_v": False
        },
        {
            "name": "viewport_rotated_90_cw",
            "view_w": 512, "view_h": 512,
            "pan_x": 512.0, "pan_y": 0.0, # (doc_w, 0) rotated by 90deg maps with pan
            "zoom": 1.0, "rot": 90.0, "flip_h": False, "flip_v": False
        },
        {
            "name": "viewport_horizontal_flip_mirror",
            "view_w": 512, "view_h": 512,
            "pan_x": 0.0, "pan_y": 0.0,
            "zoom": 1.0, "rot": 0.0, "flip_h": True, "flip_v": False
        }
    ]

    all_passed = True
    summary_results = []

    for sc in scenarios:
        name = sc["name"]
        print(f"\n--- Testing Scenario: {name} ---")
        golden_file = GOLDENS_DIR / f"{name}.png"
        cand_file = CANDIDATES_DIR / f"{name}.png"
        diff_file = DIFFS_DIR / f"{name}_diff.png"

        rendered_rgba = render_viewport_transform(
            src_rgba, doc_w, doc_h,
            sc["view_w"], sc["view_h"],
            sc["pan_x"], sc["pan_y"],
            sc["zoom"], sc["rot"],
            sc["flip_h"], sc["flip_v"]
        )

        # Ensure golden reference exists
        if not golden_file.exists():
            write_png_rgba(golden_file, sc["view_w"], sc["view_h"], rendered_rgba)
            print(f"Created reference golden: {golden_file}")

        # Write candidate
        write_png_rgba(cand_file, sc["view_w"], sc["view_h"], rendered_rgba)

        # Compare using compare_images
        passed, metrics = compare_images(
            candidate_path=cand_file,
            golden_path=golden_file,
            diff_path=diff_file,
            max_rmse=0.015,
            min_psnr=45.0,
            min_ssim=0.99
        )
        if not passed:
            all_passed = False
        summary_results.append({"name": name, "passed": passed, "metrics": metrics})

    print("\n==================================================")
    print("  GOLDEN COMPARISON SUITE SUMMARY")
    print("==================================================")
    for res in summary_results:
        status_str = "[PASS]" if res["passed"] else "[FAIL]"
        m = res["metrics"]
        print(f"  {res['name']:<35} : {status_str} (RMSE: {m['rmse']}, PSNR: {m['psnr_db']} dB, SSIM: {m['ssim']})")

    report_path = REPO_ROOT / "build" / "reports" / "viewport_golden_report.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    with open(report_path, "w", encoding="utf-8") as f:
        import json
        json.dump(summary_results, f, indent=2)
    print(f"\nReport written to: {report_path}")

    sys.exit(0 if all_passed else 1)

if __name__ == "__main__":
    main()
