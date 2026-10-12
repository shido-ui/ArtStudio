# Art Studio Test Fixtures Corpus v1

This directory holds the versioned test corpus for the Android-First Digital Art Application.
All fixtures in this corpus must be licensed under CC0, Public Domain, or custom repository-authored test assets.
**NO copyrighted, scraped, or unknown-license artwork is permitted.**

## Directory Structure
- `manifest.json`: Authoritative catalog of all 18 fixtures with SHA-256 hashes, license identifiers, source references, and intended milestone use.
- `LICENSES.md`: Explicit license declaration (CC0-1.0) and provenance documentation.
- `line_art/`: Monochromatic line art drawings (simple and dense) for edge detection, gap closing, flood fill, and No Colour baseline tests.
- `flat_colour/`: Multi-region flat-color illustrations (simple and dense) for palette quantization, segmentation, and region filling.
- `noisy_sketches/`: Multi-frequency noisy sketches (simple and dense) for line cleanup, thresholding, and despeckle algorithms.
- `transparent_png/`: Alpha channel assets (cutout and gradient) for alpha lock, layer opacity, and transparency compositing tests.
- `exif_rotated/`: JPEGs with EXIF orientation tags (90° CW and 180°) for rotation-aware raster import validation.
- `corrupt_truncated/`: Fault-injection test cases (truncated PNG, corrupted ORA archive, malformed SVG) to ensure robust error handling and zero crashes.
- `layered_ora/`: Multi-layer OpenRaster archives (`stack.xml` + PNG layers) for layered interchange import/export validation.
- `svg_samples/`: Safe-subset SVG files (simple and detail-dense) for vector import/export and path rasterization.
- `palettes/`: Color swatches, gradient tables, and Colour Lab 15-style reference palettes.

## Verification
Run the QA validator:
```bash
python tools/qa/verify_art_fixtures.py
```
