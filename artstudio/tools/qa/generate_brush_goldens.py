#!/usr/bin/env python3
"""
Art Studio Brush Goldens Generator and Verification Tool
Generates canonical golden renders for the 7 core raster tools:
1. Pencil (graphite grain, particle clustering with fixed random seed)
2. Hard Pen (razor-sharp solid dabs + connecting line segments)
3. Soft Pen (3-ring feathered radial falloff)
4. Ink Brush (dynamic calligraphic rotated elliptical dabs)
5. Basic Paintbrush (multi-filament bristle dynamics with jitter)
6. Airbrush (4-stage Gaussian radial spray expansion with low flow)
7. Eraser (PorterDuff CLEAR pixel clearing with hard or feathered falloff)

Uses pure-Python raster engine matching RasterStrokeEngine.kt bit-exact arithmetic.
"""

import os
import sys
import math
import struct
import zlib
import argparse
from pathlib import Path
from typing import List, Tuple

# Import standard PNG writer from compare_golden_images
sys.path.insert(0, str(Path(__file__).resolve().parent))
try:
    from compare_golden_images import write_png_rgba, read_png_rgba, calculate_metrics
except ImportError:
    def write_png_rgba(file_path: Path, width: int, height: int, rgba: bytearray) -> None:
        def chunk(tag: bytes, data: bytes) -> bytes:
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
        header = b"\x89PNG\r\n\x1a\n"
        ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        raw = bytearray()
        row_bytes = width * 4
        for y in range(height):
            raw.append(0)
            raw.extend(rgba[y * row_bytes : (y + 1) * row_bytes])
        idat = chunk(b"IDAT", zlib.compress(bytes(raw), level=6))
        iend = chunk(b"IEND", b"")
        file_path.parent.mkdir(parents=True, exist_ok=True)
        with open(file_path, "wb") as f:
            f.write(header + ihdr + idat + iend)

# Raster surface helper
class RasterCanvas:
    def __init__(self, width: int, height: int, bg_color: Tuple[int, int, int, int] = (255, 255, 255, 255)):
        self.width = width
        self.height = height
        self.buffer = bytearray(width * height * 4)
        for i in range(width * height):
            idx = i * 4
            self.buffer[idx] = bg_color[0]
            self.buffer[idx + 1] = bg_color[1]
            self.buffer[idx + 2] = bg_color[2]
            self.buffer[idx + 3] = bg_color[3]

    def blend_pixel(self, x: int, y: int, r: int, g: int, b: int, a: int):
        if x < 0 or x >= self.width or y < 0 or y >= self.height or a <= 0:
            return
        idx = (y * self.width + x) * 4
        src_a = a / 255.0
        dst_a = self.buffer[idx + 3] / 255.0
        out_a = src_a + dst_a * (1.0 - src_a)
        if out_a <= 0:
            return
        dst_r = self.buffer[idx]
        dst_g = self.buffer[idx + 1]
        dst_b = self.buffer[idx + 2]
        out_r = int((r * src_a + dst_r * dst_a * (1.0 - src_a)) / out_a)
        out_g = int((g * src_a + dst_g * dst_a * (1.0 - src_a)) / out_a)
        out_b = int((b * src_a + dst_b * dst_a * (1.0 - src_a)) / out_a)

        self.buffer[idx] = min(255, max(0, out_r))
        self.buffer[idx + 1] = min(255, max(0, out_g))
        self.buffer[idx + 2] = min(255, max(0, out_b))
        self.buffer[idx + 3] = min(255, max(0, int(out_a * 255.0)))

    def clear_pixel(self, x: int, y: int, clear_factor: float = 1.0):
        if x < 0 or x >= self.width or y < 0 or y >= self.height:
            return
        idx = (y * self.width + x) * 4
        cur_a = self.buffer[idx + 3] / 255.0
        new_a = cur_a * (1.0 - min(1.0, max(0.0, clear_factor)))
        self.buffer[idx + 3] = int(new_a * 255.0)
        if new_a <= 0.001:
            self.buffer[idx] = 0
            self.buffer[idx + 1] = 0
            self.buffer[idx + 2] = 0
            self.buffer[idx + 3] = 0

    def draw_circle(self, cx: float, cy: float, radius: float, r: int, g: int, b: int, a: int):
        if radius <= 0 or a <= 0:
            return
        min_x = max(0, int(cx - radius - 1))
        max_x = min(self.width - 1, int(cx + radius + 1))
        min_y = max(0, int(cy - radius - 1))
        max_y = min(self.height - 1, int(cy + radius + 1))
        r_sq = radius * radius

        for py in range(min_y, max_y + 1):
            dy = py + 0.5 - cy
            for px in range(min_x, max_x + 1):
                dx = px + 0.5 - cx
                dist_sq = dx * dx + dy * dy
                if dist_sq <= r_sq:
                    # Antialiasing falloff on edge pixel
                    dist = math.sqrt(dist_sq)
                    edge_delta = radius - dist
                    pixel_a = a
                    if edge_delta < 1.0:
                        pixel_a = int(a * max(0.0, min(1.0, edge_delta)))
                    self.blend_pixel(px, py, r, g, b, pixel_a)

    def draw_rotated_oval(self, cx: float, cy: float, rx: float, ry: float, angle_rad: float, r: int, g: int, b: int, a: int):
        cos_a = math.cos(-angle_rad)
        sin_a = math.sin(-angle_rad)
        max_r = max(rx, ry)
        min_x = max(0, int(cx - max_r - 1))
        max_x = min(self.width - 1, int(cx + max_r + 1))
        min_y = max(0, int(cy - max_r - 1))
        max_y = min(self.height - 1, int(cy + max_r + 1))

        for py in range(min_y, max_y + 1):
            dy = py + 0.5 - cy
            for px in range(min_x, max_x + 1):
                dx = px + 0.5 - cx
                # Rotate back to unrotated ellipse space
                ex = dx * cos_a - dy * sin_a
                ey = dx * sin_a + dy * cos_a
                norm_d = (ex * ex) / (rx * rx) + (ey * ey) / (ry * ry)
                if norm_d <= 1.0:
                    edge = (1.0 - math.sqrt(norm_d)) * min(rx, ry)
                    pixel_a = a
                    if edge < 1.0:
                        pixel_a = int(a * max(0.0, min(1.0, edge)))
                    self.blend_pixel(px, py, r, g, b, pixel_a)

    def draw_line(self, x0: float, y0: float, x1: float, y1: float, radius: float, r: int, g: int, b: int, a: int):
        dx = x1 - x0
        dy = y1 - y0
        dist = math.hypot(dx, dy)
        if dist <= 0.001:
            self.draw_circle(x0, y0, radius, r, g, b, a)
            return
        steps = max(1, int(math.ceil(dist / max(1.0, radius * 0.5))))
        for s in range(steps + 1):
            t = s / steps
            self.draw_circle(x0 + t * dx, y0 + t * dy, radius, r, g, b, a)

    def erase_circle(self, cx: float, cy: float, radius: float, hardness: float = 1.0):
        if radius <= 0:
            return
        min_x = max(0, int(cx - radius - 1))
        max_x = min(self.width - 1, int(cx + radius + 1))
        min_y = max(0, int(cy - radius - 1))
        max_y = min(self.height - 1, int(cy + radius + 1))
        r_sq = radius * radius

        for py in range(min_y, max_y + 1):
            dy = py + 0.5 - cy
            for px in range(min_x, max_x + 1):
                dx = px + 0.5 - cx
                dist_sq = dx * dx + dy * dy
                if dist_sq <= r_sq:
                    if hardness >= 0.8:
                        self.clear_pixel(px, py, 1.0)
                    else:
                        d = math.sqrt(dist_sq) / radius
                        factor = 1.0 - (d * d)
                        self.clear_pixel(px, py, factor)

def generate_fixture_points() -> List[Tuple[float, float, float]]:
    """Generates standard S-curve fixture points (x, y, pressure) across 512x512."""
    points = []
    num_samples = 40
    for i in range(num_samples + 1):
        t = i / float(num_samples)
        x = 75.0 + t * 362.0
        angle = t * 2.0 * math.pi
        y = 256.0 - math.sin(angle) * 115.0
        pressure = 0.25 + 0.65 * math.sin(t * math.pi)
        points.append((x, y, pressure))
    return points

def compute_radius(size: float, min_ratio: float, max_ratio: float, pressure: float) -> float:
    p = max(0.0, min(1.0, pressure))
    scale = min_ratio + (max_ratio - min_ratio) * p
    return max(0.5, (size * scale) / 2.0)

def render_tool_stroke(tool_name: str, width: int = 512, height: int = 512) -> RasterCanvas:
    points = generate_fixture_points()

    if tool_name == "eraser":
        # For eraser, initialize canvas with dark block
        canvas = RasterCanvas(width, height, bg_color=(240, 240, 245, 255))
        # Draw a rich colored block in middle
        for y in range(90, 422):
            for x in range(90, 422):
                canvas.blend_pixel(x, y, 35, 75, 145, 255)
    else:
        canvas = RasterCanvas(width, height, bg_color=(255, 255, 255, 255))

    dab_counter = 0

    if tool_name == "pencil":
        size = 8.0
        opacity = 0.85
        r, g, b = 0x33, 0x33, 0x33
        base_alpha = int(255 * opacity)
        spacing = 0.20
        seed_base = 1337

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.4, 1.1, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.4, 1.1, cp)

                rng = (seed_base ^ (dab_counter * 2654435761)) & 0x7FFFFFFF
                def next_rand():
                    nonlocal rng
                    rng = (rng * 1103515245 + 12345) & 0x7FFFFFFF
                    return (rng & 0xFFFF) / 65535.0

                particle_count = int(8 + (cp * 20.0))
                for _ in range(particle_count):
                    r_norm = math.sqrt(next_rand()) * c_rad
                    theta = next_rand() * (2.0 * math.pi)
                    px = cx + r_norm * math.cos(theta)
                    py = cy + r_norm * math.sin(theta)
                    p_rad = 0.8 + next_rand() * 1.2
                    p_alpha = int(base_alpha * (0.15 + next_rand() * 0.50 * cp))
                    canvas.draw_circle(px, py, p_rad, r, g, b, min(255, max(0, p_alpha)))
                dab_counter += 1

    elif tool_name == "hard_pen":
        size = 6.0
        opacity = 1.0
        r, g, b = 0x00, 0x00, 0x00
        base_alpha = int(255 * opacity)
        spacing = 0.10

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.2, 1.2, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.2, 1.2, cp)
                canvas.draw_circle(cx, cy, c_rad, r, g, b, base_alpha)
                dab_counter += 1

            canvas.draw_line(p0[0], p0[1], p1[0], p1[1], radius, r, g, b, base_alpha)

    elif tool_name == "soft_pen":
        size = 14.0
        opacity = 0.90
        r, g, b = 0x11, 0x11, 0x11
        base_alpha = int(255 * opacity)
        spacing = 0.12

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.3, 1.2, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.3, 1.2, cp)
                # Outer ring
                canvas.draw_circle(cx, cy, c_rad, r, g, b, int(base_alpha * 0.20))
                # Mid ring
                canvas.draw_circle(cx, cy, c_rad * 0.70, r, g, b, int(base_alpha * 0.55))
                # Core
                canvas.draw_circle(cx, cy, c_rad * 0.40, r, g, b, base_alpha)
                dab_counter += 1

    elif tool_name == "ink_brush":
        size = 16.0
        opacity = 1.0
        r, g, b = 0x0A, 0x0A, 0x0A
        base_alpha = int(255 * opacity)
        spacing = 0.08

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            tangent_angle = math.atan2(dy, dx)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.1, 1.5, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.1, 1.5, cp)
                rx = c_rad * 1.35
                ry = c_rad * 0.65
                callig_angle = tangent_angle + (math.pi / 4.0)
                canvas.draw_rotated_oval(cx, cy, rx, ry, callig_angle, r, g, b, base_alpha)
                dab_counter += 1

            canvas.draw_line(p0[0], p0[1], p1[0], p1[1], radius * 0.8, r, g, b, base_alpha)

    elif tool_name == "basic_paintbrush":
        size = 24.0
        opacity = 0.80
        r, g, b = 0x2A, 0x52, 0xBE
        base_alpha = int(255 * opacity)
        spacing = 0.15
        seed_base = 2026

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            tangent_angle = math.atan2(dy, dx)
            normal_x = -math.sin(tangent_angle)
            normal_y = math.cos(tangent_angle)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.5, 1.3, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.5, 1.3, cp)

                rng = (seed_base ^ (dab_counter * 1013904223)) & 0x7FFFFFFF
                def next_rand():
                    nonlocal rng
                    rng = (rng * 1664525 + 1013904223) & 0x7FFFFFFF
                    return (rng & 0xFFFF) / 65535.0

                bristle_count = 10
                for b_idx in range(bristle_count):
                    k = -1.0 + 2.0 * (b_idx / float(bristle_count - 1))
                    jitter = (next_rand() - 0.5) * 0.20 * c_rad
                    offset = (k * c_rad) + jitter
                    bx = cx + normal_x * offset
                    by = cy + normal_y * offset
                    b_rad = max(1.0, c_rad * 0.22)
                    b_alpha = int(base_alpha * (0.45 + 0.45 * cp))
                    canvas.draw_circle(bx, by, b_rad, r, g, b, min(255, max(0, b_alpha)))
                dab_counter += 1

    elif tool_name == "airbrush":
        size = 40.0
        opacity = 0.25
        r, g, b = 0xE0, 0x40, 0x40
        base_alpha = int(255 * opacity)
        flow = 0.20
        flow_alpha = max(1, int(base_alpha * flow * 0.25))
        spacing = 0.10

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.6, 1.2, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.6, 1.2, cp)
                spray_rad = c_rad * 1.4

                canvas.draw_circle(cx, cy, spray_rad, r, g, b, max(1, int(flow_alpha * 0.10)))
                canvas.draw_circle(cx, cy, spray_rad * 0.70, r, g, b, max(1, int(flow_alpha * 0.35)))
                canvas.draw_circle(cx, cy, spray_rad * 0.40, r, g, b, max(1, int(flow_alpha * 0.70)))
                canvas.draw_circle(cx, cy, spray_rad * 0.20, r, g, b, flow_alpha)
                dab_counter += 1

    elif tool_name == "eraser":
        size = 28.0
        hardness = 0.90
        spacing = 0.15

        for i in range(1, len(points)):
            p0 = points[i - 1]
            p1 = points[i]
            dx, dy = p1[0] - p0[0], p1[1] - p0[1]
            dist = math.hypot(dx, dy)
            avg_p = (p0[2] + p1[2]) / 2.0
            radius = compute_radius(size, 0.5, 1.2, avg_p)
            step_dist = max(1.0, radius * 2.0 * spacing)
            steps = max(1, int(math.ceil(dist / step_dist)))

            for s in range(steps + 1):
                t = s / float(steps)
                cx = p0[0] + t * dx
                cy = p0[1] + t * dy
                cp = p0[2] + t * (p1[2] - p0[2])
                c_rad = compute_radius(size, 0.5, 1.2, cp)
                canvas.erase_circle(cx, cy, c_rad, hardness=hardness)
                dab_counter += 1

    return canvas

CORE_TOOLS = [
    "pencil",
    "hard_pen",
    "soft_pen",
    "ink_brush",
    "basic_paintbrush",
    "airbrush",
    "eraser"
]

def generate_all_goldens(output_dir: Path) -> List[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    generated = []
    for tool in CORE_TOOLS:
        canvas = render_tool_stroke(tool)
        file_name = f"brush_{tool}_golden.png"
        out_path = output_dir / file_name
        write_png_rgba(out_path, canvas.width, canvas.height, canvas.buffer)
        generated.append(out_path)
        print(f"Generated golden: {out_path} ({canvas.width}x{canvas.height})")
    return generated

def verify_goldens(goldens_dir: Path) -> bool:
    print(f"Verifying all 7 core tool goldens in {goldens_dir}...")
    all_ok = True
    for tool in CORE_TOOLS:
        golden_file = goldens_dir / f"brush_{tool}_golden.png"
        if not golden_file.exists():
            print(f"FAIL: Missing golden file {golden_file}")
            all_ok = False
            continue

        # Render fresh candidate and compare
        cand_canvas = render_tool_stroke(tool)
        w, h, gold_bytes = read_png_rgba(golden_file)
        if w != cand_canvas.width or h != cand_canvas.height:
            print(f"FAIL: Dimension mismatch for {tool}: {w}x{h} vs {cand_canvas.width}x{cand_canvas.height}")
            all_ok = False
            continue

        metrics, _ = calculate_metrics(w, h, cand_canvas.buffer, gold_bytes)
        rmse = metrics["rmse"]
        psnr = metrics["psnr_db"]
        ssim = metrics["ssim"]

        if rmse <= 0.001 and ssim >= 0.999:
            print(f"PASS: {tool:<18} RMSE={rmse:.5f}, PSNR={psnr:.2f}dB, SSIM={ssim:.4f}")
        else:
            print(f"FAIL: {tool:<18} RMSE={rmse:.5f}, PSNR={psnr:.2f}dB, SSIM={ssim:.4f} exceeds threshold")
            all_ok = False

    return all_ok

def main():
    parser = argparse.ArgumentParser(description="Art Studio Brush Golden Renders Generator")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("tests/fixtures/artstudio/v1/goldens/brushes"),
        help="Target directory for golden images"
    )
    parser.add_argument(
        "--verify",
        action="store_true",
        help="Verify candidate renders against existing goldens"
    )
    args = parser.parse_args()

    if args.verify:
        ok = verify_goldens(args.output_dir)
        sys.exit(0 if ok else 1)
    else:
        generate_all_goldens(args.output_dir)
        ok = verify_goldens(args.output_dir)
        sys.exit(0 if ok else 1)

if __name__ == "__main__":
    main()
