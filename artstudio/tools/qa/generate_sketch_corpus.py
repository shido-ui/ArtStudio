#!/usr/bin/env python3
"""
Art Studio Sketch Corpus Generator
Generates a comprehensive corpus of 20 realistic photographed and scanned sketches
along with ground-truth reference geometry, metadata, and quality criteria.

Categories:
- 10 Photographed Sketches:
  Features non-uniform lighting (directional gradients, vignette, cast phone/desk shadows),
  paper exposure variations, graphite tooth, and fine crumbs/speckles.
- 10 Scanned Sketches:
  Features flatbed scanner background textures, sensor dust speckles, scanline banding,
  high-contrast graphite/ink lines, thin spurs, figure-8 junctions, and nested holes.

Outputs:
- tests/fixtures/artstudio/v1/sketches/sketch_01_photo_geometric.png (.json)
  ... through sketch_20_scan_city_skyline.png (.json)
- tests/fixtures/artstudio/v1/sketches/corpus_manifest.json
"""

import os
import sys
import math
import zlib
import struct
import json
import hashlib
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SKETCHES_DIR = REPO_ROOT / "tests" / "fixtures" / "artstudio" / "v1" / "sketches"

def make_png_bytes(width: int, height: int, rgba_bytes: bytearray) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    header = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    raw = bytearray()
    row_bytes = width * 4
    for y in range(height):
        raw.append(0) # Filter: None
        raw.extend(rgba_bytes[y * row_bytes : (y + 1) * row_bytes])
    idat = chunk(b"IDAT", zlib.compress(bytes(raw), level=6))
    iend = chunk(b"IEND", b"")
    return header + ihdr + idat + iend

class SketchCanvas:
    def __init__(self, width: int = 512, height: int = 512, seed: int = 42):
        self.width = width
        self.height = height
        self.buf = bytearray([255, 255, 255, 255] * (width * height))
        self.reference_paths = []
        self.seed = seed

    def rand(self) -> float:
        self.seed = (self.seed * 1103515245 + 12345) & 0x7fffffff
        return (self.seed >> 16) / 32768.0

    def set_pixel(self, x: int, y: int, r: int, g: int, b: int, a: int = 255):
        if 0 <= x < self.width and 0 <= y < self.height:
            idx = (y * self.width + x) * 4
            self.buf[idx] = r
            self.buf[idx + 1] = g
            self.buf[idx + 2] = b
            self.buf[idx + 3] = a

    def get_pixel(self, x: int, y: int):
        if 0 <= x < self.width and 0 <= y < self.height:
            idx = (y * self.width + x) * 4
            return self.buf[idx], self.buf[idx + 1], self.buf[idx + 2]
        return 255, 255, 255

    def apply_illumination_gradient(self, start_val: int = 245, end_val: int = 155, angle_deg: float = 45):
        rad = math.radians(angle_deg)
        cos_a = math.cos(rad)
        sin_a = math.sin(rad)
        diag = math.hypot(self.width, self.height)
        for y in range(self.height):
            for x in range(self.width):
                proj = (x * cos_a + y * sin_a) / diag
                proj = max(0.0, min(1.0, proj))
                paper_val = int(start_val + (end_val - start_val) * proj)
                # Add micro-grain
                grain = int((self.rand() - 0.5) * 12)
                v = max(0, min(255, paper_val + grain))
                self.set_pixel(x, y, v, v, v)

    def apply_vignette(self, center_val: int = 240, edge_val: int = 170):
        cx, cy = self.width / 2.0, self.height / 2.0
        max_dist = math.hypot(cx, cy)
        for y in range(self.height):
            for x in range(self.width):
                d = math.hypot(x - cx, y - cy) / max_dist
                paper_val = int(center_val + (edge_val - center_val) * (d ** 1.5))
                grain = int((self.rand() - 0.5) * 10)
                v = max(0, min(255, paper_val + grain))
                self.set_pixel(x, y, v, v, v)

    def apply_cast_shadow(self, x0: int, y0: int, x1: int, y1: int, shadow_intensity: float = 0.55):
        # Dark shadow band cast across canvas (e.g. phone shadow or arm)
        dx = x1 - x0
        dy = y1 - y0
        len_sq = max(1.0, dx * dx + dy * dy)
        for y in range(self.height):
            for x in range(self.width):
                # Distance to line
                t = max(0.0, min(1.0, ((x - x0) * dx + (y - y0) * dy) / len_sq))
                proj_x = x0 + t * dx
                proj_y = y0 + t * dy
                dist = math.hypot(x - proj_x, y - proj_y)
                if dist < 120:
                    falloff = math.exp(-(dist * dist) / (2.0 * 50.0 * 50.0))
                    factor = 1.0 - (shadow_intensity * falloff)
                    r, g, b = self.get_pixel(x, y)
                    self.set_pixel(x, y, int(r * factor), int(g * factor), int(b * factor))

    def apply_scanner_texture(self, base_val: int = 242):
        for y in range(self.height):
            # Subtle scanline banding
            band = math.sin(y * 0.4) * 3.0
            for x in range(self.width):
                grain = (self.rand() - 0.5) * 8.0
                v = max(0, min(255, int(base_val + band + grain)))
                self.set_pixel(x, y, v, v, v)

    def add_speckles(self, count: int = 60, min_size: int = 1, max_size: int = 2):
        for _ in range(count):
            sx = int(self.rand() * self.width)
            sy = int(self.rand() * self.height)
            sz = int(min_size + self.rand() * (max_size - min_size + 1))
            val = int(20 + self.rand() * 40)
            for dy in range(sz):
                for dx in range(sz):
                    self.set_pixel(sx + dx, sy + dy, val, val, val)

    def draw_smooth_polyline(self, pts, stroke_width: int = 3, stroke_color: int = 35, record_ref: bool = True):
        if record_ref and len(pts) >= 2:
            self.reference_paths.append([{"x": float(p[0]), "y": float(p[1])} for p in pts])

        for i in range(len(pts) - 1):
            x0, y0 = int(pts[i][0]), int(pts[i][1])
            x1, y1 = int(pts[i + 1][0]), int(pts[i + 1][1])
            self._draw_bresenham_line(x0, y0, x1, y1, stroke_width, stroke_color)

    def _draw_bresenham_line(self, x0: int, y0: int, x1: int, y1: int, width: int, color: int):
        dx = abs(x1 - x0)
        dy = abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx - dy
        curr_x, curr_y = x0, y0
        rad = width // 2
        while True:
            for ox in range(-rad, rad + 1):
                for oy in range(-rad, rad + 1):
                    if ox * ox + oy * oy <= rad * rad + 1:
                        # Add graphite tooth jitter
                        jitter = int((self.rand() - 0.5) * 15)
                        c = max(0, min(255, color + jitter))
                        self.set_pixel(curr_x + ox, curr_y + oy, c, c, c)
            if curr_x == x1 and curr_y == y1:
                break
            e2 = 2 * err
            if e2 > -dy:
                err -= dy
                curr_x += sx
            if e2 < dx:
                err += dx
                curr_y += sy

    def draw_circle(self, cx: int, cy: int, radius: int, stroke_width: int = 3, stroke_color: int = 35, num_pts: int = 48, record_ref: bool = True):
        pts = []
        for i in range(num_pts + 1):
            theta = 2.0 * math.pi * i / num_pts
            pts.append((cx + radius * math.cos(theta), cy + radius * math.sin(theta)))
        self.draw_smooth_polyline(pts, stroke_width, stroke_color, record_ref)

    def to_png_bytes(self) -> bytes:
        return make_png_bytes(self.width, self.height, self.buf)

def generate_sketch_01_photo_geometric():
    # Sketch 1: Outer Rectangle + Inner Triangle with Top-Left Illumination Gradient
    c = SketchCanvas(512, 512, seed=101)
    c.apply_illumination_gradient(start_val=245, end_val=155, angle_deg=35)
    c.add_speckles(count=40)

    # Outer rectangle: (90, 90) to (420, 420)
    rect = [(90, 90), (420, 90), (420, 420), (90, 420), (90, 90)]
    c.draw_smooth_polyline(rect, stroke_width=2, stroke_color=25)

    # Inner triangle: (256, 150) -> (370, 360) -> (140, 360) -> (256, 150)
    tri = [(256, 150), (370, 360), (140, 360), (256, 150)]
    c.draw_smooth_polyline(tri, stroke_width=2, stroke_color=30)
    return c, "photo", "Geometric rectangle with nested triangle, top-left lighting gradient and paper grain."

def generate_sketch_02_photo_face_profile():
    # Sketch 2: Profile Face with Nose Spur, Chin Curve, Eye Loop, Cast Phone Shadow
    c = SketchCanvas(512, 512, seed=102)
    c.apply_vignette(center_val=238, edge_val=165)
    c.apply_cast_shadow(x0=50, y0=480, x1=460, y1=420, shadow_intensity=0.45)
    c.add_speckles(count=35)

    # Face profile contour: Forehead, nose tip (spur), lips, chin, jawline, neck
    face = [
        (180, 80), (200, 140), (210, 190), (275, 230), (250, 245), # Nose bridge and tip spur
        (255, 270), (240, 285), (258, 305), (248, 335),             # Lips and chin
        (200, 370), (170, 410), (160, 450)                          # Jaw and neck
    ]
    c.draw_smooth_polyline(face, stroke_width=2, stroke_color=30)

    # Eye loop (hole/closed loop)
    eye = [(200, 190), (225, 185), (240, 195), (220, 202), (200, 190)]
    c.draw_smooth_polyline(eye, stroke_width=2, stroke_color=35)

    # Eyebrow curve
    eyebrow = [(190, 175), (220, 168), (245, 172)]
    c.draw_smooth_polyline(eyebrow, stroke_width=2, stroke_color=30)
    return c, "photo", "Portrait face profile with sharp nose spur, eye contour, and diagonal cast shadow."

def generate_sketch_03_photo_botanical_leaf():
    # Sketch 3: Elliptical Leaf Outline with Central Stem and 6 Lateral Vein Spurs
    c = SketchCanvas(512, 512, seed=103)
    c.apply_illumination_gradient(start_val=240, end_val=160, angle_deg=120)
    c.add_speckles(count=45)

    # Leaf perimeter
    leaf = []
    for i in range(37):
        t = i / 36.0 * 2.0 * math.pi
        x = 256 + 130 * math.cos(t)
        y = 256 + 180 * math.sin(t) * (1.0 - 0.25 * math.cos(t))
        leaf.append((x, y))
    c.draw_smooth_polyline(leaf, stroke_width=2, stroke_color=25)

    # Central stem line
    stem = [(256, 80), (256, 170), (256, 260), (256, 350), (256, 440)]
    c.draw_smooth_polyline(stem, stroke_width=2, stroke_color=30)

    # 6 lateral vein spurs (1-pixel fine spurs extending to near edge)
    veins = [
        [(256, 150), (320, 130)],
        [(256, 150), (192, 130)],
        [(256, 230), (345, 205)],
        [(256, 230), (167, 205)],
        [(256, 310), (330, 290)],
        [(256, 310), (182, 290)]
    ]
    for v in veins:
        c.draw_smooth_polyline(v, stroke_width=2, stroke_color=40)
    return c, "photo", "Botanical leaf contour with 6 thin branching vein spurs and directional illumination."

def generate_sketch_04_photo_architecture():
    # Sketch 4: House Facade with Roof Spire Spur, Door, 2 Window Holes, Corner Vignette
    c = SketchCanvas(512, 512, seed=104)
    c.apply_vignette(center_val=242, edge_val=158)
    c.add_speckles(count=30)

    # Building base and triangular roof with spire
    building = [
        (120, 420), (120, 240), (256, 120), (256, 70), (256, 120), # Left wall and roof with spire spur
        (392, 240), (392, 420), (120, 420)                          # Right wall and ground line
    ]
    c.draw_smooth_polyline(building, stroke_width=2, stroke_color=25)

    # Window 1 (left hole)
    w1 = [(160, 270), (210, 270), (210, 320), (160, 320), (160, 270)]
    c.draw_smooth_polyline(w1, stroke_width=2, stroke_color=30)

    # Window 2 (right hole)
    w2 = [(302, 270), (352, 270), (352, 320), (302, 320), (302, 270)]
    c.draw_smooth_polyline(w2, stroke_width=2, stroke_color=30)

    # Door
    door = [(230, 420), (230, 330), (282, 330), (282, 420)]
    c.draw_smooth_polyline(door, stroke_width=2, stroke_color=30)
    return c, "photo", "Architectural building facade with roof spire, window holes, and corner vignette."

def generate_sketch_05_photo_donut_gears():
    # Sketch 5: Concentric Gear with 8 Teeth (Spurs) and Central Hole under Spotlight
    c = SketchCanvas(512, 512, seed=105)
    c.apply_illumination_gradient(start_val=248, end_val=165, angle_deg=90)
    c.add_speckles(count=50)

    # Gear outer perimeter with 8 teeth
    teeth = []
    num_teeth = 8
    for i in range(num_teeth * 4 + 1):
        step = i % 4
        theta = 2.0 * math.pi * i / (num_teeth * 4)
        r = 160 if (step in [1, 2]) else 130
        teeth.append((256 + r * math.cos(theta), 256 + r * math.sin(theta)))
    c.draw_smooth_polyline(teeth, stroke_width=2, stroke_color=25)

    # Inner circular hole (donut hole)
    c.draw_circle(256, 256, radius=55, stroke_width=2, stroke_color=30)
    return c, "photo", "Concentric gear ring with 8 tooth spurs and internal hole under vertical lighting."

def generate_sketch_06_photo_figure8_ribbon():
    # Sketch 6: Figure-8 Ribbon Knot Touching at Center with Desk Shadow
    c = SketchCanvas(512, 512, seed=106)
    c.apply_vignette(center_val=235, edge_val=170)
    c.apply_cast_shadow(x0=100, y0=450, x1=400, y1=380, shadow_intensity=0.50)
    c.add_speckles(count=35)

    # Figure 8 parametric curve (Lemniscate of Gerono)
    fig8 = []
    for i in range(61):
        t = i / 60.0 * 2.0 * math.pi
        x = 256 + 150 * math.sin(t)
        y = 256 + 150 * math.sin(t) * math.cos(t) * 1.5
        fig8.append((x, y))
    c.draw_smooth_polyline(fig8, stroke_width=2, stroke_color=28)
    return c, "photo", "Figure-8 ribbon junction touching at crossing point under soft desk shadow."

def generate_sketch_07_photo_coffee_mug():
    # Sketch 7: Mug Silhouette with Closed Handle Hole and Steam Spurs
    c = SketchCanvas(512, 512, seed=107)
    c.apply_illumination_gradient(start_val=242, end_val=160, angle_deg=220)
    c.add_speckles(count=40)

    # Mug Body
    mug = [
        (160, 220), (320, 220), (310, 400), (170, 400), (160, 220)
    ]
    c.draw_smooth_polyline(mug, stroke_width=2, stroke_color=25)

    # Handle hole (outer loop and inner hole)
    handle_outer = [(320, 245), (380, 260), (385, 340), (315, 365)]
    c.draw_smooth_polyline(handle_outer, stroke_width=2, stroke_color=30)
    handle_inner = [(320, 270), (355, 280), (355, 325), (315, 340)]
    c.draw_smooth_polyline(handle_inner, stroke_width=2, stroke_color=35)

    # Steam curves (spurs)
    steam1 = [(210, 200), (225, 160), (215, 120), (230, 85)]
    steam2 = [(270, 200), (255, 160), (270, 120), (255, 85)]
    c.draw_smooth_polyline(steam1, stroke_width=2, stroke_color=40)
    c.draw_smooth_polyline(steam2, stroke_width=2, stroke_color=40)
    return c, "photo", "Coffee mug with handle loop hole, elliptical rim, and steam curve spurs."

def generate_sketch_08_photo_tree_branches():
    # Sketch 8: Tree Trunk with 6 Fine 1-Pixel Branch Spurs and Side Shadow
    c = SketchCanvas(512, 512, seed=108)
    c.apply_illumination_gradient(start_val=245, end_val=162, angle_deg=180)
    c.add_speckles(count=45)

    # Trunk outline
    trunk_left = [(235, 450), (240, 320), (220, 250)]
    trunk_right = [(277, 450), (272, 320), (292, 250)]
    c.draw_smooth_polyline(trunk_left, stroke_width=2, stroke_color=25)
    c.draw_smooth_polyline(trunk_right, stroke_width=2, stroke_color=25)

    # Branches (thin 1-2 px spurs)
    branches = [
        [(220, 250), (160, 180), (110, 140)],
        [(160, 180), (140, 210)],
        [(220, 250), (240, 170), (220, 110)],
        [(292, 250), (350, 180), (410, 130)],
        [(350, 180), (380, 210)],
        [(292, 250), (270, 170), (290, 100)]
    ]
    for b in branches:
        c.draw_smooth_polyline(b, stroke_width=2, stroke_color=30)
    return c, "photo", "Tree silhouette branching into thin 1-pixel spurs under lateral gradient lighting."

def generate_sketch_09_photo_cat_silhouette():
    # Sketch 9: Cat Silhouette with Ear Spurs, Whisker Spurs, Curved Tail
    c = SketchCanvas(512, 512, seed=109)
    c.apply_vignette(center_val=240, edge_val=168)
    c.add_speckles(count=35)

    # Body and head with ears
    cat = [
        (256, 430), (200, 410), (190, 320), (210, 250),           # Body back
        (195, 200), (195, 140), (220, 180),                       # Left ear spur
        (256, 190), (292, 140), (292, 200),                       # Right ear spur
        (310, 250), (320, 340), (310, 420), (256, 430)            # Chest and base
    ]
    c.draw_smooth_polyline(cat, stroke_width=2, stroke_color=25)

    # Whisker spurs
    whiskers = [
        [(215, 225), (145, 215)],
        [(215, 235), (140, 238)],
        [(275, 225), (345, 215)],
        [(275, 235), (350, 238)]
    ]
    for w in whiskers:
        c.draw_smooth_polyline(w, stroke_width=2, stroke_color=35)

    # Tail curve
    tail = [(310, 420), (380, 400), (410, 340), (395, 300)]
    c.draw_smooth_polyline(tail, stroke_width=2, stroke_color=30)
    return c, "photo", "Cat silhouette with sharp pointed ear spurs, whisker spurs, and tail curve."

def generate_sketch_10_photo_flower_blossom():
    # Sketch 10: Flower Blossom with Central Disk Hole, 6 Petals, Stem Spur
    c = SketchCanvas(512, 512, seed=110)
    c.apply_illumination_gradient(start_val=244, end_val=168, angle_deg=60)
    c.add_speckles(count=40)

    # Central disk (hole)
    c.draw_circle(256, 210, radius=35, stroke_width=2, stroke_color=25)

    # 6 petals around center
    for i in range(6):
        deg = i * 60
        rad = math.radians(deg)
        px = 256 + 75 * math.cos(rad)
        py = 210 + 75 * math.sin(rad)
        c.draw_circle(int(px), int(py), radius=35, stroke_width=2, stroke_color=30)

    # Stem spur
    stem = [(256, 280), (256, 360), (245, 450)]
    c.draw_smooth_polyline(stem, stroke_width=2, stroke_color=28)
    return c, "photo", "Flower blossom with central circular hole, 6 outer petals, and stem spur."

def generate_sketch_11_scan_mechanical_bracket():
    # Sketch 11: L-Bracket with 3 Mounting Bolt Holes, 2 Alignment Spurs, Scanner Dust
    c = SketchCanvas(512, 512, seed=111)
    c.apply_scanner_texture(base_val=244)
    c.add_speckles(count=60, min_size=1, max_size=2)

    # L-bracket perimeter with 2 notch spurs
    bracket = [
        (100, 100), (410, 100), (410, 200), (430, 200), (410, 200), # Spur at (430, 200)
        (220, 200), (220, 410), (240, 410), (220, 410),             # Spur at (240, 410)
        (100, 410), (100, 100)
    ]
    c.draw_smooth_polyline(bracket, stroke_width=2, stroke_color=18)

    # 3 circular bolt holes
    c.draw_circle(160, 150, radius=22, stroke_width=2, stroke_color=20)
    c.draw_circle(320, 150, radius=22, stroke_width=2, stroke_color=20)
    c.draw_circle(160, 310, radius=22, stroke_width=2, stroke_color=20)
    return c, "scan", "Mechanical L-bracket with 3 mounting holes, notch spurs, and flatbed dust noise."

def generate_sketch_12_scan_lettering_script():
    # Sketch 12: Cursive Lettering ("Art") with Loop Holes and Thin Flourish Spurs
    c = SketchCanvas(512, 512, seed=112)
    c.apply_scanner_texture(base_val=245)
    c.add_speckles(count=55)

    # Letter 'A' loop and crossbar spur
    a_loop = [
        (130, 320), (160, 200), (180, 160), (200, 200), (220, 320),
        (190, 260), (150, 260), (240, 260) # Crossbar spur to right
    ]
    c.draw_smooth_polyline(a_loop, stroke_width=2, stroke_color=20)

    # Inner hole of 'A'
    a_inner = [(165, 250), (180, 190), (195, 250), (165, 250)]
    c.draw_smooth_polyline(a_inner, stroke_width=2, stroke_color=25)

    # Cursive 'r' with top spur
    r_stroke = [(240, 260), (260, 230), (280, 235), (295, 220), (285, 260), (300, 320)]
    c.draw_smooth_polyline(r_stroke, stroke_width=2, stroke_color=22)

    # Cursive 't' with flourish spur
    t_stem = [(340, 140), (340, 300), (370, 320), (410, 310)] # Flourish tail spur
    c.draw_smooth_polyline(t_stem, stroke_width=2, stroke_color=20)
    t_bar = [(310, 210), (380, 210)]
    c.draw_smooth_polyline(t_bar, stroke_width=2, stroke_color=20)
    return c, "scan", "Cursive lettering script with loop holes, crossbar spurs, and paper grain."

def generate_sketch_13_scan_circuit_schematic():
    # Sketch 13: Circuit Schematic with 2 IC Chips, Wire Lines, Resistor Zigzag Spur
    c = SketchCanvas(512, 512, seed=113)
    c.apply_scanner_texture(base_val=246)
    c.add_speckles(count=50)

    # IC 1 (Left Box)
    ic1 = [(80, 180), (180, 180), (180, 330), (80, 330), (80, 180)]
    c.draw_smooth_polyline(ic1, stroke_width=2, stroke_color=20)

    # IC 2 (Right Box)
    ic2 = [(330, 180), (430, 180), (430, 330), (330, 330), (330, 180)]
    c.draw_smooth_polyline(ic2, stroke_width=2, stroke_color=20)

    # Wire bus with resistor zigzag spur
    bus = [
        (180, 220), (220, 220),
        (230, 205), (240, 235), (250, 205), (260, 235), (270, 220), # Resistor zigzag
        (330, 220)
    ]
    c.draw_smooth_polyline(bus, stroke_width=2, stroke_color=25)

    # Ground connection spur
    ground = [(256, 330), (256, 380), (240, 380), (272, 380)]
    c.draw_smooth_polyline(ground, stroke_width=2, stroke_color=25)
    return c, "scan", "Electronic schematic diagram with component boxes, wire bus, and resistor zigzag spur."

def generate_sketch_14_scan_star_mandala():
    # Sketch 14: 6-Pointed Star Polygon with Concentric Circular Ring Hole
    c = SketchCanvas(512, 512, seed=114)
    c.apply_scanner_texture(base_val=243)
    c.add_speckles(count=65)

    # 6-pointed star polygon
    star = []
    for i in range(13):
        theta = 2.0 * math.pi * i / 12.0
        r = 180 if (i % 2 == 0) else 90
        star.append((256 + r * math.cos(theta), 256 + r * math.sin(theta)))
    c.draw_smooth_polyline(star, stroke_width=2, stroke_color=20)

    # Inner circular hole
    c.draw_circle(256, 256, radius=45, stroke_width=2, stroke_color=22)
    return c, "scan", "6-pointed star mandala with concentric center hole and scanner grain."

def generate_sketch_15_scan_optical_spiral():
    # Sketch 15: Archimedean Spiral Ending in Sharp Spur Tip
    c = SketchCanvas(512, 512, seed=115)
    c.apply_scanner_texture(base_val=245)
    c.add_speckles(count=50)

    spiral = []
    turns = 3.2
    steps = 120
    for i in range(steps + 1):
        theta = turns * 2.0 * math.pi * (i / steps)
        r = 15.0 + 175.0 * (i / steps)
        spiral.append((256 + r * math.cos(theta), 256 + r * math.sin(theta)))
    c.draw_smooth_polyline(spiral, stroke_width=2, stroke_color=20)
    return c, "scan", "Continuous Archimedean spiral terminating at sharp outer spur tip."

def generate_sketch_16_scan_key_lock():
    # Sketch 16: Antique Skeleton Key with Bow Hole and 3 Bit Tooth Spurs
    c = SketchCanvas(512, 512, seed=116)
    c.apply_scanner_texture(base_val=244)
    c.add_speckles(count=45)

    # Key bow outer loop
    c.draw_circle(140, 256, radius=55, stroke_width=2, stroke_color=18)

    # Key bow inner hole
    c.draw_circle(140, 256, radius=28, stroke_width=2, stroke_color=25)

    # Shaft
    shaft = [(195, 256), (390, 256)]
    c.draw_smooth_polyline(shaft, stroke_width=2, stroke_color=18)

    # Key bit teeth spurs
    bit = [
        (350, 256), (350, 310), (370, 310), (370, 290),
        (380, 290), (380, 310), (390, 310), (390, 256)
    ]
    c.draw_smooth_polyline(bit, stroke_width=2, stroke_color=20)
    return c, "scan", "Antique key with hollow bow hole, central shaft, and bit teeth spurs."

def generate_sketch_17_scan_bird_in_flight():
    # Sketch 17: Soaring Bird with Feather Spurs, Beak Tip Spur, Eye Hole
    c = SketchCanvas(512, 512, seed=117)
    c.apply_scanner_texture(base_val=245)
    c.add_speckles(count=55)

    # Wing tips and body
    bird = [
        (80, 160), (160, 220), (130, 140), (220, 230), # Left wing feather spurs
        (256, 260), (280, 240), (310, 245),             # Head and beak spur
        (350, 210), (430, 130), (400, 210), (450, 150), # Right wing feather spurs
        (340, 280), (300, 340), (256, 380), (230, 340), # Tail and belly
        (180, 290), (80, 160)
    ]
    c.draw_smooth_polyline(bird, stroke_width=2, stroke_color=20)

    # Eye circle hole
    c.draw_circle(285, 250, radius=6, stroke_width=2, stroke_color=25)
    return c, "scan", "Bird in flight outline with wing feather spurs, beak spur, and eye hole."

def generate_sketch_18_scan_crosshatch_cube():
    # Sketch 18: 3D Isometric Cube with Crosshatched Shading and Bounding Edges
    c = SketchCanvas(512, 512, seed=118)
    c.apply_scanner_texture(base_val=244)
    c.add_speckles(count=60)

    # Top rhomboid face
    top = [(256, 120), (380, 190), (256, 260), (132, 190), (256, 120)]
    c.draw_smooth_polyline(top, stroke_width=2, stroke_color=18)

    # Left face
    left = [(132, 190), (256, 260), (256, 400), (132, 330), (132, 190)]
    c.draw_smooth_polyline(left, stroke_width=2, stroke_color=18)

    # Right face
    right = [(256, 260), (380, 190), (380, 330), (256, 400), (256, 260)]
    c.draw_smooth_polyline(right, stroke_width=2, stroke_color=18)

    # Crosshatching lines on left face
    for y in range(210, 380, 20):
        hatch = [(150, y), (240, y + 50)]
        c.draw_smooth_polyline(hatch, stroke_width=2, stroke_color=35, record_ref=True)
    return c, "scan", "Isometric 3D cube with crosshatching texture and sharp facet borders."

def generate_sketch_19_scan_butterfly_wings():
    # Sketch 19: Butterfly Outline with 4 Wing Cell Holes and Antenna Spurs
    c = SketchCanvas(512, 512, seed=119)
    c.apply_scanner_texture(base_val=246)
    c.add_speckles(count=50)

    # Outer wings and body
    wings = [
        (256, 200), (240, 130), (200, 100), (120, 120), (90, 190), (140, 260), # Top-left wing
        (110, 330), (150, 400), (210, 390), (250, 330),                         # Bottom-left wing
        (256, 370),                                                             # Abdomen tip
        (262, 330), (302, 390), (362, 400), (402, 330),                         # Bottom-right wing
        (372, 260), (422, 190), (392, 120), (312, 100), (272, 130), (256, 200) # Top-right wing
    ]
    c.draw_smooth_polyline(wings, stroke_width=2, stroke_color=20)

    # 4 interior wing cell holes
    cell_tl = [(140, 160), (180, 140), (190, 190), (150, 210), (140, 160)]
    cell_tr = [(372, 160), (332, 140), (322, 190), (362, 210), (372, 160)]
    cell_bl = [(150, 320), (190, 320), (180, 360), (150, 320)]
    cell_br = [(362, 320), (322, 320), (332, 360), (362, 320)]
    c.draw_smooth_polyline(cell_tl, stroke_width=2, stroke_color=25)
    c.draw_smooth_polyline(cell_tr, stroke_width=2, stroke_color=25)
    c.draw_smooth_polyline(cell_bl, stroke_width=2, stroke_color=25)
    c.draw_smooth_polyline(cell_br, stroke_width=2, stroke_color=25)

    # 2 antenna spurs
    c.draw_smooth_polyline([(254, 200), (230, 140), (210, 130)], stroke_width=2, stroke_color=25)
    c.draw_smooth_polyline([(258, 200), (282, 140), (302, 130)], stroke_width=2, stroke_color=25)
    return c, "scan", "Symmetrical butterfly with 4 interior wing cell holes and antenna spurs."

def generate_sketch_20_scan_city_skyline():
    # Sketch 20: City Skyline with 5 Towers, Antenna Spire Spurs, Bridge Arch Hole
    c = SketchCanvas(512, 512, seed=120)
    c.apply_scanner_texture(base_val=245)
    c.add_speckles(count=60)

    skyline = [
        (60, 420), (60, 280), (120, 280),                           # Building 1
        (120, 200), (155, 200), (155, 120), (155, 200), (190, 200), # Tower 2 with spire spur
        (190, 160), (240, 160), (240, 310),                         # Building 3
        (240, 360), (280, 360), (280, 420),                         # Bridge approach
        (280, 240), (340, 240), (340, 140), (340, 80), (340, 140), # High-rise 4 with antenna spur
        (380, 240), (380, 320), (450, 320), (450, 420), (60, 420)   # Building 5 and ground line
    ]
    c.draw_smooth_polyline(skyline, stroke_width=2, stroke_color=18)

    # Bridge arch hole (under bridge at x=240..280)
    arch = [(240, 420), (240, 380), (260, 365), (280, 380), (280, 420)]
    c.draw_smooth_polyline(arch, stroke_width=2, stroke_color=25)
    return c, "scan", "City skyline with high-rise antenna spurs, bridge arch hole, and scan noise."

def main():
    SKETCHES_DIR.mkdir(parents=True, exist_ok=True)

    generators = [
        ("sketch_01_photo_geometric", generate_sketch_01_photo_geometric),
        ("sketch_02_photo_face_profile", generate_sketch_02_photo_face_profile),
        ("sketch_03_photo_botanical_leaf", generate_sketch_03_photo_botanical_leaf),
        ("sketch_04_photo_architecture", generate_sketch_04_photo_architecture),
        ("sketch_05_photo_donut_gears", generate_sketch_05_photo_donut_gears),
        ("sketch_06_photo_figure8_ribbon", generate_sketch_06_photo_figure8_ribbon),
        ("sketch_07_photo_coffee_mug", generate_sketch_07_photo_coffee_mug),
        ("sketch_08_photo_tree_branches", generate_sketch_08_photo_tree_branches),
        ("sketch_09_photo_cat_silhouette", generate_sketch_09_photo_cat_silhouette),
        ("sketch_10_photo_flower_blossom", generate_sketch_10_photo_flower_blossom),
        ("sketch_11_scan_mechanical_bracket", generate_sketch_11_scan_mechanical_bracket),
        ("sketch_12_scan_lettering_script", generate_sketch_12_scan_lettering_script),
        ("sketch_13_scan_circuit_schematic", generate_sketch_13_scan_circuit_schematic),
        ("sketch_14_scan_star_mandala", generate_sketch_14_scan_star_mandala),
        ("sketch_15_scan_optical_spiral", generate_sketch_15_scan_optical_spiral),
        ("sketch_16_scan_key_lock", generate_sketch_16_scan_key_lock),
        ("sketch_17_scan_bird_in_flight", generate_sketch_17_scan_bird_in_flight),
        ("sketch_18_scan_crosshatch_cube", generate_sketch_18_scan_crosshatch_cube),
        ("sketch_19_scan_butterfly_wings", generate_sketch_19_scan_butterfly_wings),
        ("sketch_20_scan_city_skyline", generate_sketch_20_scan_city_skyline)
    ]

    manifest_entries = []
    print("Generating 20 Photographed and Scanned Sketch Fixtures...")

    for sketch_id, gen_fn in generators:
        canvas, category, desc = gen_fn()
        png_data = canvas.to_png_bytes()

        png_path = SKETCHES_DIR / f"{sketch_id}.png"
        png_path.write_bytes(png_data)

        hasher = hashlib.sha256(png_data)
        sha256 = hasher.hexdigest()

        ref_meta = {
            "id": sketch_id,
            "category": category,
            "dimensions": [canvas.width, canvas.height],
            "description": desc,
            "sha256": sha256,
            "referencePathCount": len(canvas.reference_paths),
            "referencePaths": canvas.reference_paths,
            "qualityTargets": {
                "maxGapRate": 0.05,
                "maxErrorPx": 1.5,
                "maxExecutionTimeMs": 3000
            }
        }
        json_path = SKETCHES_DIR / f"{sketch_id}.json"
        json_path.write_text(json.dumps(ref_meta, indent=2))

        manifest_entries.append({
            "id": sketch_id,
            "path": f"sketches/{sketch_id}.png",
            "metadataPath": f"sketches/{sketch_id}.json",
            "category": category,
            "dimensions": [canvas.width, canvas.height],
            "referencePaths": len(canvas.reference_paths),
            "sha256": sha256
        })
        print(f"  [CREATED] {sketch_id}.png ({category}, {len(canvas.reference_paths)} ref paths, {len(png_data)} bytes)")

    corpus_manifest = {
        "corpusVersion": "1.0.0",
        "description": "ArtStudio Photographed and Scanned Sketch Corpus (20 assets)",
        "totalSketches": len(manifest_entries),
        "photographedCount": sum(1 for m in manifest_entries if m["category"] == "photo"),
        "scannedCount": sum(1 for m in manifest_entries if m["category"] == "scan"),
        "sketches": manifest_entries
    }

    manifest_path = SKETCHES_DIR / "manifest.json"
    manifest_path.write_text(json.dumps(corpus_manifest, indent=2))
    print(f"\nCorpus generation complete! Saved 20 sketches + manifest to {SKETCHES_DIR}")

if __name__ == "__main__":
    main()
