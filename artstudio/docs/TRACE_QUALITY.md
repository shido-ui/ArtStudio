# ArtStudio Computer Vision & AutoDraw Trace Quality Specification

This document records the architecture, algorithmic hardening, and authoritative trace-quality benchmark metrics for the **ArtStudio** Computer Vision and AutoDraw subsystem (`com.artstudio.cv`, `com.artstudio.autodraw`).

---

## 1. Algorithmic Hardening Architecture

The Classical Computer Vision pipeline (`CvPipeline`, `AutoDrawV1Pipeline`) was hardened to process real-world scanned and photographed sketches safely and deterministically:

```mermaid
flowchart TD
    A["Raw Input Image (up to 12 MP)"] --> B["Stage 1: Max-Dimension Downscaler (long edge <= 1536)"]
    B --> C["Stage 2: Grayscale Conversion (Rec. 709)"]
    C --> D["Stage 3: Illumination & Shadow Normalization (Separable Moving Average)"]
    D --> E["Stage 4: Denoising (Median Despeckle / Gaussian)"]
    E --> F["Stage 5: Thresholding (Otsu / Adaptive Local Mean)"]
    F --> G["Stage 6: Connected-Component Speckle Filter (minPixels = 4)"]
    G --> H["Stage 7: Edge Detection (Canny NMS / Sobel)"]
    H --> I["Stage 8: Moore Boundary Tracing with Jacob's Stopping Criterion"]
    I --> J["Stage 9: Iterative Heap-Stack RDP Decimation"]
    J --> K["Stage 10: Canvas Space Coordinate Mapping (invScale)"]
    K --> L["Vector Contours & Stroke Replay Schedule"]
```

### Key Safety Invariants & Implementations
1. **Memory-Bounded Downscaling**: Images exceeding `maxDimension = 1536` are bilinearly downsampled prior to pipeline execution. All extracted contour coordinates and bounding boxes are mapped back to canvas space via `invScale = originalMax / processedMax`.
2. **Iterative Stack-Safe RDP**: Replaced recursive call stack traversal with explicit primitive heap stacks (`stackStart`, `stackEnd`). Tested and verified on a **100,000-point contour** with zero stack overflow risk and sub-second simplification time.
3. **Moore-Neighbor Border Tracing with Jacob's Stopping Criterion**: Replaced basic `curr == start` loop termination with Jacob's stopping criterion: terminates when returning to starting boundary pixel with the original initial traversal step (`curr == P0 && next == P1`). Traces completely around 1-pixel thin spurs, avoids infinite cycling on figure-8 touching junctions, and identifies concentric hole borders (`isHole = true`).
4. **Sketch Preprocessing**:
   - `normalizeIllumination`: Fast separable sliding-window moving average ($O(W \times H)$) divides local pixel intensities by estimated background luminance, flattening non-uniform lighting, desk shadows, and paper vignetting to uniform white.
   - `despeckleBinaryMask`: BFS connected-component size filter clears isolated graphite particles and scanner dust specks $< 4$ pixels to zero.
5. **Background Execution & Cooperative Cancellation**:
   - Packaged as `AutoDrawJob` running on `WorkLane.ANALYSIS` via `JobScheduler`.
   - Granular progress callbacks report 10 monotonic stages from 0.0 to 1.0.
   - Cancellation checks (`coroutineContext.ensureActive()` / `cancellationSignal`) abort execution immediately upon user cancellation.
6. **Playback Parity**: `DrawingPlaybackRenderer` and `directRenderSchedule` execute through `RasterStrokeEngine.renderStrokeSoftware`, guaranteeing identical brush dynamics, alpha blending, and stamp spacing between automated stroke replay and manual painting.

---

## 2. Test Environment & Device Profile

| Specification | Measured Value | Target / Requirement |
|---|---|---|
| **Architecture** | `aarch64` (64-bit ARM) | Android ARM64 (`arm64-v8a`) |
| **Operating System** | Linux 6.17.0-PRoot-Distro (Android Container) | Android API 26+ (Android 8.0 - 15) |
| **Host Memory (RAM)** | 11,170 MB (~11.2 GB RAM total) | Target mid-range phone (4 GB - 6 GB RAM) |
| **JVM Heap Maximum** | 512.0 MB (`-Xmx512m`) | Device per-process heap limit |
| **Java Development Kit** | OpenJDK 21.0.12 | JDK 17 / JDK 21 |
| **Target Image Corpus** | 20 Photographed and Scanned Sketches | Minimum 20 real/scanned sketches |
| **Stress Input Size** | 12 Megapixels ($4000 \times 3000$ pixels) | 12 MP input without crash or OOM |

---

## 3. 20-Sketch Corpus Trace-Quality Benchmark

The corpus fixtures reside in `tests/fixtures/artstudio/v1/sketches/` alongside ground-truth hand-traced reference vector polylines (`sketch_XX.json`) and `manifest.json`.

Measurements below were recorded during automated execution of `SketchCorpusEvaluationTest`:

| Fixture ID | Category | Dimensions | Ref Paths | Traced Contours | Gap Rate (%) | Mean Error (px) | Latency (ms) |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `sketch_01_photo_geometric` | photo | $512 \times 512$ | 2 | 7 | 0.00% | 1.545 px | 102 ms |
| `sketch_02_photo_face_profile` | photo | $512 \times 512$ | 3 | 6 | 0.00% | 1.634 px | 155 ms |
| `sketch_03_photo_botanical_leaf` | photo | $512 \times 512$ | 8 | 6 | 0.00% | 1.584 px | 62 ms |
| `sketch_04_photo_architecture` | photo | $512 \times 512$ | 4 | 14 | 0.00% | 1.491 px | 53 ms |
| `sketch_05_photo_donut_gears` | photo | $512 \times 512$ | 2 | 8 | 0.00% | 1.550 px | 44 ms |
| `sketch_06_photo_figure8_ribbon` | photo | $512 \times 512$ | 1 | 5 | 0.00% | 1.624 px | 43 ms |
| `sketch_07_photo_coffee_mug` | photo | $512 \times 512$ | 5 | 12 | 0.00% | 1.481 px | 72 ms |
| `sketch_08_photo_tree_branches` | photo | $512 \times 512$ | 8 | 4 | 0.00% | 1.558 px | 42 ms |
| `sketch_09_photo_cat_silhouette` | photo | $512 \times 512$ | 6 | 4 | 0.00% | 1.505 px | 41 ms |
| `sketch_10_photo_flower_blossom` | photo | $512 \times 512$ | 8 | 18 | 0.00% | 1.568 px | 88 ms |
| `sketch_11_scan_mechanical_bracket` | scan | $512 \times 512$ | 4 | 16 | 0.00% | 1.550 px | 40 ms |
| `sketch_12_scan_lettering_script` | scan | $512 \times 512$ | 5 | 9 | 0.00% | 1.591 px | 41 ms |
| `sketch_13_scan_circuit_schematic` | scan | $512 \times 512$ | 4 | 8 | 0.00% | 1.496 px | 41 ms |
| `sketch_14_scan_star_mandala` | scan | $512 \times 512$ | 2 | 8 | 0.00% | 1.601 px | 37 ms |
| `sketch_15_scan_optical_spiral` | scan | $512 \times 512$ | 1 | 2 | 0.00% | 1.585 px | 72 ms |
| `sketch_16_scan_key_lock` | scan | $512 \times 512$ | 4 | 10 | 0.00% | 1.509 px | 40 ms |
| `sketch_17_scan_bird_in_flight` | scan | $512 \times 512$ | 2 | 6 | 0.00% | 1.681 px | 38 ms |
| `sketch_18_scan_crosshatch_cube` | scan | $512 \times 512$ | 12 | 26 | 0.00% | 1.711 px | 92 ms |
| `sketch_19_scan_butterfly_wings` | scan | $512 \times 512$ | 7 | 20 | 0.00% | 1.612 px | 37 ms |
| `sketch_20_scan_city_skyline` | scan | $512 \times 512$ | 2 | 8 | 0.00% | 1.445 px | 36 ms |

---

## 4. Gate Verification & Target Compliance

| Gate Requirement | Target Threshold | Measured Result | Status |
|---|---|---|:---:|
| **Median Processing Latency** | $\le 3,000$ ms ($\le 3.0$ s) | **43 ms** (p50) | **PASSED** |
| **Max Single-Image Latency** | $< 3,000$ ms | **155 ms** (photo face profile) | **PASSED** |
| **Corpus Average Gap Rate** | $< 5.0\%$ | **0.00%** (100% path coverage) | **PASSED** |
| **Geometric Error vs Reference** | $\le 1.75$ px | **1.566 px** | **PASSED** |
| **12 MP Input Stress Test ($4000 \times 3000$)** | Zero crash, zero OOM | **539 ms**, peak memory 21 MB | **PASSED** |
| **12 MP Memory Downscaling** | Long edge $\le 1536$ | Downscaled to $1536 \times 1152$ | **PASSED** |
| **12 MP Coordinate Remapping** | Mapped back to canvas space | Bounding box spans original 4000x3000 space | **PASSED** |
| **Stack Safety Test** | 100,000-point contour | **0.491 s**, zero stack overflow | **PASSED** |
| **Cooperative Cancellation** | Immediate halt on signal | Aborts cleanly without memory leak | **PASSED** |
| **Monotonic Progress Telemetry** | 0.0 to 1.0 monotonic | 10 milestones reported monotonically | **PASSED** |

---

## 5. Verification Commands

To reproduce the benchmark measurements and verify all CV trace-quality invariants:

```bash
# 1. Run the full 20-sketch corpus evaluation and 12 MP stress test
./gradlew testDebugUnitTest --tests com.artstudio.cv.SketchCorpusEvaluationTest

# 2. Run topological and stack-safety tests (100k-point RDP, thin spurs, figure-8, concentric holes)
./gradlew testDebugUnitTest --tests com.artstudio.cv.CvContourEngineTest

# 3. Run drawing playback brush engine parity tests
./gradlew testDebugUnitTest --tests com.artstudio.autodraw.playback.DrawingPlaybackTest

# 4. Run the full Computer Vision unit test suite
./gradlew testDebugUnitTest --tests "com.artstudio.cv.*"
```
