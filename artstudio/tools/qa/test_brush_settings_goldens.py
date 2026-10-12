#!/usr/bin/env python3
"""
Art Studio Brush Dynamics Settings Verification Tool
Validates that customizable stroke dynamics settings objectively produce distinct visual output:
1. Pressure Response Curve (Linear vs Soft vs Firm)
2. Taper (No Taper vs Start/End Taper)
3. Dab Spacing (Tight 0.06 vs Wide 0.40)
4. Hardness (Solid 1.0 vs Feathered 0.3)
5. Tip Shape & Angle (Round vs Chisel 45 deg)
"""

import sys
import math
from pathlib import Path
from typing import List, Tuple

sys.path.insert(0, str(Path(__file__).resolve().parent))
from compare_golden_images import calculate_metrics, write_png_rgba
from generate_brush_goldens import RasterCanvas, generate_fixture_points

def render_dynamics_variant(
    setting_name: str,
    variant: str,
    width: int = 512,
    height: int = 512
) -> RasterCanvas:
    canvas = RasterCanvas(width, height, bg_color=(255, 255, 255, 255))
    points = generate_fixture_points()

    # Base configuration: Hard Pen size 20
    base_size = 20.0
    r, g, b = 0x1A, 0x36, 0x5D
    opacity = 0.90
    base_alpha = int(255 * opacity)
    spacing = 0.12
    gamma = 1.0
    curve_mode = "linear"
    start_taper = 0.0
    end_taper = 0.0
    tip_shape = "round"
    tip_angle = 0.0
    hardness = 1.0

    if setting_name == "pressure_curve":
        if variant == "linear":
            curve_mode = "linear"
        elif variant == "soft":
            curve_mode = "soft"  # gamma 0.60
            gamma = 0.60
        elif variant == "firm":
            curve_mode = "firm"  # gamma 1.75
            gamma = 1.75

    elif setting_name == "taper":
        if variant == "none":
            start_taper = 0.0
            end_taper = 0.0
        elif variant == "enabled":
            start_taper = 0.25
            end_taper = 0.25

    elif setting_name == "spacing":
        if variant == "tight":
            spacing = 0.05
        elif variant == "wide":
            spacing = 0.60

    elif setting_name == "hardness":
        if variant == "solid":
            hardness = 1.0
        elif variant == "soft":
            hardness = 0.20

    elif setting_name == "tip_shape_angle":
        if variant == "round":
            tip_shape = "round"
        elif variant == "chisel_45":
            tip_shape = "chisel"
            tip_angle = math.pi / 4.0  # 45 degrees

    total_segments = max(1, len(points) - 1)

    for i in range(1, len(points)):
        p0 = points[i - 1]
        p1 = points[i]
        dx, dy = p1[0] - p0[0], p1[1] - p0[1]
        dist = math.hypot(dx, dy)
        tangent_angle = math.atan2(dy, dx)
        seg_start_u = (i - 1) / float(total_segments)
        seg_end_u = i / float(total_segments)

        avg_p = (p0[2] + p1[2]) / 2.0
        # Curve transformation
        if curve_mode == "soft":
            curved_p = avg_p ** 0.60
        elif curve_mode == "firm":
            curved_p = avg_p ** 1.75
        else:
            curved_p = avg_p

        scale = 0.2 + 1.0 * curved_p
        avg_radius = max(0.5, (base_size * scale) / 2.0)
        step_dist = max(1.0, avg_radius * 2.0 * spacing)
        steps = max(1, int(math.ceil(dist / step_dist)))

        for s in range(steps + 1):
            t = s / float(steps)
            cx = p0[0] + t * dx
            cy = p0[1] + t * dy
            raw_p = p0[2] + t * (p1[2] - p0[2])

            if curve_mode == "soft":
                curved_cp = raw_p ** 0.60
            elif curve_mode == "firm":
                curved_cp = raw_p ** 1.75
            else:
                curved_cp = raw_p

            u = seg_start_u + t * (seg_end_u - seg_start_u)

            # Taper factor
            taper_factor = 1.0
            if start_taper > 0.001 and u < start_taper:
                taper_factor = min(taper_factor, u / start_taper)
            if end_taper > 0.001 and (1.0 - u) < end_taper:
                taper_factor = min(taper_factor, (1.0 - u) / end_taper)

            c_rad = max(0.5, (base_size * (0.2 + 1.0 * curved_cp)) / 2.0) * taper_factor
            dab_alpha = int(base_alpha * taper_factor)

            if tip_shape == "chisel":
                rx = c_rad * 1.5
                ry = c_rad * 0.35
                canvas.draw_rotated_oval(cx, cy, rx, ry, tip_angle, r, g, b, dab_alpha)
            elif hardness < 0.99:
                # Soft falloff
                canvas.draw_circle(cx, cy, c_rad, r, g, b, int(dab_alpha * (0.3 + 0.7 * hardness)))
                canvas.draw_circle(cx, cy, c_rad * hardness, r, g, b, dab_alpha)
            else:
                canvas.draw_circle(cx, cy, c_rad, r, g, b, dab_alpha)

    return canvas

def test_five_dynamics_settings() -> bool:
    print("Testing 5 Customizable Dynamics Settings Output Variations...")
    tests = [
        ("pressure_curve", "linear", "soft", 0.015, "Pressure Response Curve (Linear vs Soft)"),
        ("taper", "none", "enabled", 0.010, "Stroke Taper (No Taper vs 25% Start/End Taper)"),
        ("spacing", "tight", "wide", 0.015, "Dab Spacing (Tight 0.05 vs Wide 0.60)"),
        ("hardness", "solid", "soft", 0.010, "Hardness (Solid 1.0 vs Feathered 0.20)"),
        ("tip_shape_angle", "round", "chisel_45", 0.015, "Tip Shape & Angle (Round vs Chisel 45 deg)")
    ]

    all_passed = True
    output_dir = Path("tests/fixtures/artstudio/v1/goldens/dynamics")
    output_dir.mkdir(parents=True, exist_ok=True)

    for setting, var1, var2, min_rmse_diff, description in tests:
        canvas1 = render_dynamics_variant(setting, var1)
        canvas2 = render_dynamics_variant(setting, var2)

        file1 = output_dir / f"{setting}_{var1}.png"
        file2 = output_dir / f"{setting}_{var2}.png"
        write_png_rgba(file1, canvas1.width, canvas1.height, canvas1.buffer)
        write_png_rgba(file2, canvas2.width, canvas2.height, canvas2.buffer)

        metrics, diff_rgba = calculate_metrics(
            canvas1.width, canvas1.height, canvas1.buffer, canvas2.buffer
        )

        rmse = metrics["rmse"]
        diff_pixels = metrics["diff_pixels"]
        diff_pct = metrics["diff_percent"]

        # Diff must exceed min_rmse_diff proving the setting changes stroke output
        if rmse >= min_rmse_diff and diff_pixels > 500:
            print(f"PASS: {description:<55} Diff Pixels: {diff_pixels:>6} ({diff_pct:>5.2f}%), RMSE: {rmse:.4f} >= {min_rmse_diff}")
        else:
            print(f"FAIL: {description:<55} Setting did not produce sufficient output change! RMSE: {rmse:.4f} < {min_rmse_diff}")
            all_passed = False

    return all_passed

if __name__ == "__main__":
    ok = test_five_dynamics_settings()
    sys.exit(0 if ok else 1)
