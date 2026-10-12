#!/usr/bin/env python3
"""
Art Studio 10-Layer Fixture & Golden Generator
Generates canonical 10-layer test fixture and bit-exact golden render:
1. Layer 0: "Background" (Solid cream base #FDFBF7, opacity 1.0, visible true)
2. Layer 1: "Underdrawing Blue" (Soft blue #4A90E2 guideline arcs, opacity 0.5, visible true)
3. Layer 2: "Rough Sketch Warm" (Warm brown #8B572A gesture lines, opacity 0.7, visible true)
4. Layer 3: "Hidden Draft Grid" (Dark charcoal #333333 crosshatch, opacity 0.85, visible false - MUST BE CULLED)
5. Layer 4: "Flat Color Teal" (Teal #50E3C2 banner stripe, opacity 1.0, visible true)
6. Layer 5: "Flat Color Orange" (Orange #F5A623 disc badge, opacity 0.85, visible true)
7. Layer 6: "Shadow Wash" (Deep indigo #1F2937 lower gradient wash, opacity 0.45, visible true)
8. Layer 7: "Inks Contours" (Razor-sharp black #111827 contours, opacity 1.0, isLocked true, visible true)
9. Layer 8: "Highlight Glow" (Lemon yellow #FEF08A soft aura, opacity 0.65, visible true)
10. Layer 9: "Vignette Accent" (Crimson #DC2626 corner markers, opacity 0.75, visible true)

Outputs:
- tests/fixtures/artstudio/v1/goldens/layers/ten_layer_fixture_golden.png
- tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture_project.json
- tests/fixtures/artstudio/v1/sample_projects/ten_layer_fixture.ffart
"""

import os
import sys
import math
import struct
import zlib
import json
import zipfile
from pathlib import Path
from typing import List, Tuple, Callable

sys.path.insert(0, str(Path(__file__).resolve().parent))
try:
    from compare_golden_images import write_png_rgba
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


class LayerCanvas:
    def __init__(self, width: int, height: int, clear: bool = True):
        self.width = width
        self.height = height
        self.buffer = bytearray(width * height * 4)
        if not clear:
            for i in range(width * height):
                idx = i * 4
                self.buffer[idx] = 255
                self.buffer[idx + 1] = 255
                self.buffer[idx + 2] = 255
                self.buffer[idx + 3] = 255

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

    def fill_rect(self, x: int, y: int, w: int, h: int, r: int, g: int, b: int, a: int = 255):
        min_x = max(0, x)
        max_x = min(self.width, x + w)
        min_y = max(0, y)
        max_y = min(self.height, y + h)
        for py in range(min_y, max_y):
            for px in range(min_x, max_x):
                self.blend_pixel(px, py, r, g, b, a)

    def draw_circle(self, cx: float, cy: float, radius: float, r: int, g: int, b: int, a: int = 255):
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
                    dist = math.sqrt(dist_sq)
                    edge = radius - dist
                    alpha = a if edge >= 1.0 else int(a * max(0.0, edge))
                    self.blend_pixel(px, py, r, g, b, alpha)

    def draw_stroke_line(self, x0: float, y0: float, x1: float, y1: float, radius: float, r: int, g: int, b: int, a: int = 255):
        dist = math.hypot(x1 - x0, y1 - y0)
        steps = max(1, int(math.ceil(dist / max(1.0, radius * 0.5))))
        for s in range(steps + 1):
            t = s / float(steps)
            cx = x0 + t * (x1 - x0)
            cy = y0 + t * (y1 - y0)
            self.draw_circle(cx, cy, radius, r, g, b, a)


class LayerDef:
    def __init__(self, id_str: str, name: str, opacity: float, visible: bool, is_locked: bool, render_fn: Callable[[LayerCanvas], None]):
        self.id = id_str
        self.name = name
        self.opacity = opacity
        self.visible = visible
        self.is_locked = is_locked
        self.render_fn = render_fn


def build_ten_layers() -> List[LayerDef]:
    layers = []

    # Layer 0: Background (Cream solid base)
    def render_l0(cv: LayerCanvas):
        cv.fill_rect(0, 0, cv.width, cv.height, 253, 251, 247, 255)
    layers.append(LayerDef("layer-fixture-00", "Background", 1.0, True, False, render_l0))

    # Layer 1: Underdrawing Blue (Soft sweeping arcs)
    def render_l1(cv: LayerCanvas):
        for deg in range(30, 150, 15):
            rad = math.radians(deg)
            x1 = 256 + 180 * math.cos(rad)
            y1 = 256 + 180 * math.sin(rad)
            cv.draw_stroke_line(256, 100, x1, y1, 3.5, 74, 144, 226, 220)
    layers.append(LayerDef("layer-fixture-01", "Underdrawing Blue", 0.50, True, False, render_l1))

    # Layer 2: Rough Sketch Warm (Warm brown gesture diagonals)
    def render_l2(cv: LayerCanvas):
        cv.draw_stroke_line(60, 120, 452, 400, 4.0, 139, 87, 42, 240)
        cv.draw_stroke_line(452, 120, 60, 400, 4.0, 139, 87, 42, 240)
        cv.draw_circle(256, 256, 120, 139, 87, 42, 120)
    layers.append(LayerDef("layer-fixture-02", "Rough Sketch Warm", 0.70, True, False, render_l2))

    # Layer 3: Hidden Draft Grid (MUST BE CULLED - invisible in output)
    def render_l3(cv: LayerCanvas):
        # Dense dark grid that would corrupt the image if rendered
        for pos in range(20, 500, 20):
            cv.draw_stroke_line(pos, 0, pos, 512, 6.0, 30, 30, 30, 255)
            cv.draw_stroke_line(0, pos, 512, pos, 6.0, 30, 30, 30, 255)
    layers.append(LayerDef("layer-fixture-03", "Hidden Draft Grid", 0.85, False, False, render_l3))

    # Layer 4: Flat Color Teal (Horizontal rectangular shape block)
    def render_l4(cv: LayerCanvas):
        cv.fill_rect(96, 180, 320, 140, 80, 227, 194, 255)
    layers.append(LayerDef("layer-fixture-04", "Flat Color Teal", 1.0, True, False, render_l4))

    # Layer 5: Flat Color Orange (Centered badge disk)
    def render_l5(cv: LayerCanvas):
        cv.draw_circle(256, 250, 75, 245, 166, 35, 255)
    layers.append(LayerDef("layer-fixture-05", "Flat Color Orange", 0.85, True, False, render_l5))

    # Layer 6: Shadow Wash (Soft semi-transparent lower gradient)
    def render_l6(cv: LayerCanvas):
        for y in range(300, 480):
            progress = (y - 300) / 180.0
            alpha = int(220 * progress)
            cv.fill_rect(60, y, 392, 1, 31, 41, 55, alpha)
    layers.append(LayerDef("layer-fixture-06", "Shadow Wash", 0.45, True, False, render_l6))

    # Layer 7: Inks Contours (Crisp black outlines, locked)
    def render_l7(cv: LayerCanvas):
        # Outer border
        cv.draw_stroke_line(96, 180, 416, 180, 3.0, 17, 24, 39, 255)
        cv.draw_stroke_line(416, 180, 416, 320, 3.0, 17, 24, 39, 255)
        cv.draw_stroke_line(416, 320, 96, 320, 3.0, 17, 24, 39, 255)
        cv.draw_stroke_line(96, 320, 96, 180, 3.0, 17, 24, 39, 255)
        # Disk outline
        for deg in range(0, 360, 5):
            rad1 = math.radians(deg)
            rad2 = math.radians(deg + 5)
            x1 = 256 + 75 * math.cos(rad1)
            y1 = 250 + 75 * math.sin(rad1)
            x2 = 256 + 75 * math.cos(rad2)
            y2 = 250 + 75 * math.sin(rad2)
            cv.draw_stroke_line(x1, y1, x2, y2, 2.5, 17, 24, 39, 255)
    layers.append(LayerDef("layer-fixture-07", "Inks Contours", 1.0, True, True, render_l7))

    # Layer 8: Highlight Glow (Bright warm central highlight)
    def render_l8(cv: LayerCanvas):
        cv.draw_circle(256, 240, 35, 254, 240, 138, 240)
        cv.draw_circle(256, 240, 15, 255, 255, 255, 255)
    layers.append(LayerDef("layer-fixture-08", "Highlight Glow", 0.65, True, False, render_l8))

    # Layer 9: Vignette Accent (Crimson corners)
    def render_l9(cv: LayerCanvas):
        size = 32
        cv.fill_rect(20, 20, size, size, 220, 38, 38, 255)
        cv.fill_rect(460, 20, size, size, 220, 38, 38, 255)
        cv.fill_rect(20, 460, size, size, 220, 38, 38, 255)
        cv.fill_rect(460, 460, size, size, 220, 38, 38, 255)
    layers.append(LayerDef("layer-fixture-09", "Vignette Accent", 0.75, True, False, render_l9))

    return layers


def composite_layers(layers: List[LayerDef], width: int = 512, height: int = 512) -> LayerCanvas:
    root = LayerCanvas(width, height, clear=True)

    # Document order: bottom-to-top (index 0 to N-1)
    for layer in layers:
        if not layer.visible or layer.opacity <= 0.0:
            continue

        layer_cv = LayerCanvas(width, height, clear=True)
        layer.render_fn(layer_cv)

        # Composite layer_cv onto root with layer.opacity
        eff_opacity = max(0.0, min(1.0, layer.opacity))
        for y in range(height):
            for x in range(width):
                idx = (y * width + x) * 4
                la = layer_cv.buffer[idx + 3]
                if la > 0:
                    lr = layer_cv.buffer[idx]
                    lg = layer_cv.buffer[idx + 1]
                    lb = layer_cv.buffer[idx + 2]
                    alpha_to_blend = int(la * eff_opacity)
                    root.blend_pixel(x, y, lr, lg, lb, alpha_to_blend)

    return root


def create_project_manifest(layers: List[LayerDef], width: int = 512, height: int = 512) -> dict:
    children = []
    for l in layers:
        children.append({
            "type": "raster",
            "id": l.id,
            "name": l.name,
            "visible": l.visible,
            "opacity": l.opacity,
            "blendMode": "normal",
            "transform": {
                "translationX": 0.0,
                "translationY": 0.0,
                "scaleX": 1.0,
                "scaleY": 1.0,
                "rotationDegrees": 0.0
            },
            "isLocked": l.is_locked,
            "isClippingMask": False,
            "assetPath": None,
            "width": width,
            "height": height,
            "offsetX": 0,
            "offsetY": 0
        })

    return {
        "schemaVersion": "2.0.0",
        "appVersion": "1.0.0",
        "id": "doc-fixture-10layers",
        "title": "10-Layer Canonical Golden Fixture",
        "canvas": {
            "width": width,
            "height": height,
            "dpi": 300,
            "backgroundColor": 0xFFFDFBF7,
            "colorSpace": "sRGB"
        },
        "rootGroup": {
            "type": "group",
            "id": "group-fixture-root",
            "name": "Root",
            "visible": True,
            "opacity": 1.0,
            "blendMode": "normal",
            "transform": {
                "translationX": 0.0,
                "translationY": 0.0,
                "scaleX": 1.0,
                "scaleY": 1.0,
                "rotationDegrees": 0.0
            },
            "isLocked": False,
            "isClippingMask": False,
            "isCollapsed": False,
            "children": children
        },
        "selectedLayerId": "layer-fixture-07",
        "palettes": [],
        "referenceImages": [],
        "animation": None,
        "autoDrawProvenance": None,
        "createdAt": 1728500000000,
        "updatedAt": 1728500000000
    }


def main():
    base_dir = Path(__file__).resolve().parent.parent.parent
    goldens_dir = base_dir / "tests" / "fixtures" / "artstudio" / "v1" / "goldens" / "layers"
    goldens_dir.mkdir(parents=True, exist_ok=True)
    sample_dir = base_dir / "tests" / "fixtures" / "artstudio" / "v1" / "sample_projects"
    sample_dir.mkdir(parents=True, exist_ok=True)

    print("=== Generating 10-Layer Fixture & Canonical Golden ===")
    layers = build_ten_layers()
    assert len(layers) == 10, f"Expected 10 layers, got {len(layers)}"

    # 1. Composite & save golden PNG
    composite = composite_layers(layers, 512, 512)
    golden_path = goldens_dir / "ten_layer_fixture_golden.png"
    write_png_rgba(golden_path, 512, 512, composite.buffer)
    print(f"Generated Golden Image: {golden_path} ({len(composite.buffer)} bytes)")

    # 2. Save project.json
    project_doc = create_project_manifest(layers, 512, 512)
    json_path = sample_dir / "ten_layer_fixture_project.json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(project_doc, f, indent=2)
    print(f"Generated Project JSON: {json_path}")

    # 3. Save native .ffart container package
    ffart_path = sample_dir / "ten_layer_fixture.ffart"
    with zipfile.ZipFile(ffart_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        # mimetype entry (STORED)
        mime_info = zipfile.ZipInfo("mimetype")
        mime_info.compress_type = zipfile.ZIP_STORED
        zf.writestr(mime_info, b"application/vnd.artstudio.project")

        # project.json entry
        json_bytes = json.dumps(project_doc, indent=2).encode("utf-8")
        zf.writestr("project.json", json_bytes)

        # thumbnail.png entry
        with open(golden_path, "rb") as gf:
            zf.writestr("thumbnail.png", gf.read())

    print(f"Generated Package Archive: {ffart_path} ({ffart_path.stat().st_size} bytes)")
    print("SUCCESS: 10-layer fixture and golden generated.")


if __name__ == "__main__":
    main()
