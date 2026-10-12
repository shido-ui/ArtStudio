# Art Studio — Texture & Stamp Asset Specification

This document defines the authoritative file format specifications, validation invariants, memory bounds, and error handling rules for external and bundled brush textures and stamps in Art Studio.

---

## 1. Supported File Formats & Encodings

| Format | MIME Type | Magic Signature | Optimal Usage | Recommendation |
|---|---|---|---|---|
| **PNG** | `image/png` | `\x89PNG\r\n\x1a\n` (`89 50 4E 47 0D 0A 1A 0A`) | Lossless alpha masks, 8-bit grayscale, 32-bit RGBA stamps | **Primary (Recommended)** |
| **WebP** | `image/webp` | `RIFF....WEBP` | Compact photo/grain textures | Supported |
| **JPEG** | `image/jpeg` | `\xFF\xD8\xFF` | High-frequency background grains without alpha | Supported |

---

## 2. Color Type & Channel Interpretation

Textures in Art Studio are interpreted as 8-bit single-channel intensity/alpha masks ($M(x, y) \in [0, 255]$):

1. **8-bit Grayscale (PNG Color Type 0)**:
   - Direct 1:1 byte mapping: intensity $= Y$.
   - Used for built-in high-efficiency textures ($64 \times 64 = 4$ KB uncompressed).
2. **8-bit Grayscale with Alpha (PNG Color Type 4)**:
   - Evaluated as $M(x, y) = (Y \times A) / 255$.
3. **24-bit RGB Truecolor (PNG Color Type 2 / JPEG)**:
   - Converted to grayscale luminance via standard Rec. 601 coefficients:
     $$Y = 0.299R + 0.587G + 0.114B$$
4. **32-bit RGBA Truecolor with Alpha (PNG Color Type 6 / WebP)**:
   - If the alpha channel has non-trivial variation ($\min(A) < 255$), the alpha channel $A$ is used as the mask: $M(x, y) = A$.
   - If the alpha channel is fully opaque ($A = 255$ everywhere), luminance $Y$ is used as the mask: $M(x, y) = Y$.

---

## 3. Dimensional Limits & Memory Budgets

To protect mobile devices operating within 4 GB physical RAM budgets from Out-Of-Memory (OOM) crashes:

| Attribute | Minimum | Default / Recommended | Maximum Permitted | Action on Violation |
|---|---|---|---|---|
| **Width** | 8 pixels | 64 to 256 pixels | **1,024 pixels** | Rejected with `DIMENSIONS_TOO_LARGE` |
| **Height** | 8 pixels | 64 to 256 pixels | **1,024 pixels** | Rejected with `DIMENSIONS_TOO_LARGE` |
| **File Size** | 16 bytes | $< 100$ KB | **2.0 MB (2,097,152 bytes)** | Rejected with `FILE_TOO_LARGE` |
| **Aspect Ratio** | 1:4 (0.25) | 1:1 (Square) | 4:1 (4.0) | Clamped / Letterboxed |
| **RAM Footprint (Uncompressed)** | 64 bytes | 4 KB to 64 KB | **1.0 MB ($1024 \times 1024$)** | Bounded by dimension ceiling |

---

## 4. Texture Categories

1. **`GRAIN` ("Grain / Paper Tooth")**:
   - Continuous stochastic or multi-octave noise applied across strokes or the entire canvas.
   - Evaluated via screen/tooth skip thresholding (e.g. Dry Brush, Pencil, Chalk).
2. **`PATTERN` ("Seamless Pattern")**:
   - Repeating 2D tiling pattern (e.g. Canvas Weave, Halftone Dots, Screentone).
   - Coordinates wrap seamlessly across $[0, 1] \times [0, 1]$.
3. **`STAMP` ("Dab Stamp")**:
   - Discrete 2D silhouette or porous alpha footprint (e.g. Sea Sponge, Ink Blot, Foliage).
   - Applied at individual stroke dab locations with rotation/scale jitter and scatter.
4. **`TEXTURED_STROKE` ("Textured Stroke")**:
   - Directional bristle or rake striations oriented along the stroke tangent.

---

## 5. Validation Invariants & Error Codes

Importing or loading an asset performs strict pre-flight validation. Violations result in an explicit `TextureValidationResult.Error` or `TextureLoadError`:

- `FILE_NOT_FOUND`: Target asset file does not exist on disk or in APK assets.
- `EMPTY_FILE`: File length is 0 bytes.
- `FILE_TOO_LARGE`: File size exceeds 2,097,152 bytes (2 MB).
- `DIMENSIONS_TOO_LARGE`: Width or height exceeds 1,024 pixels.
- `DIMENSIONS_TOO_SMALL`: Width or height is less than 8 pixels.
- `INVALID_HEADER_OR_FORMAT`: File does not match magic byte signature for PNG, WebP, or JPEG.
- `CORRUPT_OR_TRUNCATED`: Truncated chunk headers, invalid IHDR chunk length, or CRC failure.
- `BUILT_IN_CANNOT_BE_MODIFIED`: Attempt to rename or modify a bundled APK asset.
- `BUILT_IN_CANNOT_BE_DELETED`: Attempt to delete a bundled APK asset.

---

## 6. Graceful Fallback Policy (Zero Silent Failures)

When a brush references an asset that is missing, oversized, or corrupted:
1. **Never Fail Silently**: The engine does NOT substitute an invisible blank or black texture.
2. **Explicit Error Event**: A `TextureLoadError` is recorded and exposed to the user interface with an accessible warning indicator and explanation dialog.
3. **Diagnostic Fallback**: The engine renders a clearly visible procedural diagnostic pattern (e.g. standard circular dab with diagnostic stipple) so the artist can continue drawing while alerting them to re-link or reset the asset.
