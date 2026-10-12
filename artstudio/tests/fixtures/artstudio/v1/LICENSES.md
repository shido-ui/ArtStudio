# Art Studio Test Corpus v1 — Licenses and Provenance

All assets in `tests/fixtures/artstudio/v1/` are dedicated to the public domain under the **Creative Commons Zero 1.0 Universal (CC0-1.0)** license.

## Provenance and Creation Methodology
- **Author**: Antigravity Art Studio Engineering & QA Team
- **Generation Method**: 100% synthetically generated via deterministic Python scripts (`tools/qa/generate_art_fixtures.py`) using geometric primitives, mathematical Bezier splines, procedural noise algorithms, and standard file format serialization.
- **Copyright Status**: Zero third-party, scraped, copyrighted, or unverified art assets are included in this repository.

## Inventory of Assets & License Declarations

| Fixture ID | Category | Relative Path | License | Dimensions | Complexity |
|---|---|---|---|---|---|
| `lineart_simple_01` | `line_art` | `line_art/lineart_simple_01.png` | CC0-1.0 | 512x512 | Simple |
| `lineart_dense_02` | `line_art` | `line_art/lineart_dense_02.png` | CC0-1.0 | 1024x1024 | Detail-dense |
| `flat_colour_simple_01` | `flat_colour` | `flat_colour/flat_colour_simple_01.png` | CC0-1.0 | 512x512 | Simple |
| `flat_colour_dense_02` | `flat_colour` | `flat_colour/flat_colour_dense_02.png` | CC0-1.0 | 1024x1024 | Detail-dense |
| `sketch_noisy_simple_01` | `noisy_sketches` | `noisy_sketches/sketch_noisy_simple_01.png` | CC0-1.0 | 512x512 | Simple |
| `sketch_noisy_dense_02` | `noisy_sketches` | `noisy_sketches/sketch_noisy_dense_02.png` | CC0-1.0 | 1024x1024 | Detail-dense |
| `transparent_cutout_01` | `transparent_png` | `transparent_png/transparent_cutout_01.png` | CC0-1.0 | 512x512 | Simple |
| `transparent_gradient_02` | `transparent_png` | `transparent_png/transparent_gradient_02.png` | CC0-1.0 | 512x512 | Detail-dense |
| `exif_rotate_90_cw_01` | `exif_rotated` | `exif_rotated/exif_rotate_90_cw_01.jpg` | CC0-1.0 | 256x512 | Simple |
| `exif_rotate_180_02` | `exif_rotated` | `exif_rotated/exif_rotate_180_02.jpg` | CC0-1.0 | 512x512 | Simple |
| `truncated_idat_01` | `corrupt_truncated` | `corrupt_truncated/truncated_idat_01.png` | CC0-1.0 | N/A | Corrupted |
| `corrupted_archive_02` | `corrupt_truncated` | `corrupt_truncated/corrupted_archive_02.ora` | CC0-1.0 | N/A | Corrupted |
| `malformed_tags_03` | `corrupt_truncated` | `corrupt_truncated/malformed_tags_03.svg` | CC0-1.0 | N/A | Corrupted |
| `sample_layered_simple_01` | `layered_ora` | `layered_ora/sample_layered_simple_01.ora` | CC0-1.0 | 512x512 | Simple |
| `sample_layered_dense_02` | `layered_ora` | `layered_ora/sample_layered_dense_02.ora` | CC0-1.0 | 1024x1024 | Detail-dense |
| `sample_vector_simple_01` | `svg_samples` | `svg_samples/sample_vector_simple_01.svg` | CC0-1.0 | 512x512 | Simple |
| `sample_vector_dense_02` | `svg_samples` | `svg_samples/sample_vector_dense_02.svg` | CC0-1.0 | 1024x1024 | Detail-dense |
| `sample_layered_simple_01_psd` | `layered_psd` | `layered_psd/sample_layered_simple_01.psd` | CC0-1.0 | 64x64 | Simple |
| `sample_layered_clipping_02_psd` | `layered_psd` | `layered_psd/sample_layered_clipping_02.psd` | CC0-1.0 | 64x64 | Detail-dense |
| `corrupted_header_01_psd` | `corrupt_truncated` | `corrupt_truncated/corrupted_header_01.psd` | CC0-1.0 | N/A | Corrupted |
| `truncated_channels_02_psd` | `corrupt_truncated` | `corrupt_truncated/truncated_channels_02.psd` | CC0-1.0 | N/A | Corrupted |
| `palette_15_styles` | `palettes` | `palettes/colour_lab_reference_palettes.json` | CC0-1.0 | N/A | Reference |

## Bundled Typography Assets (SIL Open Font License 1.1)

All bundled application fonts in `app/src/main/assets/fonts/` are released under the **SIL Open Font License, Version 1.1 (OFL-1.1)** with full auditable provenance and zero commercial restrictions.

| Font Asset ID | Font Family | Asset Path | License | Designer / Author | SHA-256 Hash |
|---|---|---|---|---|---|
| `builtin_carrois_gothic_sc` | Carrois Gothic SC | `fonts/CarroisGothicSC-Regular.ttf` | OFL-1.1 | Vernon Adams | `3dfc5fe797ab3e04327287d9dbae313847623670417e850d39e24662071f1c98` |
| `builtin_cutive_mono` | Cutive Mono | `fonts/CutiveMono.ttf` | OFL-1.1 | Vernon Adams | `543c74f6185531aebf6151fba7c618f5aeb43af8cb6b09d70e97e3611b351e6e` |

## Modification & Attribution Notes
No external works were adapted, traced, or incorporated without license clearance. Synthetic fixtures remain CC0-1.0; typography assets adhere strictly to SIL OFL-1.1 terms documented in `app/src/main/assets/fonts/OFL.txt`.
