#!/usr/bin/env python3
"""
Art Studio Sample Documents Generator
Generates canonical sample documents (.ffart ZIP packages and legacy v1 JSON)
based on tests/fixtures/artstudio/v1/ for round-trip, layer-hierarchy, group/mask,
vector stroke, transparency, animation timeline, and migration tests.
"""

import os
import sys
import json
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURES_DIR = REPO_ROOT / "tests" / "fixtures" / "artstudio" / "v1"
OUTPUT_DIR = FIXTURES_DIR / "sample_projects"

MIMETYPE = "application/vnd.artstudio.project"

def write_ffart_package(out_path: Path, project_dict: dict, assets: dict, thumbnail_bytes: bytes = None):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    if out_path.exists():
        out_path.unlink()

    with zipfile.ZipFile(out_path, mode="w", compression=zipfile.ZIP_DEFLATED) as zf:
        # 1. Uncompressed mimetype first
        mime_bytes = MIMETYPE.encode("ascii")
        mime_info = zipfile.ZipInfo("mimetype")
        mime_info.compress_type = zipfile.ZIP_STORED
        mime_info.file_size = len(mime_bytes)
        zf.writestr(mime_info, mime_bytes)

        # 2. project.json
        project_json = json.dumps(project_dict, indent=2).encode("utf-8")
        zf.writestr("project.json", project_json)

        # 3. thumbnail if provided
        if thumbnail_bytes:
            zf.writestr("thumbnail.png", thumbnail_bytes)

        # 4. assets/*
        for asset_path, data in assets.items():
            norm_path = asset_path if asset_path.startswith("assets/") else f"assets/{asset_path}"
            zf.writestr(norm_path, data)

def generate_all():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    lineart_file = FIXTURES_DIR / "line_art" / "lineart_simple_01.png"
    flat_colour_file = FIXTURES_DIR / "flat_colour" / "flat_colour_simple_01.png"
    transparent_file = FIXTURES_DIR / "transparent_png" / "transparent_cutout_01.png"
    sketch_file = FIXTURES_DIR / "noisy_sketches" / "sketch_noisy_simple_01.png"

    lineart_bytes = lineart_file.read_bytes() if lineart_file.exists() else b"dummy_png"
    flat_colour_bytes = flat_colour_file.read_bytes() if flat_colour_file.exists() else b"dummy_png"
    transparent_bytes = transparent_file.read_bytes() if transparent_file.exists() else b"dummy_png"
    sketch_bytes = sketch_file.read_bytes() if sketch_file.exists() else b"dummy_png"

    # =========================================================================
    # 1. sample_single_layer.ffart
    # =========================================================================
    doc_single = {
        "schemaVersion": "2.0.0",
        "appVersion": "1.0.0",
        "id": "doc_sample_single_01",
        "title": "Single Layer Line Art",
        "createdAt": 1700000000000,
        "updatedAt": 1700000001000,
        "canvas": {
            "width": 512,
            "height": 512,
            "dpi": 300,
            "backgroundColor": -1, # 0xFFFFFFFF
            "colorSpace": "sRGB"
        },
        "rootGroup": {
            "type": "group",
            "id": "root_group",
            "name": "Root",
            "visible": True,
            "opacity": 1.0,
            "blendMode": "normal",
            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
            "isLocked": False,
            "isClippingMask": False,
            "isCollapsed": False,
            "children": [
                {
                    "type": "raster",
                    "id": "layer_lineart_01",
                    "name": "Line Art Layer",
                    "visible": True,
                    "opacity": 1.0,
                    "blendMode": "normal",
                    "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "assetPath": "assets/lineart_simple_01.png",
                    "width": 512,
                    "height": 512,
                    "offsetX": 0,
                    "offsetY": 0
                }
            ]
        },
        "palettes": [
            {
                "id": "pal_monochrome",
                "name": "Monochrome Inking",
                "swatches": [
                    {"hex": "#000000", "name": "Ink Black"},
                    {"hex": "#FFFFFF", "name": "Pure White"},
                    {"hex": "#7F7F7F", "name": "Midtone Gray"}
                ]
            }
        ],
        "referenceImages": []
    }
    write_ffart_package(
        OUTPUT_DIR / "sample_single_layer.ffart",
        doc_single,
        {"assets/lineart_simple_01.png": lineart_bytes},
        thumbnail_bytes=lineart_bytes
    )
    print("Generated sample_single_layer.ffart")

    # =========================================================================
    # 2. sample_multilayer_groups_masks.ffart
    # =========================================================================
    doc_multilayer = {
        "schemaVersion": "2.0.0",
        "appVersion": "1.0.0",
        "id": "doc_sample_multi_02",
        "title": "Character Art with Groups & Clipping Masks",
        "createdAt": 1700000100000,
        "updatedAt": 1700000105000,
        "canvas": {
            "width": 512,
            "height": 512,
            "dpi": 300,
            "backgroundColor": -1,
            "colorSpace": "sRGB"
        },
        "rootGroup": {
            "type": "group",
            "id": "root_group",
            "name": "Root",
            "visible": True,
            "opacity": 1.0,
            "blendMode": "normal",
            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
            "isLocked": False,
            "isClippingMask": False,
            "isCollapsed": False,
            "children": [
                {
                    "type": "group",
                    "id": "grp_background",
                    "name": "Background Group",
                    "visible": True,
                    "opacity": 1.0,
                    "blendMode": "normal",
                    "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "isCollapsed": False,
                    "children": [
                        {
                            "type": "raster",
                            "id": "layer_paper",
                            "name": "Paper Texture",
                            "visible": True,
                            "opacity": 0.9,
                            "blendMode": "normal",
                            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                            "isLocked": True,
                            "isClippingMask": False,
                            "assetPath": "assets/flat_colour_simple_01.png",
                            "width": 512,
                            "height": 512,
                            "offsetX": 0,
                            "offsetY": 0
                        }
                    ]
                },
                {
                    "type": "group",
                    "id": "grp_character",
                    "name": "Character Artwork",
                    "visible": True,
                    "opacity": 1.0,
                    "blendMode": "normal",
                    "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "isCollapsed": False,
                    "children": [
                        {
                            "type": "raster",
                            "id": "layer_base_color",
                            "name": "Base Flat Color",
                            "visible": True,
                            "opacity": 1.0,
                            "blendMode": "normal",
                            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                            "isLocked": False,
                            "isClippingMask": False,
                            "assetPath": "assets/flat_colour_simple_01.png",
                            "width": 512,
                            "height": 512,
                            "offsetX": 0,
                            "offsetY": 0
                        },
                        {
                            "type": "raster",
                            "id": "layer_shadows",
                            "name": "Shadows Layer",
                            "visible": True,
                            "opacity": 0.7,
                            "blendMode": "multiply",
                            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                            "isLocked": False,
                            "isClippingMask": True,
                            "assetPath": "assets/flat_colour_simple_01.png",
                            "width": 512,
                            "height": 512,
                            "offsetX": 0,
                            "offsetY": 0
                        },
                        {
                            "type": "raster",
                            "id": "layer_highlights",
                            "name": "Highlights Screen",
                            "visible": True,
                            "opacity": 0.85,
                            "blendMode": "screen",
                            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                            "isLocked": False,
                            "isClippingMask": True,
                            "assetPath": "assets/flat_colour_simple_01.png",
                            "width": 512,
                            "height": 512,
                            "offsetX": 0,
                            "offsetY": 0
                        },
                        {
                            "type": "raster",
                            "id": "layer_lineart",
                            "name": "Lineart Overlay",
                            "visible": True,
                            "opacity": 1.0,
                            "blendMode": "normal",
                            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                            "isLocked": False,
                            "isClippingMask": False,
                            "assetPath": "assets/lineart_simple_01.png",
                            "width": 512,
                            "height": 512,
                            "offsetX": 0,
                            "offsetY": 0
                        }
                    ]
                }
            ]
        },
        "palettes": [
            {
                "id": "pal_char_01",
                "name": "Anime Character Palette",
                "swatches": [
                    {"hex": "#FFDFD3", "name": "Skin"},
                    {"hex": "#957DAD", "name": "Hair"},
                    {"hex": "#D291BC", "name": "Clothing"}
                ]
            }
        ],
        "referenceImages": [],
        "autoDrawProvenance": {
            "sourceImageHash": "e57bb565dbdc2d8977bbba949b2cbe67576b5c39121a88bb8f060fc18ae676b7",
            "algorithm": "offline_morphological_v1",
            "edgeThresholdLow": 40.0,
            "edgeThresholdHigh": 120.0,
            "generatedLayerIds": ["layer_base_color", "layer_shadows", "layer_lineart"],
            "timestampMs": 1700000100000
        }
    }
    write_ffart_package(
        OUTPUT_DIR / "sample_multilayer_groups_masks.ffart",
        doc_multilayer,
        {
            "assets/lineart_simple_01.png": lineart_bytes,
            "assets/flat_colour_simple_01.png": flat_colour_bytes
        },
        thumbnail_bytes=flat_colour_bytes
    )
    print("Generated sample_multilayer_groups_masks.ffart")

    # =========================================================================
    # 3. sample_vector_paths.ffart
    # =========================================================================
    doc_vector = {
        "schemaVersion": "2.0.0",
        "appVersion": "1.0.0",
        "id": "doc_sample_vector_03",
        "title": "Vector Paths & Pressure Curves",
        "createdAt": 1700000200000,
        "updatedAt": 1700000202000,
        "canvas": {
            "width": 1080,
            "height": 1080,
            "dpi": 300,
            "backgroundColor": -1,
            "colorSpace": "sRGB"
        },
        "rootGroup": {
            "type": "group",
            "id": "root_group",
            "name": "Root",
            "visible": True,
            "opacity": 1.0,
            "blendMode": "normal",
            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
            "isLocked": False,
            "isClippingMask": False,
            "isCollapsed": False,
            "children": [
                {
                    "type": "vector",
                    "id": "layer_ink_strokes",
                    "name": "Inked Strokes",
                    "visible": True,
                    "opacity": 1.0,
                    "blendMode": "normal",
                    "transform": {"tx": 10.0, "ty": 15.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "paths": [
                        {
                            "id": "path_curve_01",
                            "brushId": "inking_pen",
                            "strokeColor": -16777216, # 0xFF000000
                            "strokeWidth": 6.0,
                            "points": [
                                {"x": 100.0, "y": 150.0, "p": 0.3, "tx": 0.0, "ty": 0.0, "t": 100},
                                {"x": 120.0, "y": 180.0, "p": 0.6, "tx": 0.1, "ty": 0.0, "t": 116},
                                {"x": 150.0, "y": 210.0, "p": 0.9, "tx": 0.2, "ty": 0.1, "t": 133},
                                {"x": 190.0, "y": 230.0, "p": 0.5, "tx": 0.0, "ty": 0.0, "t": 150}
                            ]
                        },
                        {
                            "id": "path_curve_02",
                            "brushId": "rough_pencil",
                            "strokeColor": -11184811, # 0xFF555555
                            "strokeWidth": 3.5,
                            "points": [
                                {"x": 200.0, "y": 300.0, "p": 0.4, "tx": 0.0, "ty": 0.0, "t": 200},
                                {"x": 240.0, "y": 320.0, "p": 0.7, "tx": 0.1, "ty": 0.0, "t": 216},
                                {"x": 290.0, "y": 330.0, "p": 0.3, "tx": 0.0, "ty": 0.0, "t": 233}
                            ]
                        }
                    ]
                }
            ]
        },
        "palettes": [],
        "referenceImages": []
    }
    write_ffart_package(
        OUTPUT_DIR / "sample_vector_paths.ffart",
        doc_vector,
        {}
    )
    print("Generated sample_vector_paths.ffart")

    # =========================================================================
    # 4. sample_transparency_and_reference.ffart
    # =========================================================================
    doc_transparency = {
        "schemaVersion": "2.0.0",
        "appVersion": "1.0.0",
        "id": "doc_sample_transp_04",
        "title": "Transparent Cutout & Pinned Reference",
        "createdAt": 1700000300000,
        "updatedAt": 1700000305000,
        "canvas": {
            "width": 512,
            "height": 512,
            "dpi": 300,
            "backgroundColor": 0, # Fully transparent 0x00000000
            "colorSpace": "sRGB"
        },
        "rootGroup": {
            "type": "group",
            "id": "root_group",
            "name": "Root",
            "visible": True,
            "opacity": 1.0,
            "blendMode": "normal",
            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
            "isLocked": False,
            "isClippingMask": False,
            "isCollapsed": False,
            "children": [
                {
                    "type": "raster",
                    "id": "layer_cutout_01",
                    "name": "Transparent Cutout",
                    "visible": True,
                    "opacity": 0.95,
                    "blendMode": "normal",
                    "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "assetPath": "assets/transparent_cutout_01.png",
                    "width": 512,
                    "height": 512,
                    "offsetX": 0,
                    "offsetY": 0
                }
            ]
        },
        "palettes": [],
        "referenceImages": [
            {
                "id": "ref_sketch_01",
                "assetPath": "assets/sketch_noisy_simple_01.png",
                "x": 20.0,
                "y": 20.0,
                "width": 180.0,
                "height": 180.0,
                "opacity": 0.65,
                "visible": True
            }
        ]
    }
    write_ffart_package(
        OUTPUT_DIR / "sample_transparency_and_reference.ffart",
        doc_transparency,
        {
            "assets/transparent_cutout_01.png": transparent_bytes,
            "assets/sketch_noisy_simple_01.png": sketch_bytes
        }
    )
    print("Generated sample_transparency_and_reference.ffart")

    # =========================================================================
    # 5. sample_animation_timeline.ffart
    # =========================================================================
    doc_animation = {
        "schemaVersion": "2.0.0",
        "appVersion": "1.0.0",
        "id": "doc_sample_anim_05",
        "title": "Animation Flipbook 8-Frames",
        "createdAt": 1700000400000,
        "updatedAt": 1700000405000,
        "canvas": {
            "width": 512,
            "height": 512,
            "dpi": 240,
            "backgroundColor": -1,
            "colorSpace": "sRGB"
        },
        "rootGroup": {
            "type": "group",
            "id": "root_group",
            "name": "Root",
            "visible": True,
            "opacity": 1.0,
            "blendMode": "normal",
            "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
            "isLocked": False,
            "isClippingMask": False,
            "isCollapsed": False,
            "children": [
                {
                    "type": "raster",
                    "id": "frame_layer_1",
                    "name": "Frame 1-2 Keyframe",
                    "visible": True,
                    "opacity": 1.0,
                    "blendMode": "normal",
                    "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "assetPath": "assets/lineart_simple_01.png",
                    "width": 512,
                    "height": 512,
                    "offsetX": 0,
                    "offsetY": 0
                },
                {
                    "type": "raster",
                    "id": "frame_layer_2",
                    "name": "Frame 3-4 Inbetween",
                    "visible": True,
                    "opacity": 1.0,
                    "blendMode": "normal",
                    "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
                    "isLocked": False,
                    "isClippingMask": False,
                    "assetPath": "assets/lineart_simple_01.png",
                    "width": 512,
                    "height": 512,
                    "offsetX": 0,
                    "offsetY": 0
                }
            ]
        },
        "palettes": [],
        "referenceImages": [],
        "animation": {
            "fps": 12,
            "totalFrames": 8,
            "onionSkinEnabled": True,
            "onionSkinFramesBefore": 2,
            "onionSkinFramesAfter": 1,
            "frameTracks": [
                {"layerId": "frame_layer_1", "activeFrames": [0, 1]},
                {"layerId": "frame_layer_2", "activeFrames": [2, 3]}
            ]
        }
    }
    write_ffart_package(
        OUTPUT_DIR / "sample_animation_timeline.ffart",
        doc_animation,
        {"assets/lineart_simple_01.png": lineart_bytes}
    )
    print("Generated sample_animation_timeline.ffart")

    # =========================================================================
    # 6. sample_legacy_v1.json (Legacy Schema)
    # =========================================================================
    doc_legacy_v1 = {
        "schemaVersion": "1.0.0",
        "appVersion": "0.9.0",
        "id": "doc_legacy_v1_sample",
        "title": "Legacy v1 Flat Project",
        "createdAt": 1690000000000,
        "canvas": {
            "width": 512,
            "height": 512,
            "dpi": 300,
            "backgroundColor": -1
        },
        "layers": [
            {
                "id": "legacy_layer_bg",
                "name": "Background",
                "type": "raster",
                "visible": True,
                "opacity": 100, # 0..100 scale in v1!
                "blendMode": "normal",
                "assetPath": "assets/flat_colour_simple_01.png",
                "width": 512,
                "height": 512,
                "offsetX": 0,
                "offsetY": 0,
                "isLocked": True
            },
            {
                "id": "legacy_layer_ink",
                "name": "Ink Strokes",
                "type": "raster",
                "visible": True,
                "opacity": 85, # 85% opacity
                "blendMode": "multiply",
                "assetPath": "assets/lineart_simple_01.png",
                "width": 512,
                "height": 512,
                "offsetX": 0,
                "offsetY": 0,
                "isLocked": False
            }
        ],
        "palettes": [
            {
                "id": "pal_v1_default",
                "name": "Default v1",
                "swatches": [
                    {"hex": "#000000", "name": "Black"},
                    {"hex": "#FF0000", "name": "Red"}
                ]
            }
        ]
    }
    legacy_json_path = OUTPUT_DIR / "sample_legacy_v1.json"
    legacy_json_path.write_text(json.dumps(doc_legacy_v1, indent=2), encoding="utf-8")
    print("Generated sample_legacy_v1.json")

    # =========================================================================
    # 7. sample_corrupt_bad_mimetype.ffart
    # =========================================================================
    corrupt_mime_path = OUTPUT_DIR / "sample_corrupt_bad_mimetype.ffart"
    with zipfile.ZipFile(corrupt_mime_path, mode="w") as zf:
        zf.writestr("mimetype", "text/plain")
        zf.writestr("project.json", json.dumps(doc_single))
    print("Generated sample_corrupt_bad_mimetype.ffart")

    # =========================================================================
    # 8. sample_corrupt_missing_json.ffart
    # =========================================================================
    corrupt_nojson_path = OUTPUT_DIR / "sample_corrupt_missing_json.ffart"
    with zipfile.ZipFile(corrupt_nojson_path, mode="w") as zf:
        zf.writestr("mimetype", MIMETYPE)
        zf.writestr("random_file.txt", "hello")
    print("Generated sample_corrupt_missing_json.ffart")

    # =========================================================================
    # 9. sample_corrupt_invalid_json.ffart
    # =========================================================================
    corrupt_syntax_path = OUTPUT_DIR / "sample_corrupt_invalid_json.ffart"
    with zipfile.ZipFile(corrupt_syntax_path, mode="w") as zf:
        zf.writestr("mimetype", MIMETYPE)
        zf.writestr("project.json", "{ malformed json: true, broken... ")
    print("Generated sample_corrupt_invalid_json.ffart")

    print("\nAll 9 sample documents successfully generated in:")
    print(OUTPUT_DIR)

if __name__ == "__main__":
    generate_all()
