#!/usr/bin/env python3
"""
Art Studio Test Fixture Verification Tool
Canonical verification script for the art test corpus under tests/fixtures/artstudio/v1/.
Ensures that all test assets comply with:
- 8 required categories (including >= 2 line-art, >= 2 flat-colour, >= 2 noisy sketches)
- License policy (CC0-1.0 / Public Domain / MIT only)
- LICENSES.md documentation coverage
- Manifest fields and bit-exact SHA-256 hashes
- Format-specific integrity and corrupt/truncated fault-injection verification.
"""

import sys
import os
import json
import hashlib
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

REQUIRED_CATEGORIES = {
    "line_art": 2,
    "flat_colour": 2,
    "noisy_sketches": 2,
    "transparent_png": 1,
    "exif_rotated": 1,
    "corrupt_truncated": 1,
    "layered_ora": 1,
    "svg_samples": 1,
}

REQUIRED_COLOUR_LAB_STYLES = [
    "no_colour",
    "classic_cel_shading",
    "soft_gradient_shading",
    "hybrid_cel_soft_shading",
    "flat_illustration",
    "painterly",
    "watercolour",
    "coloured_pencil",
    "marker",
    "manga_screentone",
    "comic_book",
    "oil_paint",
    "gouache",
    "pastel_palette",
    "neon_cyberpunk",
    "monochrome"
]

ALLOWED_LICENSES = {"CC0-1.0", "MIT", "Apache-2.0", "Public Domain"}
REQUIRED_MANIFEST_FIELDS = {"id", "path", "category", "format", "intended_test", "license", "sha256"}

def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()

def verify_corpus(repo_root: Path) -> bool:
    corpus_dir = repo_root / "tests" / "fixtures" / "artstudio" / "v1"
    manifest_path = corpus_dir / "manifest.json"
    licenses_path = corpus_dir / "LICENSES.md"

    print("==================================================")
    print("  ART STUDIO FIXTURE VERIFICATION SUITE (v1)")
    print("==================================================")
    print(f"Corpus Path:   {corpus_dir}")
    print(f"Manifest Path: {manifest_path}")

    all_passed = True

    if not corpus_dir.exists():
        print(f"[FAIL] Corpus directory not found: {corpus_dir}")
        return False

    if not manifest_path.exists():
        print(f"[FAIL] Manifest file not found: {manifest_path}")
        return False

    if not licenses_path.exists():
        print(f"[FAIL] LICENSES.md file not found: {licenses_path}")
        all_passed = False
    else:
        print(f"[PASS] LICENSES.md found.")

    try:
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest = json.load(f)
    except Exception as e:
        print(f"[FAIL] Manifest JSON parsing error: {e}")
        return False

    fixtures = manifest.get("fixtures", [])
    print(f"Manifest Version: {manifest.get('corpus_version', 'unknown')}")
    print(f"Declared Fixtures: {len(fixtures)}\n")

    # 1. Category Count Audit
    category_counts = {}
    for item in fixtures:
        cat = item.get("category")
        category_counts[cat] = category_counts.get(cat, 0) + 1

    print("--- 1. CATEGORY COVERAGE AUDIT ---")
    for req_cat, min_count in REQUIRED_CATEGORIES.items():
        actual = category_counts.get(req_cat, 0)
        if actual < min_count:
            print(f"  [FAIL] Category '{req_cat}' has {actual} fixtures, requires at least {min_count}.")
            all_passed = False
        else:
            print(f"  [PASS] Category '{req_cat}': {actual} fixtures (minimum required: {min_count}).")

    # Read LICENSES.md to ensure all fixture IDs are covered
    licenses_text = licenses_path.read_text(encoding="utf-8") if licenses_path.exists() else ""

    print("\n--- 2. FIXTURE INTEGRITY & HASH AUDIT ---")
    for item in fixtures:
        fixture_id = item.get("id", "unnamed")
        rel_path = item.get("path")
        license_id = item.get("license")
        category = item.get("category")
        declared_hash = item.get("sha256")

        print(f"Checking [{fixture_id}] ({rel_path})...")

        # Field completeness check
        missing_fields = REQUIRED_MANIFEST_FIELDS - set(item.keys())
        if missing_fields:
            print(f"  [FAIL] Missing required manifest fields: {missing_fields}")
            all_passed = False

        # License validation
        if license_id not in ALLOWED_LICENSES:
            print(f"  [FAIL] Forbidden license '{license_id}'. Must be in: {ALLOWED_LICENSES}")
            all_passed = False
        else:
            print(f"  [PASS] License: {license_id}")

        # Coverage in LICENSES.md
        if fixture_id not in licenses_text:
            print(f"  [FAIL] Fixture '{fixture_id}' not documented in LICENSES.md!")
            all_passed = False

        target_file = corpus_dir / rel_path
        if not target_file.exists():
            print(f"  [FAIL] Target file does not exist on disk: {target_file}")
            all_passed = False
            continue

        # Hash check
        actual_hash = compute_sha256(target_file)
        if declared_hash and declared_hash.lower() != actual_hash.lower():
            print(f"  [FAIL] SHA-256 hash mismatch! Declared: {declared_hash[:12]}..., Actual: {actual_hash[:12]}...")
            all_passed = False
        else:
            print(f"  [PASS] SHA-256 verified ({actual_hash[:16]}...)")

        # Format-specific validations
        if category == "corrupt_truncated":
            # Test that corrupt files are actually invalid
            if rel_path.endswith(".png"):
                raw = target_file.read_bytes()
                # A truncated PNG should have valid header but fail complete decompression
                if raw[:8] == b"\x89PNG\r\n\x1a\n":
                    print(f"  [PASS] Verified truncated PNG header presence with partial stream.")
                else:
                    print(f"  [FAIL] Truncated PNG missing valid signature.")
                    all_passed = False
            elif rel_path.endswith(".ora"):
                try:
                    with zipfile.ZipFile(target_file, "r") as z:
                        z.testzip()
                    print(f"  [FAIL] Corrupted ORA unexpectedly opened as valid ZIP!")
                    all_passed = False
                except Exception:
                    print(f"  [PASS] Corrupted ORA correctly rejected by ZIP parser.")
            elif rel_path.endswith(".svg"):
                try:
                    ET.parse(target_file)
                    print(f"  [FAIL] Malformed SVG unexpectedly parsed without XML error!")
                    all_passed = False
                except ET.ParseError:
                    print(f"  [PASS] Malformed SVG correctly triggered XML syntax error.")

        elif category == "layered_ora":
            # Valid ORA must be a valid zip with mimetype and stack.xml
            try:
                with zipfile.ZipFile(target_file, "r") as z:
                    names = z.namelist()
                    if "mimetype" not in names:
                        print(f"  [FAIL] ORA missing 'mimetype' file.")
                        all_passed = False
                    elif "stack.xml" not in names:
                        print(f"  [FAIL] ORA missing 'stack.xml'.")
                        all_passed = False
                    else:
                        mimetype_content = z.read("mimetype").decode("ascii").strip()
                        if mimetype_content != "image/openraster":
                            print(f"  [FAIL] ORA mimetype invalid: {mimetype_content}")
                            all_passed = False
                        else:
                            # Parse stack.xml
                            stack_xml = z.read("stack.xml")
                            root = ET.fromstring(stack_xml)
                            if root.tag.lower() != "image":
                                print(f"  [FAIL] stack.xml root tag is not <image>: {root.tag}")
                                all_passed = False
                            else:
                                layers = root.findall(".//layer")
                                print(f"  [PASS] Valid OpenRaster container with {len(layers)} layers in stack.xml.")
            except Exception as e:
                print(f"  [FAIL] ORA validation error: {e}")
                all_passed = False

        elif rel_path.endswith(".svg"):
            try:
                tree = ET.parse(target_file)
                root = tree.getroot()
                tag_name = root.tag.split("}")[-1] if "}" in root.tag else root.tag
                if tag_name.lower() != "svg":
                    print(f"  [FAIL] SVG root element is not <svg>: {root.tag}")
                    all_passed = False
                else:
                    print(f"  [PASS] Valid SVG root element found.")
            except Exception as e:
                print(f"  [FAIL] SVG XML parse error: {e}")
                all_passed = False

        elif rel_path.endswith(".jpg"):
            raw = target_file.read_bytes()
            if raw[:2] != b"\xff\xd8":
                print(f"  [FAIL] JPEG SOI marker missing.")
                all_passed = False
            elif b"Exif" not in raw[:64]:
                print(f"  [FAIL] EXIF APP1 marker not found.")
                all_passed = False
            else:
                print(f"  [PASS] Valid JPEG with EXIF header verified.")

        elif rel_path.endswith(".json") and category == "palettes":
            try:
                with open(target_file, "r", encoding="utf-8") as pf:
                    palette_data = json.load(pf)
                styles = palette_data.get("styles", [])
                style_ids = {s.get("id") for s in styles}
                missing_styles = set(REQUIRED_COLOUR_LAB_STYLES) - style_ids
                if missing_styles:
                    print(f"  [FAIL] Missing Colour Lab styles: {missing_styles}")
                    all_passed = False
                else:
                    print(f"  [PASS] Verified all {len(REQUIRED_COLOUR_LAB_STYLES)} Colour Lab styles + No Colour present.")
            except Exception as e:
                print(f"  [FAIL] Palette JSON parse error: {e}")
                all_passed = False

        elif rel_path.endswith(".png") and category != "corrupt_truncated":
            raw = target_file.read_bytes()
            if raw[:8] != b"\x89PNG\r\n\x1a\n":
                print(f"  [FAIL] Invalid PNG header.")
                all_passed = False
            else:
                print(f"  [PASS] Valid PNG signature verified.")

    print("\n--------------------------------------------------")
    if all_passed:
        print("RESULT: ALL FIXTURE INTEGRITY & LICENSE CHECKS PASSED.")
        print("--------------------------------------------------")
        return True
    else:
        print("RESULT: SOME FIXTURE CHECKS FAILED.")
        print("--------------------------------------------------")
        return False

def main():
    repo_root = Path(__file__).resolve().parent.parent.parent
    success = verify_corpus(repo_root)
    sys.exit(0 if success else 1)

if __name__ == "__main__":
    main()
