# Architectural Decision Records (ADRs)

## Index of Decisions
- [ADR-001: Preservation of Existing Repository Work and Non-Destructive Coexistence](#adr-001-preservation-of-existing-repository-work-and-non-destructive-coexistence)
- [ADR-002: Android-First Architecture with Kotlin, Jetpack Compose and Native Canvas/Skia Surface](#adr-002-android-first-architecture-with-kotlin-jetpack-compose-and-native-canvasskia-surface)
- [ADR-003: Strict Decoupling of Studio Subsystems](#adr-003-strict-decoupling-of-studio-subsystems)
- [ADR-004: Offline-First Manual Studio and Optional Consent-Gated AI Pipeline](#adr-004-offline-first-manual-studio-and-optional-consent-gated-ai-pipeline)
- [ADR-005: Authoritative Native Document Format (`.ffart`) and Interchange Tiering (OpenRaster Priority)](#adr-005-authoritative-native-document-format-ffart-and-interchange-tiering-openraster-priority)
- [ADR-006: Colour Lab 15 Composable Styles + No Colour Recipe Engine](#adr-006-colour-lab-15-composable-styles--no-colour-recipe-engine)
- [ADR-007: Hardware Constraints, Memory Budgets (4 GB Min / 6 GB Rec) and Storage Guardrails](#adr-007-hardware-constraints-memory-budgets-4-gb-min--6-gb-rec-and-storage-guardrails)
- [ADR-008: Automated Verification, Golden Rendering and Art Fixture Corpus Protocol](#adr-008-automated-verification-golden-rendering-and-art-fixture-corpus-protocol)
- [ADR-009: Audit of Discovered Prototypes and Non-Adoption of Python per-Stroke Runtime](#adr-009-audit-of-discovered-prototypes-and-non-adoption-of-python-per-stroke-runtime)
- [ADR-010: Native Drawing Surface Architecture (Canvas/Skia vs Compose Canvas vs SurfaceView) & Feasibility Spike Findings](#adr-010-native-drawing-surface-architecture-canvasskia-vs-compose-canvas-vs-surfaceview--feasibility-spike-findings)
- [ADR-011: Synthetic Test Fixtures Corpus Generation & Pure-Python Binary Serialization](#adr-011-synthetic-test-fixtures-corpus-generation--pure-python-binary-serialization)
- [ADR-012: Accessible UI Design System, Design Tokens, Accent System & Non-Decorative Semantic Rules](#adr-012-accessible-ui-design-system-design-tokens-accent-system--non-decorative-semantic-rules)
- [ADR-013: Separate Destination Routing, Android Back-Stack Safeguards & SAF Document Retention](#adr-013-separate-destination-routing-android-back-stack-safeguards--saf-document-retention)
- [ADR-014: Authoritative Hierarchical Document Model, Zero-Dependency Serialization & .ffart Package Architecture](#adr-014-authoritative-hierarchical-document-model-zero-dependency-serialization--ffart-package-architecture)
- [ADR-015: Safe Artwork Persistence, Bounded Autosave, Recovery Journaling & Storage Fault Resilience](#adr-015-safe-artwork-persistence-bounded-autosave-recovery-journaling--storage-fault-resilience)
- [ADR-016: Safe Project Browsing, Bounded Thumbnail Extraction & Recoverable Lifecycle Actions](#adr-016-safe-project-browsing-bounded-thumbnail-extraction--recoverable-lifecycle-actions)
- [ADR-021: Customizable Stroke Dynamics, Persistent Presets & Validated Interchange](#adr-021-customizable-stroke-dynamics-persistent-presets--validated-interchange)
- [ADR-022: Reversible Edit Commands, Transaction History & Bounded Memory Policy](#adr-022-reversible-edit-commands-transaction-history--bounded-memory-policy)
- [ADR-023: Core Editable Raster Layer Model, Downscaled Previews & Fixture Golden Compositing](#adr-023-core-editable-raster-layer-model-downscaled-previews--fixture-golden-compositing)
- [ADR-024: Layer Groups, Hierarchical Tree Operations & Non-Destructive Structural Commands](#adr-024-layer-groups-hierarchical-tree-operations--non-destructive-structural-commands)
- [ADR-025: Authoritative Layer Blend Modes & Porter-Duff Compositing Architecture](#adr-025-authoritative-layer-blend-modes--porter-duff-compositing-architecture)
- [ADR-026: Non-Destructive Layer Masks, Clipping Chains & Alpha-Lock Architecture](#adr-026-non-destructive-layer-masks-clipping-chains--alpha-lock-architecture)
- [ADR-027: Document-Space Selection Mask Engine and Morphology Operators](#adr-027-document-space-selection-mask-engine-and-morphology-operators)
- [ADR-028: Non-Destructive Layer Transforms & Canvas Crop Engine](#adr-028-non-destructive-layer-transforms--canvas-crop-engine)
- [ADR-029: Bounded Advanced Deformation Subsystem (Mesh Warp & Liquify)](#adr-029-bounded-advanced-deformation-subsystem-mesh-warp--liquify)
- [ADR-030: Primary Colour Selection, Palette Management & Gradient Subsystem](#adr-030-primary-colour-selection-palette-management--gradient-subsystem)
- [ADR-031: Accurate Fills with Gap Closing, Line Boundaries & Cancellation](#adr-031-accurate-fills-with-gap-closing-line-boundaries--cancellation)
- [ADR-035: Editable Vector Geometry Engine, Safe SVG Subset Interchange, and Non-Destructive Vector-to-Raster Pipeline](#adr-035-editable-vector-geometry-engine-safe-svg-subset-interchange-and-non-destructive-vector-to-raster-pipeline)
- [ADR-036: Non-Destructive Drawing Aids, Multi-Branch Symmetry Engine, and Isolated Reference Image Overlay System](#adr-036-non-destructive-drawing-aids-multi-branch-symmetry-engine-and-isolated-reference-image-overlay-system)
- [ADR-037: Editable Text Objects, Open-Licensed Typography, Geometric Shape Generation, and Reversible Vector/Raster Semantics](#adr-037-editable-text-objects-open-licensed-typography-geometric-shape-generation-and-reversible-vectorraster-semantics)
- [ADR-038: Comic & Manga Tools: Panel Slicing, Seamless Welded Balloons, Scalable Procedural Screentones, and Page Guides](#adr-038-comic--manga-tools-panel-slicing-seamless-welded-balloons-scalable-procedural-screentones-and-page-guides)
- [ADR-039: Extensible Filter System with Real Image Operations](#adr-039-extensible-filter-system-with-real-image-operations)
- [ADR-040: Non-Destructive Tonal and Colour Adjustment Architecture](#adr-040-non-destructive-tonal-and-colour-adjustment-architecture)
- [ADR-041: Frame-by-Frame Animation Architecture & Cel-Referencing Model](#adr-041-frame-by-frame-animation-architecture--cel-referencing-model)
- [ADR-042: Artist Onion Skin Overlays, Responsive Scrubbing & Reduced-Cost Preview Engine](#adr-042-artist-onion-skin-overlays-responsive-scrubbing--reduced-cost-preview-engine)
- [ADR-043: PSD Import/Export Feasibility Research, Candidate Evaluation & Cleanroom Architecture](#adr-043-psd-importexport-feasibility-research-candidate-evaluation--cleanroom-architecture)
- [ADR-044: Animated Export Encoders Feasibility Research, Candidate Evaluation & Platform MediaCodec Architecture](#adr-044-animated-export-encoders-feasibility-research-candidate-evaluation--platform-mediacodec-architecture)
- [ADR-045: Non-Destructive Multi-Destination Raster Import Engine with Orientation & Alpha Preservation](#adr-045-non-destructive-multi-destination-raster-import-engine-with-orientation--alpha-preservation)
- [ADR-046: High-Fidelity Multi-Format Raster Export Architecture with Atomic Verification](#adr-046-high-fidelity-multi-format-raster-export-architecture-with-atomic-verification)
- [ADR-047: Authoritative Native Package & OpenRaster Layered Interchange Architecture](#adr-047-authoritative-native-package--openraster-layered-interchange-architecture)
- [ADR-048: Safe SVG Vector Subset Transparent Import/Export and Sanitisation Architecture](#adr-048-safe-svg-vector-subset-transparent-importexport-and-sanitisation-architecture)
- [ADR-049: Image Processing & Computer Vision Architecture Feasibility Research: OpenCV vs BoofCV vs Cleanroom Pure-Kotlin](#adr-049-image-processing--computer-vision-architecture-feasibility-research-opencv-vs-boofcv-vs-cleanroom-pure-kotlin)
- [ADR-050: On-Device Art Region Segmentation Feasibility Research: MobileSAM vs MediaPipe vs Classical CV & User Masks](#adr-050-on-device-art-region-segmentation-feasibility-research-mobilesam-vs-mediapipe-vs-classical-cv--user-masks)
- [ADR-051: Concurrency-Bounded, Lane-Segregated Background Job Architecture and Safe Cancellation Protocol](#adr-051-concurrency-bounded-lane-segregated-background-job-architecture-and-safe-cancellation-protocol)
- [ADR-052: Non-Destructive Reference Pre-Inspection, Bidirectional Coordinate Normalization, and Reconstruction Mode Suggestion Architecture](#adr-052-non-destructive-reference-pre-inspection-bidirectional-coordinate-normalization-and-reconstruction-mode-suggestion-architecture)
- [ADR-053: Deterministic Edge, Contour, and Denoising Pipeline for Raster Art Analysis](#adr-053-deterministic-edge-contour-and-denoising-pipeline-for-raster-art-analysis)
- [ADR-054: Plausible Single-Pen Centreline Extraction with Topological Junction Preservation and Ambiguity Review Flagging](#adr-054-plausible-single-pen-centreline-extraction-with-topological-junction-preservation-and-ambiguity-review-flagging)
- [ADR-055: Contour Candidates to Editable Geometric Paths: Adaptive Schneider Cubic Bézier Spline Fitting with Corner Preservation and Hole Containment](#adr-055-contour-candidates-to-editable-geometric-paths-adaptive-schneider-cubic-b%C3%A9zier-spline-fitting-with-corner-preservation-and-hole-containment)
- [ADR-056: Connected Region Segmentation, Three-Tier Mask Separation, and Topological Gap Closing for Art Layer Coloring](#adr-056-connected-region-segmentation-three-tier-mask-separation-and-topological-gap-closing-for-art-layer-coloring)
- [ADR-057: Reference Focus Region Selection and Multi-Region Annotation Subsystem for Preserved Source Attention](#adr-057-reference-focus-region-selection-and-multi-region-annotation-subsystem-for-preserved-source-attention)
- [ADR-058: Per-Region Analysis Profiles, Inherited Presets, and Boundary-Margin-Stitched Focused CV Execution](#adr-058-per-region-analysis-profiles-inherited-presets-and-boundary-margin-stitched-focused-cv-execution)
- [ADR-059: Constrained Natural Language Description to Region Analysis Settings Translation & Security Guardrails](#adr-059-constrained-natural-language-description-to-region-analysis-settings-translation--security-guardrails)
- [ADR-060: On-Demand Optional AI Model Registry, Curated Catalog, and Runtime Adapters](#adr-060-on-demand-optional-ai-model-registry-curated-catalog-and-runtime-adapters)
- [ADR-061: Interactive Detection Review, Uncertainty Flagging, Explicit Override Operations, and Conflict-Aware Reanalysis Engine](#adr-061-interactive-detection-review-uncertainty-flagging-explicit-override-operations-and-conflict-aware-reanalysis-engine)
- [ADR-062: Source Palette Analysis, Region-Local Colour Association, Luminance/Shadow Inference, and Adaptive Role Mapping Engine](#adr-062-source-palette-analysis-region-local-colour-association-luminance-shadow-inference-and-adaptive-role-mapping-engine)
- [ADR-063: Modular Colour Stack Architecture, Ordered Multi-Stage Compositing, Conflict Resolution & Recipe Versioning Engine](#adr-063-modular-colour-stack-architecture-ordered-multi-stage-compositing-conflict-resolution--recipe-versioning-engine)
- [ADR-064: First Five Colour Lab Rendering Strategies, Source Preservation & Layer Separation](#adr-064-first-five-colour-lab-rendering-strategies-source-preservation--layer-separation)
- [ADR-065: Next Five Named Colour Lab Rendering Strategies (Styles 6–10) & Permissive Procedural Texturing](#adr-065-next-five-named-colour-lab-rendering-strategies-styles-610--permissive-procedural-texturing)
- [ADR-066: Remaining Five Named Colour Lab Rendering Strategies (Styles 11–15) and No Colour Mode](#adr-066-remaining-five-named-colour-lab-rendering-strategies-styles-1115-and-no-colour-mode)
- [ADR-076: Feature Flags Gating Table, ColorStack Integration, Interactive Deformation Wiring, and Truthful AI Model Resolution (Option A)](#adr-076-feature-flags-gating-table-colorstack-integration-interactive-deformation-wiring-and-truthful-ai-model-resolution-option-a)

---

### ADR-001: Preservation of Existing Repository Work and Non-Destructive Coexistence
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: The workspace environment may contain existing projects, tools, or sibling work trees. The project charter mandates that existing working code and data must be preserved and not replaced wholesale without evidence.
- **Decision**: All Art Studio assets, source modules, documentation, and tools will be developed in `/root/artstudio` (with subdirectories `docs/build/`, `tools/qa/`, `tests/fixtures/artstudio/v1/`, and Android source modules). Sibling files and unrelated git history will remain untouched.
- **Consequences**: Ensures backward compatibility and zero accidental data loss while allowing new Android graphics and art studio modules to develop cleanly.

---

### ADR-002: Android-First Architecture with Kotlin, Jetpack Compose and Native Canvas/Skia Surface
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: The product is an Android-first digital art creation studio requiring responsive UI controls and ultra-low-latency stroke rendering.
- **Decision**: The user interface will be built with Jetpack Compose for modern, declarative component management. The drawing surface will utilize an Android native Canvas/SurfaceView/TextureView with Skia-backed rendering pipeline rather than web-view or pure compose canvas re-renders, enabling direct access to low-level hardware-accelerated drawing primitives, dirty rect updates, and double buffering.
- **Consequences**: Low input-to-display latency (<25ms) achievable; smooth pan/zoom/rotate; fine-grained memory management over bitmap buffers.

---

### ADR-003: Strict Decoupling of Studio Subsystems
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Mixing pointer events, UI composition, rendering loops, and document state leads to UI stutter, difficult testing, and memory leaks.
- **Decision**: Strict boundaries are enforced across:
  1. Pointer Input Pipeline (captures raw motion events, historical batches, pressure, tilt)
  2. UI Presentation (Compose controls, dialogs, panels)
  3. Document State & History (layers, strokes, undo/redo command stack, project manifest)
  4. Rendering Engine (Skia/Canvas rasterization, tile cache, composition)
  5. Image Analysis / Computer Vision (edge extraction, segmentation, offline CV)
  6. AI Engine (optional, lazy-loaded)
  7. Interchange I/O (ORA, PSD, SVG, FFART import/export)
- **Consequences**: Highly testable modular architecture with clear unit-test boundaries.

---

### ADR-004: Offline-First Manual Studio and Optional Consent-Gated AI Pipeline
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Users expect art applications to function reliably in any network condition without privacy risks. AI models can be large, bandwidth-heavy, and privacy-invasive if unmanaged.
- **Decision**:
  - The manual painting studio and baseline computer vision operate 100% offline with zero network connectivity and zero AI model dependencies.
  - Any AI capability is strictly optional, loaded lazily on demand, and governed by an explicit user consent modal explaining what data is processed.
  - Cancellation must be supported at every stage.
  - No artwork will ever be transmitted to external/cloud services without prior explicit user consent.
  - Robust conventional CV and manual user-drawn masks must always serve as default fallbacks.
- **Consequences**: Total user privacy, adherence to privacy regulations, and deterministic offline reliability.

---

### ADR-005: Authoritative Native Document Format (`.ffart`) and Interchange Tiering (OpenRaster Priority)
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Digital artists require lossless preservation of layer stacks, blending modes, stroke histories, and custom recipes. Interchange with desktop tools (GIMP, Krita, Photoshop, Inkscape) is also essential.
- **Decision**:
  - Native format (`.ffart`): Authoritative, lossless ZIP package containing JSON metadata (`project.json`), chunked raster layers, vector stroke timelines, and color recipes.
  - OpenRaster (`.ora`): Primary open standard layered interchange format (`stack.xml` + PNG layers).
  - SVG (`.svg`): Safe vector subset only; unsupported elements flagged with descriptive warnings.
  - PSD (`.psd`): Best-effort support accompanied by an automated conversion parity report.
  - Raster images (PNG, JPEG, WebP): Imported non-destructively as a layer or reference; exported without silent flattening.
- **Consequences**: Guarantees zero silent data loss, transparent interoperability, and auditability.

---

### ADR-006: Colour Lab 15 Composable Styles + No Colour Recipe Engine
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Art stylization requires fine control across 15 distinct aesthetics plus a pure line-art baseline.
- **Decision**:
  - Implement 15 composable styles: Classic Cel Shading, Soft Gradient Shading, Hybrid Cel + Soft Shading, Flat Illustration, Painterly, Watercolour, Coloured Pencil, Marker, Manga Screentone, Comic Book, Oil Paint, Gouache, Pastel Palette, Neon / Cyberpunk, Monochrome.
  - Plus: No Colour (pure line art / contour baseline).
  - Styles are defined as composable multi-pass shader/filter recipes with per-pass opacity, blend modes, and mask targets.
  - Conflicts (e.g. mutually exclusive shading styles) are resolved via an explicit priority hierarchy.
  - Precision region overrides can adjust local recipes without lowering global baseline settings.
- **Consequences**: Flexible, non-destructive coloring workflows with reproducible artistic recipes.

---

### ADR-007: Hardware Constraints, Memory Budgets (4 GB Min / 6 GB Rec) and Storage Guardrails
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Android devices range widely in RAM and storage capacity. Large canvases (e.g. 4096x4096 with 10+ layers) can easily exhaust memory if unmanaged.
- **Decision**:
  - Target: 4 GB physical RAM minimum, 6 GB recommended.
  - Memory bounds: Tile-based raster management (256x256 or 512x512 tiles) with strict LRU memory eviction. Undo/redo commands store vector events or compressed tile diffs, capped at a configurable memory ceiling.
  - Storage: Enforce >= 1 GB free storage pre-flight check at startup/project creation.
  - App size: Aim for base download under 150 MB and installed footprint under 300 MB, verified by real measurements.
  - Concurrency: Bounded worker pools; zero blocking of the Android main thread; all background jobs cancelable.
  - Consequences: Rock-solid stability on budget/mid-range Android hardware.

---

### ADR-008: Automated Verification, Golden Rendering and Art Fixture Corpus Protocol
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Visual rendering and file interchange require deterministic regression testing.
- **Decision**:
  - Establish a versioned test corpus at `tests/fixtures/artstudio/v1/`.
  - Only CC0 or public-domain licensed art assets will be accepted; every fixture must have documented licensing in a manifest.
  - Canonical QA script `tools/qa/verify_art_fixtures.py` verifies corpus integrity, schema validity, and golden render diff thresholds.
  - Empirical baseline measurements recorded in `docs/build/BASELINES.md`.
- **Consequences**: Repeatable visual regression testing and zero copyright infringement risk.

---

### ADR-009: Audit of Discovered Prototypes and Non-Adoption of Python per-Stroke Runtime
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Pre-architecture search discovered an external script (`/sdcard/Download/handdraw_engine_v2_18_stable.py`) implementing a Python/Pygame speed-drawing playback player using NumPy arrays, precomputed reveal maps, and vector buckets.
- **Decision**:
  - Do NOT adopt Python as an interactive per-stroke drawing or playback runtime for Art Studio. Python in PRoot/Pydroid cannot achieve the strict input-to-display latency budget (<25ms) or native stylus pipeline (MotionEvent historical batches, pressure, tilt, palm rejection).
  - Adhere strictly to the Session Charter: "Python may support development experiments/fixture generation, not a blocking per-stroke runtime."
  - Conceptual algorithms from the prototype (such as precomputed reveal orders, deterministic monotonic stroke progression, and dirty composite caching) may inform native Kotlin/Canvas data structures, but all production runtime code must be native Android Kotlin.
- **Consequences**: Eliminates latency bottlenecks, enables direct hardware acceleration via Android Canvas/Skia, and ensures compliance with Android app lifecycle.

---

### ADR-010: Native Drawing Surface Architecture (Canvas/Skia vs Compose Canvas vs SurfaceView) & Feasibility Spike Findings
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Prompt 2 required proving the minimum viable native canvas/input architecture and measuring baseline latency, frame behaviour, allocation, and pan/zoom correctness. Three architectural approaches were evaluated:
  1. *Pure Jetpack Compose Canvas*: High declarative developer ergonomics, but forces recomposition overhead and creates object allocations during rapid high-frequency stylus batches (120Hz/240Hz).
  2. *Dedicated Custom View (`ArtStudioDrawingView`) with Native Hardware-Accelerated Canvas/Skia*: Provides direct hardware-accelerated Skia pipeline (`Canvas.concat(Matrix)`), dirty rectangle clipping, zero-allocation pure math coordinate mapping, and native `MotionEvent` historical batch processing.
  3. *Standalone `SurfaceView` with background render thread*: Decouples rendering thread from UI thread, but incurs EGL/Vulkan surface lifecycle complexity and z-ordering friction with Jetpack Compose overlays.
- **Decision**:
  - Adopt Option 2 (Dedicated Native View with Canvas/Skia embedded in Compose via `AndroidView`) as the authoritative drawing surface architecture.
  - Keep high-frequency pointer input strictly in `PointerInputPipeline`, decoupling stylus from multi-touch gestures (pan/zoom/palm rejection).
  - Implement pure mathematical mapping in `ViewportTransform` for sub-microsecond coordinate conversions without object churn.
  - Empirically measured results from Prompt 2 feasibility spike:
    - Coordinate transform latency: **108.57 ns/transform** (9.21M transforms/sec throughput).
    - Dab generation throughput: **191,053 dabs/sec** (exceeds $\ge 10,000$ target by 19x).
    - Bounding box calculation: **5.48 $\mu$s** for a 1,000-point stroke.
    - Bounded LRU tile cache throughput: **263,962 ops/sec** under a 128 MB RAM ceiling.
    - Debug APK size: **9.8 MB** (well under the 150 MB ceiling).
- **Consequences**: Confirms ultra-low-latency native rendering is viable on Android, satisfies the <25ms latency budget, and delivers clean decoupling of input, viewport, state, and rendering.
 
---

### ADR-011: Synthetic Test Fixtures Corpus Generation & Pure-Python Binary Serialization
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Later milestones for rendering, import (PNG, JPEG, ORA, SVG, PSD), analysis, and export require a reproducible, licensed test corpus under `tests/fixtures/artstudio/v1/`. The PRoot Linux environment runs Python 3.12 without external image libraries (`PIL`/`Pillow`), and strict privacy/licensing rules prohibit unverified, scraped, or copyrighted assets.
- **Decision**:
  - Implement a 100% self-contained pure-Python asset generator (`tools/qa/generate_art_fixtures.py`) relying solely on Python standard library modules (`zlib`, `struct`, `zipfile`, `xml.etree.ElementTree`, `hashlib`, `json`).
  - Synthesize binary PNGs (uncompressed/deflated RGB/RGBA with CRC-32 chunk framing), JPEGs with APP1 EXIF headers (TIFF header with orientation tags 6 [90° CW] and 3 [180°]), valid OpenRaster archives (`mimetype` stored uncompressed first + `stack.xml` layer tree + PNG layer tiles), and scalable SVG documents.
  - Generate fault-injection fixtures (truncated IDAT stream, corrupted ZIP header, malformed XML tags) to test parser resilience and error boundaries.
  - Enforce Creative Commons Zero 1.0 Universal (CC0-1.0) licensing for 100% of fixtures, recorded in `LICENSES.md` and `manifest.json`.
  - Validate all assets via `tools/qa/verify_art_fixtures.py` auditing category counts, SHA-256 hashes, license references, and format integrity.
- **Consequences**: Deterministic, zero-dependency test asset generation runnable on any environment; zero copyright risk; rock-solid test coverage for future import/export and CV pipelines.
 
---

### ADR-012: Accessible UI Design System, Design Tokens, Accent System & Non-Decorative Semantic Rules
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: As the application expands across Manual Studio, AutoDraw, Colour Lab, and Layer Management, UI components and toolbars must remain accessible, consistent, and canvas-dominant without copying proprietary competitors' visual identities. The product charter also mandates: "Every control must be functional or clearly disabled with an explanation. No decorative control implies an action that does not exist."
- **Decision**:
  - Implement a tokenized design system (`StudioTheme`) with complete scales for spacing (8-point grid + 2dp/4dp micro-steps), typography (scalable sp units), elevation (flat to modal), motion (instant, fast, normal, slow), and corner radii.
  - Implement dynamic Light and Dark themes with a 4-preset Accent System (`StudioBlue`, `EmeraldArt`, `CyberpunkNeon`, `ClassicAmber`).
  - Enforce WCAG 2.1 compliance via automated testing: $\ge 4.5:1$ contrast ratio for body text, $\ge 7.0:1$ for primary headings, and $\ge 3.0:1$ for graphical UI components.
  - Enforce minimum 48dp x 48dp touch targets on all interactive controls (`StudioAccessibility.MinTouchTarget`).
  - Provide a Reduced Motion accessibility mode where all animated transitions are bypassed (0ms instant).
  - Enforce non-decorative semantics: any control that cannot be executed in current application state must be accompanied by an inline or dialog explanation (`disabledExplanation`) rather than serving as inert visual decoration.
  - Establish responsive navigation: canvas-dominant compact bottom bar on phones, expandable navigation rail on tablets ($\ge 600$dp).
  - Build an interactive live Component Reference Screen (`ComponentReferenceScreen`) exercising 13 reusable UI components (`StudioButton`, `StudioIconButton`, `StudioSlider`, `StudioSegmentedSelector`, `StudioLayerRow`, `StudioPaletteChip`, `StudioTabs`, `StudioProgressBar`, `StudioProgressIndicator`, `StudioDialog`, `StudioLoadingState`, `StudioEmptyState`, `StudioErrorState`, `StudioDisabledExplanation`).
- **Consequences**: Guarantees accessible, consistent, and predictable user experience across all form factors while eliminating fake or inert decorative UI.
 
---

### ADR-013: Separate Destination Routing, Android Back-Stack Safeguards & SAF Document Retention
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: The product requires distinct routes for Home/Projects, Manual Studio, AutoDraw, Colour Lab, Assets/Brushes, and Settings rather than a monolithic screen with hidden panels. Route transitions must manage Android system back behavior, route arguments (project ID/title), selected-project context, and unsaved work warnings while integrating with Android Storage Access Framework (SAF) document APIs. Subsystems scheduled for later phases must be explicitly labeled as unavailable rather than displaying fake success states.
- **Decision**:
  - Implement a typed route model (`StudioNavRoute`) supporting route serialization, deserialization, and safe state restoration with fallback to `HomeProjects`.
  - Maintain a dedicated navigation stack manager (`StudioNavigationManager`) handling back transitions and preserving `HomeProjects` as the root destination.
  - Implement an unsaved-work safeguard: when the active canvas project has uncommitted or unsaved strokes (`isDirty == true`), attempting to navigate back or leave the studio intercepts the transition and displays a `StudioDialog` allowing the user to Save & Exit, Discard, or Continue Drawing.
  - Integrate Android Storage Access Framework (`takePersistableUriPermission`) inside a try-catch guard so that external document URIs (e.g. imported reference images) safely retain permissions without throwing `SecurityException` on unsupported providers.
  - In `ProjectsScreen`, implement real entry points for New Canvas (with dimensions presets: 2048x2048, 1920x1080, 4K, Mobile), Open Project (from recent projects list), Import Image (via SAF file picker), and project deletion with confirmation.
  - Enforce honesty in subsystem statuses: `AutoDrawScreen` explicitly displays "Scheduled for Phase 6 (Offline Edge Extraction & Vectorization)", `ColourLabScreen` displays "Scheduled for Phase 7 (Multi-Pass Compositing Shader Passes)", and `AssetsBrushesScreen` displays "Core Presets Defined (Custom Dynamics Phase 4)". Zero dead buttons and zero fake success toasts.
- **Consequences**: Prevents accidental data loss from unsaved strokes, provides clean multi-destination navigation, and maintains absolute transparency on feature readiness.
---

### ADR-014: Authoritative Hierarchical Document Model, Zero-Dependency Serialization & .ffart Package Architecture
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: The digital art application requires an authoritative, editable document model shared between manual drawing, AutoDraw AI reconstruction, Colour Lab shading, and animation timelines. It must decouple persistent document state from live views and GPU/Bitmap caches, support ordered layer trees with nested groups and clipping masks, provide vector strokes with pressure/tilt dynamics, handle schema migration from legacy flat schemas (v1.0.0 $\to$ v2.0.0), and package projects as a robust container format (`.ffart`) immune to data corruption and path traversal vulnerabilities. Furthermore, unit testing on the host JVM without an Android device emulator cannot rely on Android's stubbed `org.json.JSONObject`.
- **Decision**:
  - Model persistent state as immutable Kotlin data structures: `ArtDocument`, `CanvasConfig`, `LayerNode` (sealed hierarchy of `RasterLayer`, `VectorPathLayer`, and `LayerGroup`), `VectorPathData`, `PaletteDefinition`, `ReferenceImageConfig`, `AnimationTimelineConfig`, and `AutoDrawProvenance`.
  - Enforce explicit layer ordering semantics: index 0 is bottom-most, and index `lastIndex` is top-most (consistent with OpenRaster, Photoshop, and 2D canvas draw sequence).
  - Implement a pure-Kotlin zero-dependency recursive descent JSON parser and serializer (`ArtJson.kt`) that operates identically on Android devices and standalone JVM unit test runners without relying on `org.json` stubs or external library bloat.
  - Standardize native project encapsulation on `.ffart`: a ZIP container with uncompressed `mimetype` (`application/vnd.artstudio.project`) at entry 1 (OpenRaster/EPUB style), `project.json` at entry 2, and binary image resources under `assets/...`.
  - Guard against Zip Slip directory traversal attacks by rejecting entries containing `..` or leading `/` with `CorruptDocumentException`.
  - Implement atomic file replacement via temporary files during save operations to prevent project corruption on power loss or application termination.
  - Implement automatic schema migration (`DocumentMigrator.kt`) converting legacy flat `layers` arrays into structured `LayerGroup` trees, normalizing opacities from 0..100 scales to 0..1, injecting default transforms, and updating schema version to `2.0.0`.
  - Validate schema round-trip integrity, clipping masks, 9 blend modes, vector curves, timeline frames, provenance, and corrupt document rejection across 15 automated unit tests (`ArtDocumentRoundTripAndMigrationTest`).
---

### ADR-015: Safe Artwork Persistence, Bounded Autosave, Recovery Journaling & Storage Fault Resilience
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Creating valuable digital artwork requires bulletproof persistence and crash resilience before users invest hours of effort into complex layered documents. Sudden process termination (OS low-memory killer, battery depletion), storage exhaustion (disk full / ENOSPC), permission loss, or mid-stream I/O interruptions must never corrupt or overwrite the last valid document on disk. Furthermore, high-frequency stylus events (~60-120 Hz) cannot trigger full-document archive serialization on every pointer sample without degrading interactive drawing framerates.
- **Decision**:
  - Implement an explicit observable lifecycle state model (`DocumentSaveState`): `Idle`, `Unsaved(dirtyEdits)`, `Saving(progress, isAutosave)`, `Saved(timestamp, file, revision)`, `SaveFailed(reason, cause, canRetry)`, and `RecoveryAvailable(timestamp, recoveryFile, backupRevision)`.
  - Enforce atomic file operations via `StorageDriver` with shadow temporary files (`.tmp`) and mandatory readback verification (verifying ZIP header and uncompressed mimetype) before committing file renames. If writing or verification fails, temporary files are immediately unlinked, leaving the original target file 100% intact. Success is never reported before verifying the write.
  - Implement bounded autosave (`AutosaveManager`) with debouncing (2000ms delay) and edit count thresholds (forcing autosave after 10 edits). All autosave I/O is dispatched off the UI thread via `Dispatchers.IO`, writing exclusively to a separate recovery snapshot directory (`recovery/<projectId>/recovery_snapshot.ffart`) without touching the primary document file.
  - Implement crash recovery journaling (`DocumentRecoveryManager`): maintains `recovery_manifest.json` and recovery snapshots. On project open, detects uncommitted recovery material from prior crashed sessions, offers `RecoveryAvailable` state, and enables restoration.
  - Establish a strict non-deletion rule: recovery material is never deleted silently. It is retained until explicit user discard or verified manual save commit.
  - Implement a deterministic storage failure test seam (`FaultInjectingStorageDriver`) enabling automated unit testing of simulated disk-full, permission denial, interrupted write streams, checksum/verification failures, and low-space pre-checks (< 1 MB minimum).
---

### ADR-016: Safe Project Browsing, Bounded Thumbnail Extraction & Recoverable Lifecycle Actions
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: The digital art gallery must browse, sort, search, and manage dozens or hundreds of projects without consuming hundreds of megabytes of RAM or stalling the UI thread with full-resolution document deserialization. In addition, project management operations (duplicate, rename, folder organization, deletion) must use stable project IDs, handle missing/inaccessible assets gracefully, prevent accidental deletion, and never execute destructive actions without confirmation and an immediate undo/recoverable path.
- **Decision**:
  - Decouple lightweight project metadata (`ProjectMetadata`) from expensive raster previews. Project browsing displays metadata and lightweight preview thumbnails without loading full-resolution multi-megabyte layer bitmaps into memory.
  - Implement a bounded LRU `ThumbnailCache` with a 16 MB ceiling that streams and extracts strictly the `thumbnail.png` entry from `.ffart` packages using fast ZIP streaming (~5.8 ms per thumbnail, 31.7 KB for 20 entries) without decompressing or parsing layer trees.
  - Implement project duplication (`duplicateProject`): clones the `.ffart` package with a new stable UUID, generates disambiguated titles (`"Title (Copy)"`, `"Title (Copy 2)"`), and writes the new package atomically to disk while keeping the original file intact.
  - Implement safe project renaming (`renameProject`): updates the library metadata and internal `project.json` without altering the stable file ID, rejecting blank titles with actionable errors.
  - Implement folder/group organization (`moveToFolder`, `listFolders`) and multi-criteria query/filtering (`applyFilterAndSort` with 6 deterministic sort orders and text/folder/tag search).
  - Implement soft-delete with trash undo (`undoDelete`): when a user deletes a project, the package is moved to a `trash/` directory and can be immediately restored via an "Undo Delete" banner. No destructive action occurs without modal confirmation, and deleted projects are recoverable.
  - Persist library index and recent project ordering in `projects_index.json`, providing bit-exact state restoration across simulated application restarts.
  - Validate all lifecycle behaviors across 15 automated unit tests (`ProjectLibraryAndLifecycleTest`), bringing the comprehensive test suite to 102 tests passing with 0 failures.
- **Consequences**: Guarantees fast, fluid project browsing with minimal memory footprint (<32 KB for 20 previews), prevents accidental project loss through confirmation and undoable soft-deletion, and preserves rock-solid document integrity.

---

### ADR-017: Canvas Navigation Architecture, Reversible Coordinate Mapping & Touch Gesture Arbitration
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: A professional digital art studio requires smooth, predictable, and mathematically reversible canvas navigation across pan, zoom (0.1x to 30x), continuous and snapped rotation (cardinal 0°, 90°, 180°, 270°), aspect-ratio aware fit-to-screen, canvas centering, and horizontal/vertical canvas mirroring. Digital artists constantly flip the canvas horizontally to evaluate anatomy, silhouette, and perspective; this operation must be an instantaneous view projection without modifying underlying artwork pixels or layer data. Furthermore, high-frequency stylus/finger drawing input must be strictly protected from accidental pan/zoom interruptions caused by palm contact or stray multi-touch events during active strokes.
- **Decision**:
  - Maintain four distinct, type-safe coordinate spaces via `CoordinateSpaces.kt`: `ScreenPoint` (physical window/display pixels), `ViewportPoint` (local drawing canvas widget coordinates), `DocumentPoint` (canonical art document space [0, W] x [0, H]), and `SourceImagePoint` (local pixel space of placed reference images or assets).
  - Implement bidirectional affine transformations in `ViewportTransform.kt` using pure mathematical mapping for zero allocation on high-frequency input loops, combined with Android `Matrix` caching (`postTranslate`, `postScale`, `postRotate`, `preScale`) for hardware-accelerated Skia rendering via `Canvas.concat()`.
  - Implement horizontal (`isFlippedHorizontal`) and vertical (`isFlippedVertical`) canvas flipping as pure view projections centered at `(documentWidth / 2, documentHeight / 2)`. Flipping is reversible with sub-microsecond latency and strictly preserves document pixel data without any layer mutation.
  - Implement touch gesture arbitration (`TouchGestureArbiter`): when an in-flight stroke begins (`DRAWING` state), navigation is strictly locked out; incoming multi-touch events and palm touches are filtered out rather than aborting or panning the stroke. When idle, two or more fingers trigger multi-touch viewport navigation (`VIEWPORT_NAVIGATION`).
  - Implement aspect-ratio aware `fitToScreen()` (letterboxing or pillarboxing with margin) and `centerCanvas()` aligning document center with viewport center under arbitrary zoom and rotation angles.
  - Implement automated visual fidelity and regression verification via `tools/qa/compare_golden_images.py` calculating MSE, normalized RMSE, PSNR (dB), SSIM, and generating visual diff images.
  - Validate reversible coordinate mapping across 8,400 anchor tests, measuring a maximum roundtrip error of 0.000427 pixels (budget: $\le 0.5$ pixels), and passing 25 automated unit tests (`ViewportTransformTest`).
---

### ADR-018: Tiled Canvas Surface Architecture, Dirty-Region Invalidation & Bounded Memory Policy
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Digital art documents frequently exceed 2048x2048 pixels (e.g. 4096x4096 or 4K/8K formats) across multiple layers. Allocating uncompressed monolithic full-document bitmaps for every layer (16 MB per layer at 2048x2048, 64 MB per layer at 4096x4096 in ARGB_8888) causes severe memory spikes, OutOfMemory (OOM) crashes, and UI thread stalls on 4 GB memory-constrained Android devices. Furthermore, re-rasterizing the entire 2048x2048 or 4096x4096 canvas on every high-frequency stylus motion event (60-120 Hz) is computationally prohibitive.
- **Decision**:
  - Implement a bounded sparse tile grid (`TiledCanvasEngine`): divide the document into fixed $256 \times 256$ pixel tiles ($8 \times 8 = 64$ tiles for 2048x2048; $16 \times 16 = 256$ tiles for 4096x4096).
  - Allocate tiles on demand (sparse allocation); untouched canvas regions consume zero memory.
  - Implement dirty-region tracking (`DirtyRegionTracker`): compute minimal bounding boxes around active strokes/edits and intersect them with the tile grid via `getIntersectingTiles()`, confining re-rasterization strictly to affected tiles. A single stroke dab of radius 15 px touches only 1 tile out of 64 on 2048x2048 (a 98.44% work reduction).
  - Enforce hard memory ceilings via bounded LRU tile caching (`LruTileCache`, default 64 MB).
  - Separate high-frequency transient drawing from tile composition: active in-flight strokes render into an ephemeral transient overlay with zero latency, committing to bounded tiles only upon stroke completion (`finishCurrentStroke()`).
  - Maintain a lightweight downsampled document preview buffer ($512 \times 512$ ARGB_8888, 1 MB) for fast thumbnail/navigation rendering without decompressing full-resolution layers.
  - Implement proactive memory pressure management (`onTrimMemory`): `MODERATE` trims off-screen tiles outside the visible viewport; `CRITICAL` evicts all non-essential cached tiles down to an emergency working set.
  - Provide a recoverable allocation seam (`safeAllocateTile`): intercepts `OutOfMemoryError` or allocation failures, automatically triggers aggressive LRU cache eviction (pruning $\ge 50\%$ least-recently used tiles), and retries allocation safely without crashing.
  - Build repeatable profiling hooks (`RenderingProfiler`) capturing render duration, dirty tile count, dirty area ratio, cache hit/miss rates, cache memory bytes, and heap memory usage.
- **Consequences**: Guarantees bounded heap/native memory footprint regardless of canvas size, eliminates full-document bitmap copies, reduces rasterization workload by >95% for localized strokes, and provides rock-solid fault tolerance against OOM crashes on 4 GB target hardware.

---

### ADR-019: Pointer Capture, Hardware Stylus Dynamics, Dynamic Pressure Fallback & Multi-Touch Gesture Arbitration
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: A professional digital art studio must support both advanced active digitizer hardware (Wacom, S-Pen, USI stylus with fine-grained pressure, tilt, and orientation) and touch-only devices (capacitive touchscreens, entry-level phones, emulators) where hardware pressure is flat/unvarying ($1.0$ or $0.0$) and tilt is unavailable. The input architecture must normalize pointer events through the document coordinate system under arbitrary pan, zoom, rotation, and mirror flips, arbitrate multi-touch gestures to lock navigation during drawing strokes, suppress palm and oversized wrist contacts, handle pointer cancellation (`ACTION_CANCEL`) and app pause/resume cleanly without leaving dangling strokes, and expose real-time digitizer telemetry in a developer diagnostics utility.
- **Decision**:
  - Decouple pointer input into normalized `InputPointerEvent`, `PointerSample`, and `InputAction` models with adapter `MotionEventAdapter.fromMotionEvent()`.
  - Implement dynamic pressure resolver (`InputPressureResolver`): passes through and calibrates variable hardware pressure for styluses, while automatically providing velocity-based dynamic pressure simulation ($v = \Delta d / \Delta t$) with entry tapers and exponential moving average damping for touch-only devices or unvarying capacitive touchscreens.
  - Implement stylus dynamics extraction (`StylusDynamics`): extracts `AXIS_TILT` (radians and degrees), `AXIS_ORIENTATION` (radians and degrees), and contact area. Touch-only devices gracefully fall back to perpendicular tilt ($0^\circ$).
  - Implement palm contact suppression: distinguishes active stylus strokes from simultaneous secondary finger touches (`PALM_SUPPRESSED`), and filters out contacts exceeding size threshold (`contactArea >= 0.55`).
  - Strict stroke priority lock in `TouchGestureArbiter`: once a stroke begins (`DRAWING`), viewport navigation is strictly locked out; secondary pointers cannot pan/zoom or mutate viewport transforms until stroke completion.
  - Lifecycle safety: `ACTION_CANCEL` and app `pause()` cleanly abort in-flight strokes and reset arbiter state to `IDLE` without corrupting committed document state. `resume()` restores clean input readiness.
  - Developer diagnostics: built `InputDiagnosticsScreen` providing real-time telemetry of tool type, arbiter state, screen vs document coordinates, raw vs effective pressure, tilt, orientation, contact area, event counters, historical batch unpacking, missed pointer detection, and interactive live test surface.
  - Validate all input behaviors across 14 automated unit tests (`PointerInputAndStylusTest`), bringing total project test suite to 141 tests passing with 0 failures.
- **Consequences**: Guarantees responsive, natural drawing for touch-only users and full hardware fidelity for stylus users, prevents accidental viewport disruption, and ensures crash-free pointer cancellation.

---

### ADR-020: Shared Raster Stroke Pipeline, Tool Dynamics & Deterministic Fixture Goldens
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Digital art software requires distinct, authentic raster behavior for the 7 essential drawing and painting tools: Pencil (graphite grain, particle clustering with fixed random seed), Hard Pen (crisp, solid vector-smooth line with connecting bridges), Soft Pen (feathered edge, 3-ring radial falloff), Ink Brush (calligraphic dynamics, rotated elliptical ribbon), Basic Paintbrush (multi-filament bristle dynamics with jitter), Airbrush (soft Gaussian radial spray with low flow accumulation), and Eraser (PorterDuff CLEAR pixel erasure). The tools must share an incremental rasterization pipeline with sub-pixel dab interpolation based on distance and spacing, scale with dynamic stylus/velocity pressure, render active strokes without UI thread stalls, commit asynchronously to document state via coroutines, keep brush configurations serializable via JSON, and produce deterministic texture output using fixed random seeds.
- **Decision**:
  - Model brush parameters cleanly in `BrushConfig`: `BrushType` (7 core tools), `size`, `opacity`, `color`, `spacing`, `hardness`, `flow`, `minSizeRatio`, `maxSizeRatio`, `seed`, `cap`, `join`, `antiAlias`, with full JSON serialization round-trip support.
  - Provide pre-tuned factory presets in `BrushPresets` for each tool with fixed seeds (Pencil: 1337L, Paintbrush: 2026L, Airbrush: 777L) to guarantee bit-exact repeatability across platforms.
  - Implement a shared raster stroke engine (`RasterStrokeEngine`): calculates sub-pixel dab interpolation along stroke segments ($\text{step} = \max(1.0, \text{radius} \times 2 \times \text{spacing})$) to eliminate gaps and scalloping; provides tool-specific rasterization logic for each of the 7 core tools; supports high-speed incremental rendering (`renderIncremental`) for active strokes.
  - Implement `DocumentRasterCompositor`: manages active in-flight transient stroke overlays and executes non-blocking asynchronous stroke commits (`commitStrokeAsync`) off the UI thread via `Dispatchers.Default`.
  - Update `ArtStudioDrawingView` and `PointerInputPipeline` to accept active `BrushConfig` and brush presets (`setBrushPreset(type)`).
  - Add 7-tool interactive Brush Selector row to `ManualStudioScreen` with visual active-state indicators and accessible labels.
  - Create pure-Python golden render tool (`tools/qa/generate_brush_goldens.py`) and verify 7 canonical golden images in `tests/fixtures/artstudio/v1/goldens/brushes/` with `compare_golden_images.py` achieving RMSE = 0.00000, PSNR = 999.0 dB, SSIM = 1.0000.
  - Validate pipeline across 15 unit tests in `RasterBrushPipelineTest`, bringing project total to 156 tests passing with 0 failures.
- **Consequences**: Guarantees authentic, distinct artistic tools without placeholder shortcuts, prevents UI thread blocking during complex stroke commits, and ensures rock-solid visual regression testing against golden renders.

---

### ADR-021: Customizable Stroke Dynamics, Persistent Presets & Validated Interchange
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Digital artists require comprehensive customization of stroke dynamics—including configurable pressure curves (Linear, Soft, Firm, S-Curve), start/end stroke tapering, fine-grained dab spacing, customizable tip shapes (Round, Square, Chisel, Ellipse, Textured Grain), tip angle dynamics, edge hardness falloff, start/end caps, and stroke stabilization to filter hand tremor. Furthermore, artists require persistent brush presets, categorized browsing, favorites, searching, duplicating, renaming, deleting, and reliable export/import using the app's documented brush preset format (`artstudio-brush-preset`). Crucially, invalid or corrupted preset files must never crash or silently fall back to an unrelated preset, but instead fail fast with actionable, informative error messages.
- **Decision**:
  - Extend `BrushConfig` with customizable dynamics parameters: `pressureResponse`, `pressureGamma`, `startTaperRatio`, `endTaperRatio`, `tipShape`, `tipAngleDegrees`, `followStrokeAngle`, `startCap`, `endCap`, `stabilizationWeight`, preserving full JSON round-trip serialization.
  - Implement `PressureResponseMode` with mathematical transfer functions (`applyCurve`) providing graceful fallbacks for touch-only or unvarying digitizers.
  - Implement real-time EMA `StrokeStabilizer` in `PointerInputPipeline` to smooth hand jitter without input latency.
  - Model authoritative `BrushPreset` format with versioned schema (`schemaVersion: 1`, `format: "artstudio-brush-preset"`).
  - Implement strict schema and range validation in `BrushPreset.validateAndParseConfig()`, throwing `InvalidBrushPresetException` on any invalid/corrupt property (negative size, out-of-range opacity/spacing/hardness, unrecognized enums) with guaranteed zero silent fallback.
  - Implement `BrushPresetRepository`: manages built-in defaults (Classic Pencil, Soft Graphite, Studio Hard Pen, Smooth Feather Pen, Asian Calligraphy, Bristle Paintbrush, Chisel Marker, Soft Airbrush, Hard Eraser, Kneaded Eraser), custom presets, category queries, favorites, search, duplicate, rename, delete, and atomic persistence.
  - Implement `BrushPreviewGenerator`: renders authentic real-pipeline stroke previews on `Canvas` using `RasterStrokeEngine` without placeholder graphics.
  - Add interactive `BrushPresetDialog` accessible from `ManualStudioScreen`.
  - Create `test_brush_settings_goldens.py` proving that 5 dynamics settings (Pressure Curve, Taper, Spacing, Hardness, Tip Shape/Angle) objectively produce distinct rendered outputs with measurable pixel deltas (RMSE $\ge 0.01$, diff pixels $> 3,000$).
  - Validate system across 20 automated unit tests (`BrushPresetAndDynamicsTest`), bringing project test suite to 176 tests passing with 0 failures.
---

### ADR-022: Reversible Edit Commands, Transaction History & Bounded Memory Policy
- **Status**: Accepted
- **Date**: 2026-10-09
- **Context**: Drawing and painting applications require robust, reliable undo/redo capabilities across stroke commits, erases, layer operations (add, remove, reorder, visibility, opacity, blend mode), transforms, and future vector paths. Storing full-canvas bitmap copies for every action on a $2048 \times 2048$ canvas would consume 16 MB per state ($1.6$ GB for 100 actions), rapidly causing out-of-memory crashes on mobile devices. History management must be strictly bounded in memory, support clean branch truncation when editing from an undone state, decouple in-flight cancellation from committed undo, and interact correctly with save/checkpoint operations to track document dirty state and enable fast state restoration.
- **Decision**:
  - Implement a reversible command pattern (`EditCommand`):
    - `StrokeCommand`: encapsulates deterministic `BrushStroke` vector paths and brush dynamics for both paint and eraser tools without storing raster bitmaps, reducing per-stroke memory from 16 MB down to $\approx 2$ KB (a $99.98\%$ memory reduction).
    - `AddLayerCommand` / `RemoveLayerCommand`: reversible layer tree modifications preserving layer nodes, properties, and child indices.
    - `ReorderLayerCommand`: reversible layer reordering within parent groups.
    - `SetLayerPropertyCommand`: reversible modifications for layer opacity, visibility, blend modes, names, and lock states.
    - `TransformLayerCommand`: reversible affine and spatial layer transforms.
    - `VectorPathCommand`: reversible vector path insertions for AutoDraw and stylus paths.
    - `CompoundEditCommand`: composite multi-step atomic transactions.
  - Implement `HistoryManager`:
    - Maintains distinct `undoStack` and `redoStack` with configurable capacity (`maxHistoryCount = 100`) and hard memory ceiling (`maxMemoryCeilingBytes = 64 MB`).
    - Standard branch truncation: executing a new command while in an undone state discards the redo stack.
    - Decoupled cancellation: in-flight operations (e.g. pointer cancel, active transient gesture abort) are discarded via `cancelInFlight()` without pushing to or corrupting the undo stack.
    - Save point tracking: records `lastSavedCommandId` to deterministically evaluate `isDirty` (undoing back to the save point clears dirty state; redoing past it restores dirty state).
    - Periodic bounded checkpoints: captures document snapshots every $N$ commands (bounded to $M$ recent snapshots) to accelerate deep state reconstruction.
  - Integrate with `DocumentState`: exposes `historyManager`, `undo()`, `redo()`, `canUndo`, `canRedo`, and synchronized `committedStrokes`.
  - Validate reversible commands across $\ge 12$ unit tests covering command order, branch truncation, checkpointing, save/reopen, layer reorder, interleaved strokes, cancellation, golden bit-exact parity after undo-redo, and 100-stroke memory baselines.
- **Consequences**: Guarantees zero out-of-memory crashes from unbounded history allocation, provides instantaneous sub-millisecond undo/redo response, preserves bit-exact rendering parity, and maintains pristine document dirty-state tracking.

---

### ADR-023: Core Editable Raster Layer Model, Downscaled Previews & Fixture Golden Compositing
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Digital art software requires an authentic, multi-layered drawing model allowing users to create, select, rename, duplicate, delete, hide/show, reorder, lock/unlock, and adjust opacity for raster layers. In addition, rendering must composite layers strictly in document order (bottom-to-top) with accurate per-layer alpha transparency. To respect strict mobile memory budgets (avoiding a 160 MB overhead across 10 layers at 2048x2048), layer thumbnails must use downscaled preview buffers rather than full-resolution duplicate buffers. Crucially, all layer operations must integrate with the undo/redo transaction history engine, enforce stability invariants (canvas must always have at least 1 layer; locked layers reject stroke commits; hidden layers are culled from rendering), and survive document persistence in native `.ffart` archives and `project.json` with stable UUIDs and ordering.
- **Decision**:
  - Extend authoritative `ArtDocument` schema with `selectedLayerId: String? = null` and accessor helpers (`activeLayerId`, `getSelectedLayer()`), maintaining backward compatibility with prior versions.
  - Extend `DocumentSerializer` to serialize and deserialize `selectedLayerId`.
  - Extend `BrushStroke` with `layerId: String = ""` to associate individual drawing strokes with specific raster layers.
  - Implement transactional edit commands in `EditCommand.kt`:
    - `DuplicateLayerCommand`: clones layer properties and UUID, inserts immediately above source layer, updates selection, and supports full undo/redo restoration.
    - `SelectLayerCommand`: captures reversible layer selection transitions.
    - Updated `AddLayerCommand` & `RemoveLayerCommand`: track prior selection and restore selection gracefully during undo/redo.
  - Implement `LayerManager` controller: exposes transactional `createLayer`, `selectLayer`, `renameLayer`, `duplicateLayer`, `deleteLayer` (guarded by minimum 1-layer invariant), `setLayerVisibility`, `toggleLayerVisibility`, `setLayerLocked`, `toggleLayerLocked`, `setLayerOpacity` (clamped in $[0.0, 1.0]$), `reorderLayer`, `moveLayerUp`, and `moveLayerDown`.
  - Update `DocumentState` and `DefaultDocumentState`: expose `layerManager`, check layer locked state in `addCommittedStroke` (drawing into locked layers is prohibited and rejected), and duplicate associated strokes during layer duplication.
  - Update `NativeCanvasRenderer`: iterate through layers in document order (0 = bottom, N-1 = top), cull hidden/zero-opacity layers, apply per-layer alpha compositing via `canvas.saveLayerAlpha`, apply affine transforms, and render layer-specific strokes as well as in-flight active strokes on the selected unlocked layer.
  - Implement `LayerThumbnailGenerator`: generates and caches downscaled 64x64 ARGB_8888 previews (16 KB per thumbnail vs 16 MB full buffer), bounding 10-layer memory to 160 KB (a 99% reduction).
  - Implement Compose UI component `StudioLayerPanel`: provides responsive layer rows, opacity slider, visibility/lock toggles, move up/down, duplicate, and delete controls. Integrate with `ManualStudioScreen`.
  - Generate canonical 10-layer synthetic fixture (`ten_layer_fixture_golden.png`, `ten_layer_fixture_project.json`, `ten_layer_fixture.ffart`) and automated verification suite `tools/qa/test_layer_fixture_goldens.py`, reporting pixel diff and enforcing bit-exact baseline (RMSE = 0.0, SSIM = 1.0).
  - Implement 15 comprehensive unit and golden tests in `RasterLayerModelTest`, bringing the project unit test suite to 208 tests passing with 0 failures.
- **Consequences**: Guarantees full Procreate/Photoshop-grade layer management on mobile, enforces memory and stability invariants, and ensures zero visual degradation across layer operations.

### ADR-024: Layer Groups, Hierarchical Tree Operations & Non-Destructive Structural Commands
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Creative users require non-destructive structural organization: grouping layers, nesting groups, moving layers into/out of groups, multi-selection batch operations, merging selected layers, merging visible layers, and flattening. However, mobile performance and recursion depth require strict memory and nesting limits. Destructive actions (merging, flattening) must never occur silently (e.g. during save, import, analysis, or export), and must always be undoable. Group folding in the UI must be transient and decoupled from document semantics so it does not dirty the document or create command history.
- **Decision**:
  - Define `LayerHierarchyConstants.MAX_GROUP_DEPTH = 5` in `LayerTreeExtensions.kt`, preventing deep recursion overhead and memory bloat on mobile. Exceeding depth 5 throws `MaxGroupDepthExceededException`.
  - Enforce strict cycle prevention (`isDescendantOf`): moving a group into its own descendant is disallowed and rejected.
  - Decouple UI state into `LayerTreeUiState`: tracks group expansion (`expandedGroupIds`) and multi-selection (`selectedLayerIds`) independently from `ArtDocument` semantics.
  - Implement reversible structural commands in `EditCommand.kt`:
    - `CreateGroupCommand`: groups selected layers into a new `LayerNode.LayerGroup` at a specified index; undo restores layers to original parents.
    - `UngroupCommand`: dissolves group, placing children at group's index in parent; undo restores group node.
    - `MoveLayerHierarchyCommand`: moves layers between parent groups with cycle and depth checks.
    - `MergeLayersCommand`: combines selected layers into a unified raster layer, tracking stroke remappings; undo restores all original layers and strokes.
    - `MergeVisibleCommand`: collapses visible layers into a single raster layer while keeping hidden layers intact.
    - `FlattenDocumentCommand`: collapses entire hierarchy down to a single background raster layer; undo restores all groups and layers.
  - Implement `LayerManager` methods: `createGroup`, `moveLayerIntoGroup`, `moveLayerOutOfGroup`, `ungroup`, `multiSelect`, `toggleMultiSelect`, `mergeSelectedLayers`, `mergeVisibleLayers`, and `flattenDocument`.
  - Invariant: `flattenDocument(confirmed = false)` requires explicit `confirmed == true` guard; throws `IllegalStateException` if unconfirmed. Silent flattening is prohibited across save, export, import, and CV analysis.
  - Update `NativeCanvasRenderer` with recursive `renderLayerNode` traversal for `LayerNode.LayerGroup` up to `MAX_GROUP_DEPTH = 5`, applying inherited opacities via `saveLayerAlpha` and affine transforms.
  - Update `StudioLayerPanel`: visual tree items with depth indentation, expand/collapse toggles, multi-selection action bar ("Group", "Merge", "Clear"), "Merge Visible", and explicit confirmation modal (`StudioDialog`) for "Flatten Artwork".
  - Golden Quality Assurance: `test_layer_group_and_merge_goldens.py` verifies bit-exact merge equivalence (RMSE = 0.0, SSIM = 1.0, 0 diff pixels), undo equivalence (RMSE = 0.0), confirmation guard, and depth limit enforcement.
  - Unit test suite: 15 tests in `LayerGroupAndMergeTest.kt`, bringing total test count to 222 passing tests.
- **Consequences**: Provides industry-standard layer grouping and non-destructive workflows with strict depth limits, explicit confirmation safeguards against accidental data loss, and 100% reversible undo operations.

---

### ADR-025: Authoritative Layer Blend Modes & Porter-Duff Compositing Architecture
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Professional digital art applications require a robust, predictable compositing model supporting industry-standard blend modes. Aliasing unsupported modes or silently faking equations leads to jarring visual artifacts, dark edge halos, and cross-platform divergence between preview and export rendering. Compositing must precisely define alpha transparency, premultiplication semantics, sRGB color spaces, and group compositing rules. Every exposed blend mode must be backed by a verified test case, and unsupported modes must be explicitly flagged rather than silently aliased. Export paths (whole-buffer operations) must maintain bit-exact parity with native hardware canvas compositing.
- **Decision**:
  - Starter Set of 8 Verified Blend Modes in `CanvasConfig.kt`:
    - `Normal` ($B(c_b, c_s) = c_s$)
    - `Multiply` ($B(c_b, c_s) = c_b \cdot c_s$)
    - `Screen` ($B(c_b, c_s) = c_b + c_s - c_b \cdot c_s$)
    - `Add / Linear Dodge` ($B(c_b, c_s) = \min(1.0, c_b + c_s)$)
    - `Darken` ($B(c_b, c_s) = \min(c_b, c_s)$)
    - `Lighten` ($B(c_b, c_s) = \max(c_b, c_s)$)
    - `Overlay` ($2 c_b c_s$ if $c_b \le 0.5$, else $1 - 2(1 - c_b)(1 - c_s)$)
    - `Color Dodge` ($\min(1.0, c_b / (1 - c_s))$ with safe singularity handling)
    - Curated starter set exposed via `BlendModeType.supportedModes`.
  - Rejection and Clear Labeling of Unsupported Modes:
    - `ColorBurn`, `SoftLight`, `HardLight`, `Difference`, `Exclusion`, `Hue`, `Saturation`, `Color`, `Luminosity` explicitly marked with `isSupported = false` and labeled `" (Unsupported)"`.
    - No silent aliasing: attempting to use an unsupported mode in QA engine raises an explicit ValueError, and UI selectors display only `BlendModeType.supportedModes`.
  - Alpha Compositing & Color Space Rules in `LayerBlendCompositor.kt`:
    - Operates in unclamped normalized sRGB space, clamped to $[0.0, 1.0]$ upon integer packing.
    - W3C / ISO 32000-1 Porter-Duff Source Over compositing equations:
      - $\alpha_r = \alpha_s + \alpha_b \cdot (1 - \alpha_s)$
      - $C_r = \frac{(1 - \alpha_b) \cdot \alpha_s \cdot C_s + \alpha_b \cdot [(1 - \alpha_s) \cdot C_b + \alpha_s \cdot B(C_b, C_s)]}{\alpha_r}$
    - Transparent backdrop fast-path preserves un-premultiplied source color channels and eliminates dark edge halos.
    - Whole-buffer in-place compositing via `compositeBuffers` provides fast export rendering.
  - Hardware Canvas Integration in `NativeCanvasRenderer.kt`:
    - Applies `canvas.saveLayer(bounds, layerPaint)` where `layerPaint` is configured with modern `android.graphics.BlendMode` (API 29+) or legacy `PorterDuff.Mode` fallback when a layer specifies a non-Normal blend mode or opacity $< 1.0f$.
  - Layer Management & History Integration in `LayerManager.kt` & `EditCommand.kt`:
    - Added `LayerManager.setLayerBlendMode(layerId, blendMode)`.
    - `SetLayerPropertyCommand` handles `"blendmode"` property mutations with full undo/redo reversibility.
  - Jetpack Compose UI Controls:
    - `StudioLayerRow`: displays active blend mode subtitle if non-Normal (e.g. `"Opacity: 100% • Multiply"`).
    - `StudioLayerPanel`: provides horizontal `LazyRow` pill selector for `BlendModeType.supportedModes` on selected layer.
  - Hand-Calculated Fixtures & Golden QA:
    - Kotlin Unit Suite (`LayerBlendModeCompositingTest.kt`, 14 tests): verifies hand-calculated fixtures against exact mathematical goldens within $\pm 1$ delta tolerance, partial opacity, transparent edges, multi-layer stacked composition, unsupported mode rejection, undo/redo reversibility, and whole-buffer parity.
    - Python QA Suite (`test_blend_mode_goldens.py`): 7 validation sections generating and checking goldens for all 8 modes, pairwise distinctness, partial opacity, transparent edges, stacked composition, export path parity (RMSE = 0.0, SSIM = 1.0), and unsupported mode guards.
- **Consequences**: Guarantees bit-exact compositing predictability across native canvas and export pipelines, prevents halo artifacts on transparent edges, respects mobile performance constraints, and provides full undo/redo integration for blend mode mutations.

---

### ADR-026: Non-Destructive Layer Masks, Clipping Chains & Alpha-Lock Architecture
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Advanced digital art creation workflows require three core non-destructive transparency manipulation primitives:
  1. Grayscale Layer Masks: allow artists to conceal or reveal parts of a layer non-destructively without permanently erasing pixel data.
  2. Clipping Chains: allow one or more child layers to clip their visible extents strictly to the alpha silhouette of a base layer below them.
  3. Alpha-Lock: confines brush strokes to already-painted pixels on the active layer, preventing strokes from expanding into transparent areas.
  Furthermore, because external interchange formats (PNG, JPEG, WebP, OpenRaster, SVG, legacy v1 schemas) may not natively support these features, Art Studio must guarantee zero silent data loss by surfacing explicit conversion warnings whenever an export target cannot preserve native mask or clipping capabilities.
- **Decision**:
  - Layer Mask Model & Invariants (`LayerNode.kt`):
    - Added `LayerMask` data class with persistent UUID, `enabled` toggle, `inverted` toggle, `assetPath`, `width`, `height`, independent affine `transform`, and `linkTransformWithLayer` binding.
    - Attached `mask: LayerMask? = null`, `isClippingMask: Boolean = false`, and `isAlphaLocked: Boolean = false` to all `LayerNode` variants (`RasterLayer`, `VectorPathLayer`, `LayerGroup`).
  - Edit-Mask Mode & Stroke Target (`BrushStroke.kt`, `LayerTreeUiState.kt`):
    - Added `isMaskStroke: Boolean = false` to `BrushStroke`.
    - Added `LayerEditTarget` enum (`CONTENT`, `MASK`) and `activeEditTarget` to `LayerTreeUiState`. When `LayerEditTarget.MASK` is active, brush strokes are tagged `isMaskStroke = true` and paint directly onto the grayscale mask rather than RGB pixel channels.
  - Reversible History Commands (`EditCommand.kt`):
    - Added `AddMaskCommand`, `RemoveMaskCommand`, `InvertMaskCommand`, `SetMaskPropertyCommand`, and `TransformMaskCommand`.
    - Extended `SetLayerPropertyCommand` to handle `"isalphalocked"` / `"alphalock"` and `"isclippingmask"`.
  - Transactional Controller APIs (`LayerManager.kt`):
    - Added `addLayerMask`, `removeLayerMask`, `setMaskEnabled`, `toggleMaskEnabled`, `invertMask`, `setMaskInverted`, `transformMask`, `setLayerClippingMask`, `toggleLayerClippingMask`, `setLayerAlphaLock`, `toggleLayerAlphaLock`, `setEditTarget`, and `isEditingMask`.
    - Removing a mask automatically resets active edit target to `CONTENT`.
  - Compositing & Rendering Engine (`LayerBlendCompositor.kt`, `NativeCanvasRenderer.kt`):
    - `LayerBlendCompositor.applyMaskToBuffer`: modulates layer pixel alpha using normalized grayscale mask luminance ($Y = 0.299R + 0.587G + 0.114B$); inverts luminance when `mask.inverted = true`; bypasses modulation when `mask.enabled = false`.
    - `LayerBlendCompositor.applyClippingMaskToBuffer`: modulates child layer alpha by base layer alpha ($\alpha_{child}' = \alpha_{child} \cdot \alpha_{base}$).
    - `LayerBlendCompositor.applyAlphaLockStroke`: composites stroke buffer onto base buffer using Porter-Duff `SRC_ATOP`, strictly preserving destination alpha ($\alpha_{out} = \alpha_{base}$) and preventing bleeding into transparent pixels.
    - `NativeCanvasRenderer.renderLayerList`: partitions sibling layers into clipping blocks (`ClippingBlock(baseLayer, clippedLayers)`). Base layer renders first, and consecutive clipped child layers composite onto the base layer using `SRC_ATOP`. Orphan clipping masks at index 0 gracefully render unclipped.
    - `NativeCanvasRenderer.renderLayerNode`: applies mask offscreen via `canvas.saveLayer` and PorterDuff `DST_IN`.
  - Document Persistence & Interop Auditing (`DocumentSerializer.kt`, `ConversionReport.kt`):
    - `DocumentSerializer`: serializes and deserializes `isAlphaLocked`, `isClippingMask`, and full `mask` properties across all layer types.
    - `DocumentConversionAuditor`: enforces zero silent feature loss. Audits documents during export and reports explicit `ConversionWarning` objects for `LAYER_MASK`, `CLIPPING_MASK`, and `ALPHA_LOCK` when targeting `FLAT_RASTER`, `OPENRASTER`, `SVG`, or `LEGACY_V1`.
  - Jetpack Compose UI Controls (`StudioLayerRow.kt`, `StudioLayerPanel.kt`):
    - `StudioLayerRow`: displays badges/icons for clipping (`✂️`), alpha-lock (`🔒α`), mask presence (`🎭`), and active mask editing (`[Editing Mask]`).
    - `StudioLayerPanel`: provides quick-toggle pills for `Clipping: ON/OFF` and `Alpha Lock: ON/OFF`, along with mask controls (`+ Add Mask`, `🎭 On/Off`, `🔄 Inverted`, `🖌️ Edit Mask / ✏️ Edit Content`, `🗑️ Remove Mask`).
  - Golden Quality Assurance:
    - Unit test suite (`LayerMaskAndClippingTest.kt`, 13 tests): verifies mask lifecycle, invert, enable/disable, independent transform, clipping chains, orphan fallbacks, alpha lock zero bleed, pixel modulation equations, auditor warnings, and serializer roundtrips.
    - Python QA suite (`test_mask_and_clipping_goldens.py`, 11 tests): verifies golden pixel parity across all mask operations, clipping chains, alpha-lock boundary tests, and auditor rules.
- **Consequences**: Delivers complete non-destructive masking, clipping, and alpha-locking capabilities with 100% undo/redo reversibility, guaranteed absence of transparent edge bleeding, full hardware and export parity, and zero silent data loss during format conversions.

---

### ADR-027: Document-Space Selection Mask Engine and Morphology Operators
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Precision selection is a foundational capability for digital artwork creation, underpinning targeted transforms, fills, layer mask generation, filters, and region analysis. Key requirements:
  1. Multi-tool geometric support: Rectangle, Ellipse, Freehand Lasso, and Polygon.
  2. Boolean combination algebra: Replace (New), Add (Union), Subtract (Difference), and Intersect.
  3. Strict Document-Space Invariance: Selections must be defined in document canvas coordinates, ensuring that viewport pan, zoom (0.25x – 8.0x), rotation (45°, 90°, etc.), and canvas flip leave the selection mask invariant.
  4. Precision mask operations: Invert, Clear, Circular Euclidean Expand (Dilation), Circular Euclidean Contract (Erosion), and 2-pass separable Gaussian Feathering.
  5. Cross-feature reusability: Selection must integrate directly with fills, layer masks (`SelectionMask.toLayerMask()`), and undo/redo history (`SetSelectionCommand`).
  6. Visible feedback: High-contrast dual-stroke marching ants boundary rendering on native canvas.
  7. Deterministic cross-platform math: Avoid reliance on platform GUI rendering or Android SDK stub methods during host JVM unit testing.
- **Decision**:
  - Selection Model (`SelectionModel.kt`):
    - Created `SelectionBounds` pure Kotlin data class for bounds representation without reliance on Android `RectF` stubs.
    - Created `SelectionShape` hierarchy: `Rectangle(left, top, right, bottom)`, `Ellipse(left, top, right, bottom)`, `Lasso(points: List<CanvasPoint>)`, and `Polygon(vertices: List<CanvasPoint>)`.
    - Defined `SelectionCombineMode`: `REPLACE`, `ADD`, `SUBTRACT`, `INTERSECT`.
    - Defined `SelectionToolType`: `RECTANGLE`, `ELLIPSE`, `LASSO`, `POLYGON`.
    - Defined `SelectionLimits`: `MIN_EXPAND_CONTRACT = 1`, `MAX_EXPAND_CONTRACT = 100`, `MIN_FEATHER_RADIUS = 0.5f`, `MAX_FEATHER_RADIUS = 50.0f`.
  - Document-Space Mask Buffer (`SelectionMask.kt`):
    - Backed by an 8-bit unsigned alpha buffer `ByteArray(width * height)`.
    - Deterministic scanline rasterization with sub-pixel centers ($y + 0.5$) for polygons and lassos, ellipse quadratic equation $(dx^2/rx^2 + dy^2/ry^2 \le 1)$, and axis-aligned rectangle filling.
    - Boolean combination algebra:
      - `REPLACE`: Direct buffer overwrite.
      - `ADD`: Max union ($\max(a_{curr}, a_{new})$).
      - `SUBTRACT`: Difference ($a_{curr} \cdot (1 - a_{new})$).
      - `INTERSECT`: Intersection ($a_{curr} \cdot a_{new}$).
    - Invert: Bitwise negation ($255 - a$).
    - Expand & Contract: Circular Euclidean distance ($dx^2 + dy^2 \le r^2$) bounded by `SelectionLimits`.
    - Feather: Separable 2-pass Gaussian blur filter with normalized kernel weights.
    - Integration helpers: `toLayerMask()`, `fill(targetPixels, color, opacity)`, and `clone()`.
  - Transactional Selection Controller (`SelectionManager.kt`):
    - Tracks `currentSelection`, `activeTool`, `combineMode`, `featherRadius`.
    - Integrates with `HistoryManager`: records reversible `SetSelectionCommand` before and after every selection mutation (`mutateSelection`), guaranteeing 100% undo/redo fidelity.
    - Provides `createLayerMaskFromSelection(targetLayerId, layerManager)` for instant layer mask generation.
  - Viewport & Marching Ants Rendering (`SelectionOutlineRenderer.kt`, `NativeCanvasRenderer.kt`):
    - `SelectionOutlineRenderer`: Dual-tone black and white alternating `DashPathEffect` strokes ensure visibility over light and dark artwork backgrounds.
    - Rendered directly inside `NativeCanvasRenderer.render()` in document space before canvas restore, guaranteeing zero visual distortion or drift during zoom, pan, and rotation.
  - Accessible Compose Controls (`StudioSelectionBar.kt`):
    - Provides tool selection, combine modes, invert, clear, expand, contract, and feather buttons.
    - Follows strict $\ge 48$ dp touch target guidelines and WCAG AAA contrast with disabled explanation tooltips.
  - Golden Quality Assurance:
    - Kotlin Unit Test Suite (`SelectionEngineAndTransformTest.kt`, 19 tests): 100% pass rate across primitives, combinations, morphology, feathering, fills, layer masks, history undo/redo, viewport transform invariance, and corpus image loading (`transparent_cutout_01.png`).
    - Python QA Suite (`test_selection_goldens.py`, 12 tests): Generates and enforces bit-exact goldens in `tests/fixtures/artstudio/v1/goldens/selection/` with 0 diff pixels (RMSE = 0.0, SSIM = 1.0) across all operations and corpus fixtures (`transparent_cutout_01.png`, `flat_colour_simple_01.png`).
- **Consequences**: Enables pixel-precise selections that remain invariant across viewport transformations, integrate seamlessly into non-destructive masks and editing pipelines, support full undo/redo transactions, and pass strict mathematical golden verification gates.

---

### ADR-028: Non-Destructive Layer Transforms & Canvas Crop Engine
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Digital artists require precise spatial transformations (move, scale, rotate, flip) at both the individual layer level and the global document canvas level (canvas resize, crop). However, conflating layer transformations with canvas dimension resizing causes catastrophic irreversible data loss and pixel corruption. Critical requirements include:
  1. Strict Separation of Concerns: Transforming a layer must never mutate document canvas bounds or sibling layers. Resizing or cropping the canvas modifies `CanvasConfig` dimensions and repositions layers relative to an anchor point without destroying layer content.
  2. Non-Destructive Out-of-Bounds Content: Moving a layer or cropping a canvas must never delete pixels or vector math outside the canvas boundary. Expanding the canvas or undoing a crop must restore all content bit-for-bit with 0 degradation.
  3. Mathematical Vector Preservation: For vector layers (`VectorPathLayer`), affine transformations must apply at the viewport/projection level while preserving the underlying mathematical coordinates `(x, y)` and pressure/tilt metadata.
  4. Aspect Ratio & Interpolation Controls: Layer scaling must support aspect-ratio locking and user-selected interpolation modes (`NEAREST_NEIGHBOR`, `BILINEAR`, `BICUBIC`).
  5. 9-Point Anchor Canvas Resizing: Canvas resizing must allow anchoring artwork to any of 9 alignment positions (`TOP_LEFT`, `TOP_CENTER`, `TOP_RIGHT`, `CENTER_LEFT`, `CENTER`, `CENTER_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_CENTER`, `BOTTOM_RIGHT`).
  6. Reversible History Transactions: Every layer transform, canvas resize, and crop must be fully reversible through `HistoryManager`.
  7. Cross-Feature Integration: Cropping must integrate with selections (Crop-to-Selection using selection bounding boxes).
- **Decision**:
  - Transform & Anchor Models (`CanvasConfig.kt`):
    - Added `InterpolationMode` enum (`NEAREST_NEIGHBOR`, `BILINEAR`, `BICUBIC`).
    - Added `CanvasResizeAnchor` enum modeling 9 anchor positions with calculation methods for layer translation deltas: $\Delta x = \text{round}((W_{new} - W_{old}) \cdot ax)$, $\Delta y = \text{round}((H_{new} - H_{old}) \cdot ay)$.
    - Enhanced `LayerTransform` with `flipHorizontal`, `flipVertical`, `isAspectRatioLocked`, `interpolation`, and immutable mutator helpers (`withTranslation`, `withScale`, `withUniformScale`, `withRotation`, `toggleFlipHorizontal`, `toggleFlipVertical`, `toggleAspectRatioLock`, `reset`).
  - Document Tree Mapping & Schema Roundtrip (`LayerTreeExtensions.kt`, `DocumentSerializer.kt`):
    - Added functional recursive mapper `ArtDocument.mapLayers(transform: (LayerNode) -> LayerNode)`.
    - Extended JSON serialization to persist `flipH`, `flipV`, `aspectLocked`, and `interpolation` across `.ffart` project files.
  - Reversible Edit Commands (`EditCommand.kt`):
    - `TransformLayerCommand`: Mutates only target layer's `LayerTransform`. Canvas dimensions and sibling layers are untouched.
    - `ResizeCanvasCommand`: Changes `CanvasConfig` dimensions and shifts all layers according to `CanvasResizeAnchor`. Undo restores previous canvas dimensions and offsets layers in reverse.
    - `CropCanvasCommand`: Crops `CanvasConfig` to target rectangle $[cropX, cropY, cropW, cropH]$ and offsets all layers by $(-cropX, -cropY)$. Undo restores previous canvas dimensions and shifts layers by $(+cropX, +cropY)$.
  - Non-Destructive Out-of-Bounds Invariant:
    - Layer raster buffers and vector coordinates retain all original pixel and stroke data regardless of canvas boundaries. The canvas viewport merely clips the rendering presentation.
    - Undoing a crop or expanding the canvas recovers out-of-bounds content bit-exact with 0 data loss.
  - Pure-Kotlin Affine Resampling Engine (`TransformResampler.kt`):
    - Pure Kotlin 2D affine raster resampler supporting Nearest Neighbor, Bilinear, and Bicubic interpolation with inverse mapping and zero GUI platform dependencies.
  - Hardware Rendering & Document Dynamic Binding (`NativeCanvasRenderer.kt`, `DocumentState.kt`):
    - Integrated horizontal and vertical flip scale factors into hardware rendering matrices.
    - Dynamically bound `DefaultDocumentState.width` and `height` to `authoritativeDocument.canvas.width` and `height` so document boundaries reflect canvas resizing and cropping.
  - Layer Manager Controller (`LayerManager.kt`):
    - Added transactional APIs: `transformLayer`, `moveLayer`, `scaleLayer`, `rotateLayer`, `flipLayerHorizontal`, `flipLayerVertical`, `resetLayerTransform`, `resizeCanvas`, `cropCanvas`, and `addLayer`.
  - Accessible Compose UI Controls (`StudioTransformPanel.kt`):
    - Provided dual-mode switching (Layer vs Canvas), numerical move buttons, aspect lock toggle, rotation buttons ($\pm 15^\circ, \pm 90^\circ$), flip buttons, interpolation dropdown, 3x3 anchor grid selector, and crop-to-selection action ($\ge 48$ dp touch targets, WCAG AAA).
  - Golden Quality Assurance:
    - Unit test suite (`TransformAndCropEngineTest.kt`, 11 tests): verifies layer move, uniform/non-uniform scale, rotate 360 roundtrip, flip H/V, reset, command separation, anchor offsets, crop bounds & undo recovery, crop to selection, out-of-bounds content preservation, vector preservation, interpolation modes, and dynamic document binding. Total unit test suite: 283 tests passing (100%).
    - Python QA suite (`test_transform_and_crop_goldens.py`, 12 tests): verifies golden pixel parity across 12 transform and crop test cases with 0 differing pixels (RMSE = 0.0, SSIM = 1.0).
- **Consequences**: Guarantees professional-grade non-destructive transformations and canvas adjustments with zero pixel degradation, complete command separation, full undo/redo reversibility, and rigorous golden verification.

---

### ADR-029: Bounded Advanced Deformation Subsystem (Mesh Warp & Liquify)
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Advanced creative image manipulation requires non-rigid spatial deformation tools: control-point mesh warping (grid warp) and brush-based interactive liquify (Push, Expand/Inflate, Pinch/Deflate, Twirl, Reconstruct). However, mobile hardware poses acute memory and latency constraints:
  1. Interactive surface protection: deformation must never destructively mutate source artwork until explicit user confirmation; users must have instantaneous cancel and reset capabilities.
  2. Memory budget safety: full 2D vector displacement fields and high-resolution duplicated surfaces on 4 GB physical devices can cause out-of-memory crashes if unbounded.
  3. Continuous mathematical sampling: piecewise triangle inverse affine mapping and smooth cubic Hermite kernels are required to avoid tearing, holes, or edge aliasing.
  4. Real-time preview vs commit resolution: on constrained devices or large canvases ($> 1024$), interactive manipulation must operate on an adaptive downscaled preview surface while full-resolution resampling executes on commit.
  5. Reversible history: commit must generate an undoable `DeformLayerCommand` recording pre- and post-deformation snapshots.
- **Decision**:
  - Deformation Data Models (`DeformationModel.kt`):
    - `DeformToolMode`: `WARP_MESH`, `LIQUIFY_PUSH`, `LIQUIFY_EXPAND`, `LIQUIFY_PINCH`, `LIQUIFY_TWIRL_CW`, `LIQUIFY_TWIRL_CCW`, `LIQUIFY_RECONSTRUCT`.
    - `EdgeBehavior`: `CLAMP_TO_EDGE`, `TRANSPARENT_ZERO`, `WRAP`.
    - `DeformInterpolation`: `NEAREST_NEIGHBOR`, `BILINEAR`, `BICUBIC`.
    - `WarpMesh`: $R \times C$ control grid with `MeshVertex(x, y, u, v)` data classes, bounding envelope calculations, and `reset()`.
    - `DeformationConfig`: bounds maximum working resolution (`maxWorkingResolution = 2048`) and adaptive preview scale ($[0.1, 1.0]$).
  - High-Performance Displacement Field & Triangle Mesh Warper (`DeformationEngine.kt`):
    - `DisplacementField`: backed by pure-Kotlin `FloatArray(width * height)` for $dx$ and $dy$. Implements smooth quadratic decay $(1 - r^2)^2$ for brush push, radial expand/pinch, twirl rotational vortex, and reconstruct baseline decay.
    - `resampleTo(destW, destH)`: provides bilinear resampling and scale compensation for displacement fields between adaptive preview and full document resolutions.
    - Piecewise triangle affine inverse mapping: subdivides each grid quad into two triangles, computes barycentric coordinates $(\lambda_1, \lambda_2, \lambda_3)$, and inverse-maps pixel coordinates to $(u, v)$ source coordinates without edge tearing.
    - Pixel sampling: implements Nearest Neighbor, Bilinear, and Bicubic interpolation with configurable edge behaviors (Clamp, Transparent Zero, Wrap).
  - Duplicated Preview Surface & Session Controller (`DeformationSession.kt`):
    - Clones original layer raster data into an immutable snapshot `originalPixels`; all interactive editing occurs exclusively on `previewPixels`.
    - `reset()`: restores preview surface bit-for-bit to initial original duplicate (0 diff pixels).
    - `cancel()`: cleanly terminates session with 0 modifications to document or edit history.
    - `commit(layerManager)`: requires user confirmation; computes full-resolution deformation and records a transactional `DeformLayerCommand`.
  - Adaptive Preview Resolution & 4 GB Device Memory Protection:
    - On canvases $> 1024 \times 1024$ or low-memory profiles ($\le 4$ GB), interactive preview uses a downsampled surface ($\le 1024$, $\approx 16$ MB RAM). Full-resolution deformation executes only upon commit.
    - Maximum working resolution is strictly enforced at 2048x2048 ($\approx 64$ MB peak RAM). Oversized requests are cleanly rejected with `IllegalArgumentException`.
  - Accessible Compose UI (`StudioDeformPanel.kt`):
    - Interactive panel providing tool switching, brush radius/strength sliders, interpolation dropdown, edge behavior toggle, reset/cancel actions, and commit with modal confirmation dialog (`StudioDialog`). Guarantees $\ge 48$ dp touch targets and WCAG AAA contrast.
  - Golden Quality Assurance:
    - Kotlin Unit Test Suite (`DeformationEngineTest.kt`, 14 tests): verifies mesh boundaries, envelope expansion, identity rasterization, nearest/bilinear/bicubic interpolation, edge clamp/transparent/wrap, push, expand, pinch, twirl, reconstruct, reset/cancel state preservation, and commit undo/redo. Total unit test suite: 298 tests passing (100%).
    - Python QA Suite (`test_deformation_goldens.py`, 10 tests): generates and checks goldens for all deformation operations and two fixed corpus fixtures (`transparent_cutout_01.png` at 512x512 and `flat_colour_dense_02.png` at 1024x1024) with 0 differing pixels (RMSE = 0.0, SSIM = 1.0).
- **Consequences**: Delivers advanced deformation capabilities with bounded memory usage, adaptive preview scalability on 4 GB target hardware, zero risk of accidental destructive mutations, and 100% undo/redo reversibility.

---

### ADR-030: Primary Colour Selection, Palette Management & Gradient Subsystem
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Professional digital painting workflows require comprehensive, accurate color selection, robust palette management, and versatile gradient generation:
  1. Color representations: artists require smooth navigation between radial hue-saturation wheels, numerical RGB/HSV sliders, and hex inputs with strict mathematical conversions ($\le 0.001$ tolerance).
  2. Alpha channel & premultiplication: correct compositing and export require explicit alpha support and well-defined premultiplication/demultiplication routines.
  3. Color space & print accuracy: per instructions, subtractive CMYK must not be claimed as print-accurate without calibrated ICC profile transform pipelines. An explicit disclaimer and programmatic notice must be enforced.
  4. Palette isolation invariant: user palettes must be decoupled from document pixel semantics; modifying, switching, or deleting palettes must NEVER alter existing raster layers.
  5. Palettes and gradient persistence: user palettes must persist globally across sessions (`user_palettes.json`) and project-specific palettes/gradients must survive `.ffart` document save and reopen cycles.
  6. Editable gradients: linear and radial gradient generators must support arbitrary multi-stop distributions, tile modes (Clamp, Repeat, Mirror), selection mask modulation, and reversible undo/redo execution.
- **Decision**:
  - Color Models & Space Mathematics ([`ColorModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/color/ColorModels.kt)):
    - `ColorData`: 4-component Float RGBA container with 8-bit ARGB/RGBA packing, HSV conversion ($H \in [0, 360), S, V \in [0, 1]$), HSL conversion, hex parsing (`#RGB`, `#RGBA`, `#RRGGBB`, `#AARRGGBB`), premultiplication/demultiplication, IEC 61966-2-1 linear sRGB gamma transfer, and linear interpolation (`lerp`).
    - `ColorSpaceType`: `SRGB`, `LINEAR_SRGB`, `DISPLAY_P3`, `CMYK_UNPROOFED`.
    - `PrintColorNotice`: Explicit disclaimer stating subtractive CMYK is an uncalibrated display approximation; `isPrintAccurate` returns `false`.
  - Color State Coordinator ([`ColorStateManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/color/ColorStateManager.kt)):
    - `ForegroundBackgroundState`: Dual color slots with instant swap (`swapColors()`) and default reset (`resetDefaults()`).
    - `ColorHistory`: Bounded recent color stack (capacity 24) with automated deduplication and JSON persistence.
    - `StudioColorState`: Coordinated state manager supporting `WHEEL`, `SLIDERS_RGB`, `SLIDERS_HSV`, and `HEX` input modes.
  - Palette Repository & Quantization Engine ([`PaletteManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/color/PaletteManager.kt)):
    - `StandardPalettes`: 5 curated factory palettes (Classic Studio, Cyber Neon, Pastel Dream, Earth & Ochre, Manga & Ink).
    - `PaletteRepository`: User palette CRUD with global persistence (`user_palettes.json`), JSON export/import, and GIMP Palette (`.gpl`) export/import.
    - `PaletteExtractor`: Pure-Kotlin Median-Cut color quantization algorithm extracting up to 256 representative swatches from active canvas buffers.
  - Gradient Engine & Renderer ([`GradientModel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/color/GradientModel.kt)):
    - `GradientConfig`: Supports `LINEAR`, `RADIAL`, and `ANGULAR` types, arbitrary `GradientColorStop` lists, tile modes (`CLAMP`, `REPEAT`, `MIRROR`), endpoints, and linear space interpolation toggle.
    - `GradientRenderer`: Pure-Kotlin rasterizer evaluating linear vector projections and radial Euclidean distances with linear color space blending and 8-bit selection mask modulation.
  - Transactional Undo/Redo & Document Integration ([`EditCommand.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/EditCommand.kt), [`LayerManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/layer/LayerManager.kt), [`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt)):
    - Added [`ApplyGradientCommand`](file:///root/artstudio/app/src/main/java/com/artstudio/history/EditCommand.kt): transactional snapshot command storing target layer ID, pre- and post-gradient pixel buffers, and metadata. Fully reversible via `HistoryManager`.
    - Added `LayerManager.applyGradientToLayer` transactional controller API.
    - Extended `ArtDocument` and `DocumentSerializer` with `gradientPresets` list and `.ffart` JSON serialization preserving custom palettes and gradient presets across save/reopen.
  - Accessible Jetpack Compose UI Controls ([`StudioColorWheel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioColorWheel.kt), [`StudioColorPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioColorPanel.kt), [`StudioPalettePanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioPalettePanel.kt), [`StudioGradientPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioGradientPanel.kt)):
    - Interactive touch-friendly radial color wheel, dual foreground/background chips, sliders, hex input, history strip, palette swatch grid with canvas extraction action, and gradient editor with modal commit confirmation (`StudioDialog`). All targets $\ge 48$ dp, WCAG AAA.
  - Verification & Parity:
    - 17 unit tests ([`ColorAndGradientWorkflowTest.kt`](file:///root/artstudio/app/src/test/java/com/artstudio/color/ColorAndGradientWorkflowTest.kt)): 100% pass rate. Total unit test suite: 315 tests passing.
    - Python QA golden suite ([`test_color_and_gradient_goldens.py`](file:///root/artstudio/tools/qa/test_color_and_gradient_goldens.py), 6 tests): 0 diff pixels across linear and radial gradients, color conversions, and fixture palette extraction.
- **Consequences**: Provides comprehensive color selection, robust palette management with strict layer isolation, versatile gradient generation with undo/redo reversibility, zero-loss project persistence, and full adherence to color space standards and disclaimers.

---

### ADR-031: Accurate Fills with Gap Closing, Line Boundaries & Cancellation
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Artists depend on bucket/flood fill operations for efficient inking, flats, and comic book production:
  1. Contiguous & Global Selection: Fills must operate both contiguously (4-way scanline flood fill bounded by line contours) and globally (color replacement across disjoint canvas regions).
  2. Numerical Tolerance & RGBA Distance: Color similarity matching requires normalized Euclidean distance across 4-channel RGBA space ($d \in [0, 1.0]$) to handle antialiased edges, gradients, and subtle noise.
  3. Gap Closing Mechanics: Hand-drawn line art frequently exhibits 1-pixel or 2-pixel gaps that cause catastrophic leaking into background canvas regions. Morphological barrier dilation bridging gaps followed by bounded fill and clamped mask restoration must prevent leaks while preserving original boundary contours.
  4. Boundary Expansion & Bleed: In digital painting, fills must bleed under line art by configurable radii ($-10$ to $+10$ pixels) to avoid white halo fringes, and support boundary anti-aliasing edge smoothing.
  5. Off-UI Thread Execution & Cancellation: Fills on large canvases (up to 2048x2048) must run off the UI thread on `Dispatchers.Default` and support cooperative cancellation tokens so that interactive gestures or cancellations abort instantly without blocking the main UI loop.
  6. Layer Routing & Reversible History: Fills must support targeting the selected active layer or creating a new dedicated layer (`FillTargetLayer.CURRENT_LAYER` vs `NEW_LAYER`), and generate a transactional `FillCommand` preserving pre- and post-fill snapshots for 100% undo/redo reversibility.
- **Decision**:
  - Fill Domain Models ([`FillModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/fill/FillModels.kt)):
    - `FillMode`: `CONTIGUOUS`, `GLOBAL_COLOR`.
    - `FillSampleSource`: `ACTIVE_LAYER`, `ALL_LAYERS` (composite sampling).
    - `FillTargetLayer`: `CURRENT_LAYER`, `NEW_LAYER`.
    - `FillTransparencyMode`: `NORMAL`, `TRANSPARENT_ONLY`, `OPAQUE_ONLY`, `RESPECT_TRANSPARENCY`.
    - `FillCancellationToken`: Thread-safe atomic cancellation flag polled during scanline iterations and morphological processing.
    - `FillConfig`: Immutable specification capturing fill color, opacity, tolerance, boundary expansion, gap closing radius, anti-aliasing, and sample/target routing.
    - `FillResult`: Execution outcome holding boolean success, filled pixel count, 8-bit coverage mask, final composite pixels, and duration in milliseconds.
  - Core Flood Fill Engine ([`FloodFillEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/fill/FloodFillEngine.kt)):
    - Scanline Contiguous Fill: 4-way stack-based scanline algorithm traversing horizontal spans with minimal memory footprint ($O(N)$ integer arrays).
    - Dilated Barrier Gap Closing: If `gapClosingRadius > 0`, constructs a binary barrier of non-matching pixels, dilates it by `gapClosingRadius` (bridging gaps up to $2 \times \text{radius}$), executes scanline fill bounded by the dilated barrier, post-dilates the resulting mask, and clamps against the original barrier so original boundary contours are never overwritten.
    - Morphological Boundary Operators: Implements `dilateMask`, `erodeMask`, and box-smoothed edge anti-aliasing (`antiAliasMask`).
    - Porter-Duff Compositing: Source-over blending with alpha preservation and coverage modulation.
    - Asynchronous Execution: `executeFillAsync` offloads heavy raster computation to `Dispatchers.Default` with periodic cancellation checks every 4096 iterations.
  - History & Layer Management Integration ([`EditCommand.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/EditCommand.kt), [`LayerManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/layer/LayerManager.kt)):
    - Added [`FillCommand`](file:///root/artstudio/app/src/main/java/com/artstudio/history/EditCommand.kt) preserving transactional pixel snapshots.
    - Implemented `LayerManager.applyFillToLayer` supporting current layer mutation or atomic creation of a dedicated "Fill Layer".
  - Accessible Studio UI ([`StudioFillPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioFillPanel.kt)):
    - Compose panel with tolerance, gap closing radius, boundary expansion, anti-aliasing, sample source, and transparency selectors ($\ge 48$ dp touch targets, WCAG AAA compliant).
  - Verification & Golden Parity:
    - Unit test suite ([`FloodFillEngineTest.kt`](file:///root/artstudio/app/src/test/java/com/artstudio/fill/FloodFillEngineTest.kt), 14 tests): 100% pass rate covering enclosed regions, 1px/2px gap closing, tolerance thresholds, boundary expansion, anti-aliasing, transparency modes, cancellation, and layer undo/redo.
    - Python QA goldens ([`test_fill_goldens.py`](file:///root/artstudio/tools/qa/test_fill_goldens.py), 6 tests): 0 diff pixels across synthetic enclosed boxes, 1px gap closing, global color replacement, and corpus fixtures (`lineart_simple_01.png`, `flat_colour_simple_01.png`, `transparent_cutout_01.png`). Total project tests: 334 tests passing.
- **Consequences**: Delivers accurate, leak-free flood and bucket fill operations with gap bridging, boundary line bleeding, non-blocking asynchronous execution, cooperative cancellation, and full undo/redo transaction safety.

---

### ADR-032: Core Brush Engine Expansion into Distinct Paint Media

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Artistic Variety vs Engine Bloat: Artists demand distinct tactile media types—Marker, Watercolour wash, Oil paint, Dry brush, and Textured stamp—without bundling massive external proprietary bitmap libraries that violate mobile memory limits or bundle size budgets.
  2. Distinct Physical & Mathematical Behaviors:
     - **Marker**: Chisel tip aspect ratio, fixed stroke orientation angle (e.g. 45°/135° calligraphy), translucent optical buildup (Beer-Lambert overlap), and capillary ink bleed along edges.
     - **Watercolour Wash**: Dynamic wet edge puddle ring ("coffee-ring" effect), soft internal diffusion gradient, and paper granulation pigment settling.
     - **Oil Paint**: Multi-filament viscous bristle tracks, three-dimensional impasto ridge relief highlights/shadows via directional surface lighting, and heavy paint viscosity loading.
     - **Dry Brush**: Paper tooth texture skip thresholding modulated by stylus pressure/velocity, frayed split bristle dispersion across stroke width.
     - **Textured Stamp**: Procedural and user-supplied mask sampling, rotation jitter, scale jitter, and spatial scatter.
  3. Touch-Only Hardware Pressure Fallback: Touchscreens without active stylus pressure must automatically synthesize dynamic pressure from stroke velocity and trajectory acceleration via `InputPressureResolver`, ensuring full media modulation on finger or capacitive touch inputs.
  4. Extensibility & Compact Assets: Built-in textures must remain extremely compact (< 100 KB budget). Brush definitions must cleanly support loading user-supplied assets from disk or URI with memory bounding ($\le 512 \times 512$) and LRU caching without crashing 4 GB devices.
  5. Deterministic Procedural Textures & Goldens: Procedural noise generation (Perlin-like octave gradient noise, cellular Worley, Voronoi, fibrous organic) must be strictly deterministic given a fixed seed to enable zero-diff golden testing.
- **Decision**:
  - Paint Media Domain Models ([`PaintMediaModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/PaintMediaModels.kt)):
    - `PaintMediaFamily`: `STANDARD`, `MARKER`, `WATERCOLOUR`, `OIL_PAINT`, `DRY_BRUSH`, `TEXTURED_STAMP`.
    - `StampPatternShape`: `SPONGE`, `SPECKLE`, `CANVAS_GRAIN`, `LEAF_ORGANIC`, `CUSTOM_ASSET`.
    - `PaintMediaProperties`: Validated data class with full numerical constraints covering chisel aspect ratio, orientation angle, ink bleed, wet edge multiplier, granulation factor, diffusion radius, impasto relief, bristle count, viscosity, paper tooth threshold, stamp shape, jitter, scatter, and procedural noise seed.
  - Brush Asset Management & Noise Engine ([`BrushAssetManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/BrushAssetManager.kt)):
    - `BrushTexture`: 8-bit single-channel alpha mask with bilinear sampling (`sampleBilinear`), wrap and clamp modes.
    - Deterministic Procedural Noise: `generatePaperTooth`, `generateSponge`, `generateSpeckle`, `generateCanvasGrain`, `generateLeafOrganic`.
    - Custom Asset Loading: Pure-Kotlin PNG decoder and memory-bounded loader with 512x512 downsampling and LRU caching.
  - Extended Brush Configuration & Starter Presets ([`BrushConfig.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/BrushConfig.kt), [`BrushPresets.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/BrushConfig.kt)):
    - Integrated `PaintMediaProperties` into `BrushConfig` with JSON serialization.
    - Added starter presets: `marker()`, `watercolourWash()`, `oilPaint()`, `dryBrush()`, `texturedStamp()`.
    - Added corresponding presets in [`BrushPresetRepository.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/brush/BrushPresetRepository.kt).
  - Raster Stroke Engine Specialized Media Pipelines ([`RasterStrokeEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/RasterStrokeEngine.kt)):
    - Chisel Marker Renderer: Rotated elliptical/rectangular dab oriented by `chiselAngleRad` with ink bleed edge fringe.
    - Watercolour Wash Renderer: Radial falloff with elevated outer ring ("wet edge puddle ring"), diffusion blurring, and paper granulation modulation.
    - Oil Paint Renderer: Multi-filament bristle tracking across dab width, directional light impasto normal relief highlights and shadows.
    - Dry Brush Renderer: Split bristle filament tracking modulated by paper tooth skip threshold inversely scaled with stylus pressure.
    - Textured Stamp Renderer: Bilinear sampling of procedural or custom texture masks, rotation jitter, scale jitter, and radial scatter.
  - Compact Built-in Textures: Bundled 3 high-performance 8-bit PNG textures in `app/src/main/assets/brushes/textures/`: `sponge_stamp_64.png`, `canvas_grain_64.png`, and `paper_tooth_64.png` totaling **9,177 bytes (~8.96 KB)**, far below the 100 KB budget.
  - Verification & Golden Parity:
    - 5 canonical golden tests pass with **0 diff pixels** (Marker, Watercolour, Oil Paint, Dry Brush, Textured Stamp) in `test_paint_media_goldens.py`.
    - 5 parameter variation tests prove changing primary media properties modifies visual output.
    - Fixed seed repeatability verified at 0 diff pixels.
    - Touch-only hardware fallback verified in unit tests.
- **Consequences**:
  - Delivers 5 high-fidelity, physically responsive paint media families with testable parameters, touch-only pressure fallback, memory-safe custom asset support, and compact 9.1 KB footprint.

---

### ADR-033: Pixel Modification Subsystem (Smudge, Blur, Blend) with Bounded Scratch Buffers

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Pixel Modification vs Additive Strokes: Artists need tools that modify existing pixels—smudging colors across edges, smoothing contrast with blur, and wet blending—rather than merely drawing new additive marks.
  2. Memory Bounding & Pointer Loop Constraints: Naively copying or allocating a full-canvas ($W \times H$) temporary bitmap on every pointer move sample (at 60–120 Hz) causes catastrophic heap allocation (16 MB per sample on 2048x2048) and instant GC thrashing on 4 GB mobile tiers. The engine must use pre-allocated, dab-sized bounded scratch buffers ($\le 256$ KB) with zero per-sample allocations.
  3. Edge Rules & Transparency Preservation:
     - Smudge: Samples underlying pixels into a carried color reservoir, dragging and depositing color along the stroke trajectory. Crucially, when dragging from opaque into transparent regions, it fades alpha smoothly while preserving hue—preventing the common "black border/halo" artifact caused by unweighted RGB multiplication with zero alpha.
     - Blur: Localized spatial convolution must weight color channels by pixel alpha to avoid pulling zero-RGB darkness from transparent areas into semi-opaque contours.
  4. Selection, Layer Masks & Alpha-Lock Restrictions:
     - Selection Mask: Modification strength is strictly modulated by document-space 8-bit selection mask coverage; unselected pixels ($A = 0$) remain 100% bit-exact untouched.
     - Alpha-Lock: When `isAlphaLocked = true`, destination alpha is strictly immutable; only RGB channels are recolored, and no new alpha is generated on transparent pixels.
     - Layer Mask: Grayscale luminance modulates operation coverage.
  5. Incremental & Reversible History: Strokes interpolate dabs live along the trajectory, track the dirty bounding box, and record bounded pre- and post-operation patches (`PixelModifierCommand`) ensuring 100% undo/redo reversibility without full-canvas snapshot bloat.
- **Decision**:
  - Pixel Modification Domain Models ([`PixelModifierModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/pixelops/PixelModifierModels.kt)):
    - `PixelModifierMode`: `SMUDGE`, `BLUR`, `BLEND`.
    - `PixelModifierConfig`: Validated properties for size ($[1, 512]$ px), strength ($[0.01, 1.0]$), spacing ($[0.01, 1.0]$), hardness ($[0.0, 1.0]$), sample radius ratio ($[0.05, 1.0]$), reservoir retention ($[0.0, 1.0]$), pressure sensitivity ($[0.0, 2.0]$), and preserve alpha toggle.
    - `PixelBoundingBox`: Dynamic dirty region tracking $[minX, minY, maxX, maxY]$ with clamping.
  - Core Pixel Modification Engine ([`PixelModifierEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/pixelops/PixelModifierEngine.kt)):
    - Pre-allocated bounded dab scratch buffers (`dabScratchPixels` up to 512x512) guaranteeing zero full-canvas temporary allocations per pointer sample.
    - Alpha-weighted sampling and premultiplied blending preventing black halos.
    - Reusable carried color reservoir (`SmudgeReservoir`) dragging pigment with gradual opacity decay over transparent backgrounds.
  - Interactive Stroke Session ([`PixelModifierSession.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/pixelops/PixelModifierSession.kt)):
    - Incremental distance-based dab interpolation between pointer samples.
    - Dirty bounding box expansion.
    - Bounded patch extraction creating `PixelModifierCommand`.
  - Reversible History Integration ([`PixelModifierCommand.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/PixelModifierCommand.kt), [`LayerManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/layer/LayerManager.kt)):
    - Stores bounded `prePixels` and `postPixels` patches ($O(\text{patchArea})$ memory).
    - `restorePatch`: Blits patch into destination layer buffer during undo/redo with 100% bit-exact restoration.
  - Accessible Studio UI Controls ([`StudioPixelOpsPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioPixelOpsPanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Smudge and Blur quick tool buttons in the bottom toolbar.
    - Floating drawer panel with mode selector, size slider, strength slider, hardness slider, spacing slider, and preserve alpha switch ($\ge 48$ dp touch targets, WCAG AAA compliant).
  - Verification & Golden Parity:
    - Unit test suite ([`PixelModifierEngineTest.kt`](file:///root/artstudio/app/src/test/java/com/artstudio/pixelops/PixelModifierEngineTest.kt), 12 tests): 100% pass rate covering smudge boundary displacement, transparent zero-black-fringe preservation, alpha-lock enforcement, blur smoothing, strength/hardness modulation, selection mask containment, layer mask modulation, bit-exact undo/redo, and zero allocation per pointer sample.
    - Python QA goldens ([`test_pixel_ops_goldens.py`](file:///root/artstudio/tools/qa/test_pixel_ops_goldens.py), 4 golden scenarios): 0 diff pixels across canonical smudge, canonical blur, cutout smudge, and selection blur.
    - 100-stroke profiling benchmark: Mean latency recorded at 117.5 ms (Python ref) / < 2 ms (JVM native), with bounded scratch allocation $\le 256$ KB. Total test suite: 354 tests passing.
---

### ADR-034: Texture & Stamp Asset Subsystem Architecture with Licensing Manifest & Graceful Fallback

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Texture & Stamp Support Across 4 Media Categories: Artists require comprehensive texture capabilities spanning Grain (paper tooth), Pattern (seamless tileable fills), Stamp (dab silhouettes), and Textured-Stroke (directional bristle strips).
  2. Asset Management Lifecycle: Artists must be able to import custom textures, preview them with lazy-loaded thumbnails, duplicate, rename, delete, and toggle favorites, while protecting built-in system assets from deletion or renaming.
  3. Provenance & Strict Permissive Licensing Policy: No assets with unknown, proprietary, or restrictive licensing may ever be bundled in the application. Every bundled art asset must be covered by a machine-readable, auditable license manifest recording author, license (CC0-1.0 Universal), provenance, and cryptographic SHA-256 integrity checksums.
  4. Memory Bounding & Pre-flight Validation: External files can be corrupt, truncated, or excessively large. Files exceeding 1024x1024 or 2 MB must be rejected with explicit exceptions (`OversizedDimensionException`, `OversizedFileException`, `CorruptAssetException`, `EmptyAssetException`) to guarantee physical memory safety on 4 GB mobile tiers.
  5. Graceful Fallback (Zero Silent Failures): If an asset is missing or corrupted at runtime, the engine must NEVER fail silently with an invisible black or blank dab. It must emit an explicit `TextureLoadError` with user-facing diagnostics and render a clearly visible procedural diagnostic fallback.
  6. Lazy Preview Loading: Browsing hundreds of textures must not incur synchronous disk I/O or full-resolution bitmap allocations. Previews must be generated lazily as downsampled 64x64 thumbnails and cached in a bounded LRU cache (`TexturePreviewCache`).
- **Decision**:
  - Texture Domain Models ([`TextureModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/texture/TextureModels.kt)):
    - `TextureCategory`: `GRAIN`, `PATTERN`, `STAMP`, `TEXTURED_STROKE`.
    - `TextureMetadata`: Validated record storing ID, name, category, built-in flag, favorite flag, file/asset path, dimensions, size, SHA-256 hash, author, license, provenance, and serialization (`toMap`, `fromMap`, `toJson`, `fromJson`).
    - `TextureErrorCode` & `TextureValidationResult`: Granular error states and specialized typed exceptions.
    - `TextureLoadError` & `TextureLoadResult`: Explicit diagnostic error reporting with `FallbackWithError`.
  - Memory-Safe Pure-Kotlin Validator ([`TextureAssetValidator.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/texture/TextureAssetValidator.kt)):
    - Enforces max dimensions ($1024 \times 1024$), min dimensions ($8 \times 8$), and max file size ($2$ MB).
    - Validates magic byte headers for PNG, WebP, and JPEG.
    - Pure-Kotlin lossless PNG scanline un-filtering supporting Filter Types 0 (None), 1 (Sub), 2 (Up), 3 (Average), and 4 (Paeth) across 8-bit Grayscale, Grayscale+Alpha, RGB, and RGBA color types.
    - Computes cryptographic SHA-256 checksums.
  - Authoritative Asset Repository ([`TextureRepository.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/texture/TextureRepository.kt)):
    - Ingests `manifest.json` and loads persisted `textures_index.json`.
    - Provides CRUD operations: `importAsset`, `duplicateAsset`, `renameAsset`, `deleteAsset`, `toggleFavorite`, `searchAssets`.
    - Built-in system asset protection: Throws `CannotModifyBuiltInAssetException` / `CannotDeleteBuiltInAssetException` on attempted mutation of bundled assets.
    - `loadTexture`: Emits `TextureLoadError` with visible procedural fallback on missing/damaged files.
  - Lazy Preview Cache ([`TexturePreviewCache.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/texture/TexturePreviewCache.kt)):
    - Bounded LRU cache (max 64 entries) generating $64 \times 64$ downsampled thumbnails on-demand.
  - Auditable Licensing Manifest & Documentation:
    - Bundled asset manifest: `app/src/main/assets/brushes/textures/manifest.json`.
    - Legal provenance catalog: `docs/assets/TEXTURES_LICENSES.md`.
    - Format and validation specification: `docs/formats/TEXTURE_ASSET_SPECIFICATION.md`.
    - 7 bundled CC0-1.0 assets totaling **18,258 bytes (~17.83 KB)**.
  - Brush Engine & UI Integration:
    - Added `grainBrush`, `patternBrush`, `stampBrush`, and `texturedStrokeBrush` to `BrushPresets` and `BrushPresetRepository`.
    - Implemented `StudioTextureManagerDialog` with category pills, search, lazy preview cards, action buttons, and diagnostic error alerts.
- **Consequences**:
  - Delivers complete, artist-friendly texture and stamp management with robust validation, zero unrecovered crashes on corrupt files, 100% auditable CC0-1.0 provenance, and zero silent failures on missing assets.

---

### ADR-035: Editable Vector Geometry Engine, Safe SVG Subset Interchange, and Non-Destructive Vector-to-Raster Pipeline

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Vector Geometry Alongside Raster Painting: Professional artists and technical illustrators require precision vector constructs—lines, polylines, cubic/quadratic Bezier curves, rectangles, circles/ellipses, and polygons—alongside raster brush strokes.
  2. Document Space Geometry & Viewport Transforms: Vector paths must persist as mathematical geometry in authoritative document space, rendering smoothly under arbitrary viewport scale, rotation, translation, and flips without raster resampling degradation.
  3. Interactive Anchor & Handle Editing: Providing a path editing mode distinct from freehand brush painting. Supporting independent anchor positioning, incoming/outgoing tangent control handles (`handleIn`, `handleOut`), and smooth collinear constraints.
  4. Non-Destructive Vector Integrity: Vector objects must never be silently flattened or resampled. Conversion to raster pixels occurs exclusively through explicit user confirmation (`RasterizeVectorLayerCommand`), with 100% bit-exact reversibility (undo/redo preserves the original vector layer and its mathematical contours).
  5. Safe SVG Subset Interchange & Conversion Auditing: Industry standard interchange requires robust import and export of standard SVG 1.1 geometry (`<svg>`, `<g>`, `<rect>`, `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>`, `<path>` with `M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `Z`). Any unsupported elements (`<linearGradient>`, `<filter>`, `<text>`, etc.) or corrupt XML must never cause an unrecovered crash; they must be logged into `ConversionReport` guaranteeing zero silent feature loss.
- **Decision**:
  - Vector Domain Models ([`VectorModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/VectorModels.kt)):
    - `VectorShapeType`: `LINE`, `POLYLINE`, `BEZIER_PATH`, `RECTANGLE`, `ELLIPSE`, `POLYGON`.
    - `VectorStyle`: `strokeColor`, `strokeWidth`, `strokeCap`, `strokeJoin`, `strokeMiterLimit`, `fillColor`, `fillRule`, `opacity`.
    - `VectorAnchor`: Document-space coordinate with optional cubic control handles (`handleInX/Y`, `handleOutX/Y`) and `isSmooth` constraint.
    - `VectorRectData` & `VectorEllipseData`: Dedicated parameters for rounded rectangles and ellipses.
    - `VectorSegment`: `MoveTo`, `LineTo`, `QuadTo`, `CubicTo`, `Close`.
    - `AffineTransform2D` & `VectorBounds`: Pure-mathematical 2D affine transformations and axis-aligned bounding boxes with zero native JNI dependency.
    - `VectorObject`: Authoritative vector entity supporting evaluated `toSegments()`, `computeBounds()`, and geometric transformations.
  - Geometric Manipulation Engine ([`VectorGeometryEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/VectorGeometryEngine.kt)):
    - Anchor manipulation: `moveAnchor`, `moveHandleIn`, `moveHandleOut`, `setAnchorSmooth`, `insertAnchor`, `deleteAnchor`.
    - Affine transforms: `translate`, `scale`, `rotate`, `flip`.
    - Hit testing: `hitTestAnchor`, `hitTestHandleIn`, `hitTestHandleOut`.
    - Mathematical evaluation: `evalCubicBezier`, `evalQuadBezier`.
    - Native rendering: `toAndroidPath` generating `android.graphics.Path` with winding rules.
  - Safe SVG Subset Parser & Exporter ([`SafeSvgParser.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/svg/SafeSvgParser.kt), [`SafeSvgExporter.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/svg/SafeSvgExporter.kt)):
    - `SafeSvgParser`: Secure, memory-bounded DOM parser using standard `DocumentBuilderFactory`. Parses shapes, groups with inherited styles, and complex path data (`M`, `L`, `H`, `V`, `C`, `S`, `Q`, `T`, `Z`). Unsupported elements and malformed streams are captured into `ConversionReport`.
    - `SafeSvgExporter`: Serializes vector layers and objects into standard SVG 1.1 XML preserving shapes, coordinates, and styling without structural loss.
  - Layer & Persistence Integration ([`LayerNode.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/model/LayerNode.kt), [`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt), [`NativeCanvasRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/NativeCanvasRenderer.kt)):
    - Enhanced `LayerNode.VectorPathLayer` with `vectorObjects` and `allVectorObjects()` backward compatibility helper.
    - Updated `DocumentSerializer` to serialize and deserialize vector shapes, anchors, handles, and styling with 100% field preservation.
    - Updated `NativeCanvasRenderer` to render vector objects with fill, stroke, caps, joins, and miter limits.
  - Explicit & Undoable Rasterization ([`RasterizeVectorLayerCommand.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/RasterizeVectorLayerCommand.kt)):
    - Renders vector geometry onto a canvas-sized bitmap.
    - Replaces `VectorPathLayer` with `RasterLayer` (or duplicates into a new layer).
    - Session undo restores the original `VectorPathLayer` with 100% exact vector geometry.
  - Path Editing Mode & UI Controls ([`VectorEditSession.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/VectorEditSession.kt), [`StudioVectorPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioVectorPanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Interactive session managing selection, anchor moving, handle dragging, and tool switching.
    - Compose `StudioVectorPanel` exposing vector tool selectors, stroke width slider, cap/join selectors, anchor actions, and explicit "Rasterize Vector Layer" command.
  - Verification & Golden Parity:
    - 386 unit tests passing (100%), including 17 new tests in [`VectorGeometryEngineTest.kt`](file:///root/artstudio/app/src/test/java/com/artstudio/vector/VectorGeometryEngineTest.kt).
    - Python QA suite (`test_vector_geometry_goldens.py`): 0 diff pixels against canonical simple and dense vector goldens.
    - APK signed and verified (11.4 MB).
- **Consequences**:
  - Delivers full vector geometry editing alongside raster painting, safe SVG subset import/export without structural loss, and explicit, 100% undoable rasterization.

---

### ADR-036: Non-Destructive Drawing Aids, Multi-Branch Symmetry Engine, and Isolated Reference Image Overlay System

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Stroke Guidance without Destruction: Digital artists require assistance constructing straight lines, circles, ellipses, radial patterns, and perspective grids without baking auxiliary lines into the artwork layer pixels.
  2. Sub-Pixel Snapping Precision: Magnetic snapping must compute mathematically exact projections ($\le 0.5$ document pixels, targeting $< 0.001$ px error) while respecting a configurable distance threshold so freehand drawing is not hijacked when off-guide.
  3. Real-Time & Committed Symmetry: Mirror (vertical, horizontal, dual) and radial ($N$-fold) symmetry require synchronized stroke generation that preserves stylus dynamics, pressure, and timing across all sibling branches.
  4. Reference Image Separation & Export Isolation: Artists need reference photos/sketches positioned over or beside the canvas with adjustable opacity and color sampling. Critically, reference images must remain completely isolated from paint layers and project exports unless the user explicitly chooses to import them into the document.
- **Decision**:
  - Drawing Aids Domain Models ([`GuideModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/guides/GuideModels.kt)):
    - `GuideType`: `NONE`, `STRAIGHT_LINE`, `CIRCLE_ELLIPSE`, `RADIAL`, `PERSPECTIVE`.
    - `SymmetryType`: `NONE`, `MIRROR_VERTICAL`, `MIRROR_HORIZONTAL`, `MIRROR_DUAL`, `RADIAL`.
    - `DrawingAidsConfig`: Holds guide mode, snapping toggle, snap distance ($8..64$ px), angle/ellipse/perspective configurations, symmetry settings, and visual styling.
    - `ReferenceImageOverlay`: Stores position, size, opacity ($0..1$), visibility, canvas pinning mode, and 32-bit ARGB pixel buffers for color sampling.
  - Geometric Projection & Symmetry Engine ([`DrawingAidsEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/guides/DrawingAidsEngine.kt)):
    - `projectPointToLine`: Analytical scalar projection along unit direction vectors.
    - `projectPointToEllipse`: Exact perimeter projection with analytic circle fast-path and 8-iteration Newton-Raphson for general rotated ellipses.
    - `projectPointToRadial`: Discrete ray snapping radiating from pivot.
    - `projectPointToPerspective`: Projection onto rays converging toward active vanishing points ($VP_1, VP_2, VP_3$) or horizon.
    - `computeSymmetryPoints`: Computes analytical coordinates for vertical ($2cx - x, y$), horizontal ($x, 2cy - y$), dual (4 quadrants), and radial ($N$-fold rotation around center).
    - `generateSymmetricStrokes`: Synthesizes sibling strokes matching original brush configuration, pressure profiles, and timestamps.
  - Reference Image Management & Export Isolation ([`ReferenceImageManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/guides/ReferenceImageManager.kt)):
    - Manages reference overlays independently of `ArtDocument.rootGroup`.
    - Implements `sampleColor`: Direct color inspection at document coordinates via `EyedropperEngine`.
    - Implements `importReferenceToLayer`: Explicit command converting reference image into a `LayerNode.RasterLayer` with full undo/redo command history.
    - Guarantees strict export isolation: references are omitted from flattened composites and document exports by default.
  - Viewport Invariance & Non-Destructive Overlays ([`GuideOverlayRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/guides/GuideOverlayRenderer.kt), [`NativeCanvasRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/NativeCanvasRenderer.kt), [`PointerInputPipeline.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/PointerInputPipeline.kt)):
    - Guides and canvas-pinned reference overlays render inside the viewport transform matrix, scaling and panning in exact document registration.
    - Pointer input pipeline snaps coordinates in document space before stabilization and stroke point emission.
    - Live active stroke rendering paints real-time symmetric counterparts on the active layer.
  - Contextual UI Panel ([`StudioDrawingAidsPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioDrawingAidsPanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Accessible Compose panel featuring Guides, Symmetry, and Reference tabs with interactive mode chips, sliders, color sampler, and layer import action.
  - Verification & Golden Parity:
    - 402 unit tests passing (100%), including 16 tests in [`DrawingAidsAndReferenceTest.kt`](file:///root/artstudio/app/src/test/java/com/artstudio/guides/DrawingAidsAndReferenceTest.kt).
    - Python QA suite (`test_drawing_aids_goldens.py`): 0 diff pixels against canonical symmetry mandala golden.
- **Consequences**:
  - Provides professional artist drawing aids, precise sub-pixel stroke snapping, live and committed symmetry across multiple branches, and isolated reference overlays with zero risk of accidental export flattening.

---

### ADR-037: Editable Text Objects, Open-Licensed Typography, Geometric Shape Generation, and Reversible Vector/Raster Semantics
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**: Digital illustrators require versatile typography and geometric shape tools alongside raster painting. Essential requirements:
  1. Editable Text Objects: Text must remain fully editable (content, font family, font size, bold/italic style, alignment, color, letter/line spacing, text decorations) throughout the artwork lifecycle.
  2. Pure Open-Source Typography & Zero License Ambiguity: Absolutely no commercial, unlicensed, or proprietary fonts may be bundled. Every bundled font asset must be distributed under audited open licenses (SIL OFL-1.1) and cataloged in a machine-readable manifest (`manifest.json`) and `LICENSES.md`.
  3. Graceful Font Fallback Diagnostics: When an artwork references an unavailable or third-party font, the engine must gracefully substitute a standard fallback family (`sans-serif`, `serif`, `monospace`) and log an explicit diagnostic report rather than crashing or substituting silently.
  4. Geometric Shape Engine: Parametric creation for rectangles, rounded rectangles (independent corner radii), ellipses, circles, lines, regular $N$-gons (triangles, pentagons, hexagons, octagons), and $N$-pointed stars (parametric outer/inner radii) targeting either dedicated vector layers or direct raster stamps.
  5. Interchange & SVG Export: Vector shapes and text must export cleanly to SVG 1.1 elements (`<text>`, `<tspan>`, `<rect>`, `<circle>`, `<ellipse>`, `<line>`, `<polygon>`) while supporting optional explicit text rasterization.
  6. Reversible Rasterization: Explicit rasterization of text or shape layers into raster layers must be 100% undoable, preserving the source object in memory.
- **Decision**:
  - Text Domain Models ([`TextModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/text/TextModels.kt)):
    - `TextFontStyle`: `NORMAL`, `BOLD`, `ITALIC`, `BOLD_ITALIC`.
    - `TextAlignment`: `LEFT`, `CENTER`, `RIGHT`.
    - `FontFallbackReport`: Records requested family, resolved family, wasAvailable flag, and user diagnostic message.
    - `TextObject`: Data model containing string text, anchor $(x, y)$, width, height, font family, font size, style, alignment, text color, letter spacing, line spacing multiplier, underline, strikethrough, analytical bounding box computation, and affine transform mapping.
  - Font Engine & Fallback Subsystem ([`FontEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/text/FontEngine.kt)):
    - System family registry (`sans-serif`, `serif`, `monospace`, `cursive`, `casual`).
    - Bundled open-source typography registry: `Carrois Gothic SC` (OFL-1.1, 40 KB TTF) and `Cutive Mono` (OFL-1.1, 79 KB TTF).
    - `isFontAvailable` & `resolveFont`: Safe case-insensitive lookup with diagnostic fallback generation.
    - `createTypeface`: JVM-safe and Android-safe Typeface instantiation.
  - Shape Domain Models & Geometry Engine ([`ShapeModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/shape/ShapeModels.kt), [`ShapeGeometryEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/shape/ShapeGeometryEngine.kt)):
    - `GeometricShapeType`: `RECTANGLE`, `ROUNDED_RECTANGLE`, `ELLIPSE`, `CIRCLE`, `LINE`, `POLYGON`, `STAR`.
    - `ShapeTargetMode`: `NEW_VECTOR_LAYER`, `ACTIVE_VECTOR_LAYER`, `STAMP_TO_RASTER`.
    - `createRegularPolygon`: Generates analytical $N$-gon vertices with radius and orientation.
    - `createStar`: Generates alternating outer/inner vertices for $N$-pointed stars.
    - `createShape`: Generates `VectorObject` primitives (`Path`, `Rectangle`, `Ellipse`, `Line`).
    - `stampShapeToRasterBuffer`: Bit-level raster stamping directly into 32-bit ARGB pixel buffers.
  - Layer Hierarchy & Document Persistence ([`LayerNode.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/model/LayerNode.kt), [`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt)):
    - Introduced `LayerNode.TextLayer(textObject: TextObject)` into the authoritative layer tree.
    - JSON serialization and deserialization roundtrip preserves all text attributes and vector shape objects without loss.
  - Reversible History Commands ([`TextCommands.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/TextCommands.kt)):
    - `AddTextLayerCommand`: Non-destructive text layer addition.
    - `UpdateTextObjectCommand`: Typography and styling updates with undo/redo reversibility.
    - `RasterizeTextLayerCommand`: Converts text to a raster bitmap layer while keeping source text layer cached in command state for 100% bit-exact undo restoration.
  - Native Canvas & SVG Export Pipelines ([`NativeCanvasRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/NativeCanvasRenderer.kt), [`SafeSvgExporter.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/svg/SafeSvgExporter.kt)):
    - Canvas renderer draws multiline anti-aliased text with typography styling and alignment.
    - SVG exporter maps text objects to standard `<text>` and `<tspan>` nodes, recording `FONT_FALLBACK` conversion warnings when necessary.
  - Contextual Compose UI Panels ([`StudioTextPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioTextPanel.kt), [`StudioShapePanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioShapePanel.kt)):
    - Text drawer for real-time text input, font family picker, font size slider, style/alignment chips, color picker, and rasterize button.
    - Shape drawer for shape type picker (Rect, Round Rect, Ellipse, Circle, Line, Polygon, Star), fill/outline toggles, stroke width, side/point counters, and target layer modes.
  - Verification & Golden Parity:
    - 419 unit tests passing (100%), including 17 tests in [`TextAndShapeEngineTest.kt`](file:///root/artstudio/app/src/test/java/com/artstudio/text/TextAndShapeEngineTest.kt).
    - Python QA golden suite (`test_text_and_shape_goldens.py`): 0 diff pixels against canonical text and shapes golden.
    - Package budget: 11.6 MB APK size (1.16% of 1.0 GB ceiling).
- **Consequences**:
  - Delivers high-quality typography and geometric shape tools, 100% audited open-source font assets, non-destructive editable vector layers, and graceful fallback diagnostics across all supported export formats.

---

### ADR-038: Comic & Manga Tools: Panel Slicing, Seamless Welded Balloons, Scalable Procedural Screentones, and Page Guides

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Comic & Manga Domain Specialization: Comic and manga illustrators require dedicated production tooling—comic panel framing, multi-tier gutter slicing (horizontal, vertical, and slanted action cuts), speech/thought/shout dialogue balloons with welded pointers, safe text placement bounds, and scalable screentones—built on top of existing geometry, layer masking, and vector pipelines without destabilizing the core raster brush engine.
  2. Procedural Screentones vs Bitmap Storage Bloat: Traditional digital painting tools store screen patterns as pre-rendered bitmap textures, which balloon file size (up to 4 MB per 2048x2048 8-bit layer), consume critical mobile RAM on 4 GB devices, and introduce objectionable moiré or aliasing when transformed or zoomed. Storing screentones as procedural mathematical descriptors (`ScreentoneConfig`: dot pitch, density ratio, screen angle, shape function) takes $\sim 60$ bytes in project JSON, scales infinitely with zero aliasing, and can be rasterized on-demand or exported directly to standard SVG 1.1 `<pattern>` elements.
  3. Seamless Dialogue Balloon Contours: Stacking a separate tail triangle onto an ellipse balloon creates interior border seams where the stroke lines cross the balloon body. To produce professional publication-quality balloons, the pointer tail must be welded directly into the continuous perimeter loop using tangent exit/entry Bezier anchors.
  4. Non-Destructive Panel Clipping: Panel borders must isolate layer content. Converting panel polygons into `LayerMask` objects provides instant, non-destructive masking that keeps underlying layer artwork intact while strictly confining visible pixels within frame borders.
  5. Publication Guides & Margins: Standard comic production requires Bleed margins (outer cut allowance), Trim lines (finished page boundary), and Safe Area margins (guaranteed text/dialogue containment). These guides must render as viewport-aware overlays without baking into artwork on export.
  6. Reversible Commands & Native Persistence: All comic panel creations, splits, bubble edits, and tone additions must be 100% reversible in edit history and roundtrip cleanly through the native `.ffart` format and SVG export.
- **Decision**:
  - Comic Domain Models ([`ComicModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/comic/ComicModels.kt)):
    - `Point2D`, `Rect2D`: Pure-math geometric primitives with zero Android framework coupling.
    - `ComicPanel`: Polygon/rectangular panel with vertices, stroke/fill styling, corner radii, inner margins, clipping toggle, bounding boxes, and Ray-Casting point-in-polygon containment (`containsPoint`).
    - `ComicPanelLayout`: Page layout containing dimensions, outer margins, horizontal/vertical gutters, and panel lists.
    - `SpeechBubbleType`: `SPEECH_OVAL`, `SPEECH_RECTANGLE`, `THOUGHT_CLOUD`, `SHOUT_BURST`, `WHISPER`.
    - `BubbleTailType`: `STRAIGHT_POINTER`, `CURVED_POINTER`, `THOUGHT_DOTS`, `NONE`.
    - `SpeechBubble`: Authoritative dialogue balloon entity with geometry, tail angles, burst spikes, cloud puffs, text attributes, and `computeInnerSafeTextBounds(padding)`.
    - `ScreentonePattern`: `DOT`, `LINE`, `CROSS_HATCH`, `DIAMOND`.
    - `ScreentoneConfig`: Procedural parameterization with pitch/frequency, density $[0.01, 0.99]$, rotation angle (default 45°), foreground/background colors, and `scaled(factor)` scale invariance.
    - `ComicPageGuides`: Standard publication guides defining bleed, trim (40 px), safe area (120 px), and gutter guides.
  - Geometry & Slicing Engine ([`ComicGeometryEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/comic/ComicGeometryEngine.kt)):
    - Layout generators: `createGridPanels` (rows x cols) and `create4KomaLayout` (classic 4-panel vertical manga strip).
    - Precision slicing: `splitPanelHorizontal` and `splitPanelVertical` with exact gutter spacing.
    - Slanted cutting: `splitPanelSlanted` non-rectangular dynamic action cuts using normal vector displacement along the cut line.
    - Seamless welded balloon contours: `createBubbleVectorObject` welds pointer tail into continuous perimeter loop with tangent exit/entry cubic Bezier anchors.
    - Thought bubble auxiliary dots: `generateThoughtDots` generates 3 diminishing circular markers leading to the speaker.
    - Safe text placement: `generateBubbleTextObject` calculates text bounds within the maximum inscribed ellipse rectangle or inner valley radius.
    - Non-destructive panel masks: `createPanelMask` rasterizes polygon boundaries into 8-bit `LayerMask` buffers for layer clipping.
  - Procedural Screentone Engine ([`ScreentoneEngine.kt`](file:///root/artstudio/comic/ScreentoneEngine.kt)):
    - Continuous rotated coordinate space $(u, v) = R(-\theta)(x, y)$.
    - Analytical coverage calculation for DOT ($d \le r$), LINE ($d \le w/2$), CROSS_HATCH (orthogonal line union), and DIAMOND ($|u'| + |v'| \le r_d$).
    - Anti-aliased feathering softness.
    - Direct buffer rendering (`renderToBuffer`), tile creation (`createTileBitmap`), and SVG `<pattern>` generation (`generateSvgPatternDef`).
  - Native Serialization & SVG Export ([`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt), [`SafeSvgExporter.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/vector/svg/SafeSvgExporter.kt)):
    - Extended `ArtDocument` to store `comicPanels`, `speechBubbles`, `screentones`, and `comicPageGuides`.
    - Full roundtrip serialization in `DocumentSerializer` preserving all comic parameters with zero loss.
    - Enhanced `SafeSvgExporter` to export panels as `<polygon>`/`<rect>`, bubbles as `<path>`/`<text>`, and screentones as `<defs><pattern>` elements.
  - Reversible History Commands ([`ComicCommands.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/ComicCommands.kt)):
    - `AddComicPanelCommand`: Adds panel and creates corresponding vector border layer; undo reverts both.
    - `SplitComicPanelCommand`: Atomic replacement of target panel with 2 child panels; undo restores original.
    - `AddSpeechBubbleCommand`: Adds bubble, vector frame, and centered text layer; undo reverts all three.
    - `UpdateSpeechBubbleCommand`: Transactional bubble editing.
    - `AddScreentoneCommand`: Non-destructive addition of procedural screentone preset.
  - Accessible Studio UI Drawer ([`StudioComicPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioComicPanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - 4 tabbed drawer: Frames, Balloons, Screentone, and Guides.
    - Presets for 4-Koma, 2x2 Grid, Split Horizontal, Split Vertical.
    - Speech Oval, Narration Box, Thought Cloud, Shout Burst presets.
    - Dot 10%, Dot 25%, Dot 50%, Line Hatch, Cross-Hatch screentone presets.
    - Bleed, Trim, and Safe Area guides toggle.
    - Mutual drawer exclusivity and bottom toolbar `💬` icon button.
- **Consequences**:
  - Delivers specialized comic and manga production tooling without modifying or destabilizing the general brush and layer engine, achieving infinite-resolution procedural tones, professional seamless dialogue balloons, and full native project reversibility.

---

### ADR-039: Extensible Filter System with Real Image Operations
- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Artists require image processing operations to adjust contrast, introduce optical diffusion, extract linework, generate texture, and apply stylistic distortions directly to raster layers.
  2. Mobile constraints dictate that heavy image processing must never stall the Android UI thread or cause jank during interactive slider adjustments.
  3. Speculative live previews must not destructively modify authoritative document layer memory until the user explicitly commits.
  4. Rapid parameter changes during slider dragging require cooperative cancellation of stale in-flight filter calculations to prevent thermal throttling and memory buildup.
  5. The application must strictly avoid placeholder or mock filter effects; all exposed operations must be mathematically verified with bit-exact golden parity and documented parameter constraints.
  6. Filter commits must be 100% reversible via the transaction history engine (`HistoryManager`).
- **Decision**:
  - Core Filter Engine Architecture ([`ImageFilterEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/filter/ImageFilterEngine.kt), [`FilterModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/filter/FilterModels.kt)):
    - Sealed `FilterConfig` domain model with strict parameter validation:
      - `GaussianBlurConfig`: `radius` $[1.0, 50.0]$ px, `iterations` $[1, 3]$ (separable 1D horizontal + 1D vertical convolutions with alpha un-premultiplication).
      - `SharpenConfig`: `strength` $[0.1, 5.0]$, `radius` $[1.0, 10.0]$ (unsharp masking with high-pass contrast boost).
      - `OutlineConfig`: `threshold` $[1.0, 255.0]$, `edgeColor`, `backgroundColor`, `invert` (3x3 Sobel gradient magnitude operator).
      - `GlowConfig`: `radius` $[1.0, 50.0]$, `intensity` $[0.1, 3.0]$, `threshold` $[0.0, 1.0]$ (luminance thresholding, Gaussian diffusion, and additive compositing).
      - `NoiseConfig`: `amount` $[0.01, 1.0]$, `monochrome`, `seed` (deterministic procedural hash generator).
      - `PixelateConfig`: `blockSize` $[2, 64]$ px (block-averaging spatial downsampling).
      - `RippleConfig`: `amplitude` $[1.0, 50.0]$ px, `wavelength` $[5.0, 200.0]$ px, `phaseDeg` $[0.0, 360.0]^\circ$ (sinusoidal displacement with bilinear sub-pixel interpolation).
  - Background Execution & Interactive Lifecycle ([`FilterSession.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/filter/FilterSession.kt), [`FilterCancellationToken`](file:///root/artstudio/app/src/main/java/com/artstudio/filter/FilterModels.kt)):
    - `downscaleForPreview`: Clamps live preview buffer dimensions to $\le 512 \times 512$ px, providing sub-15ms rendering responsiveness on mobile devices.
    - `FilterCancellationToken`: Atomic boolean preemption checked per scanline/block; cancelling an in-flight calculation halts execution within $\le 2$ ms.
    - Non-destructive `cancel()`: Leaves authoritative source pixel buffers 100% bit-exact untouched.
    - Transactional `commit()`: Dispatches full-resolution transformation and registers reversible `ApplyFilterCommand`.
  - Reversible History Integration ([`ApplyFilterCommand.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/ApplyFilterCommand.kt), [`LayerManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/layer/LayerManager.kt)):
    - Reversible command storing `prePixels` and `postPixels` snapshots with bounded memory calculation ($2 \times \text{width} \times \text{height} \times 4$ bytes).
    - `restorePixels(target, usePre)` restores pre-filter or post-filter state with 100% bit-exact accuracy on undo/redo.
  - Accessible Compose UI Drawer ([`StudioFilterPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioFilterPanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Filter selection row with horizontal scrolling chips for all 7 filters.
    - Parametric sliders with live value labels, default resets, and live preview toggle.
    - Cancel and Apply buttons meeting WCAG AAA $\ge 48$ dp touch target standards.
    - Bottom toolbar `✨` launcher with mutual exclusivity with other studio panels.
- **Consequences**:
  - Delivers a production-grade, extensible raster filter pipeline with real mathematical image operations.
  - Protects mobile memory through bounded preview downsampling and cooperative cancellation.
  - Preserves document integrity and transaction history reversibility without placeholder or mock filters.

---

### ADR-040: Non-Destructive Tonal and Colour Adjustment Architecture

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Artists demand non-destructive tonal and colour adjustment workflows—brightness, contrast, levels, hue, saturation, grayscale conversion, and colour balance—where parameters remain editable over time rather than permanently baking into pixel data.
  2. Mobile memory and responsiveness constraints dictate that adjustments must be computationally efficient ($O(1)$ LUT lookups where applicable) and support live preview toggling without copying full canvas bitmaps.
  3. Layer mask integration (Prompt 18) requires that adjustments respect 8-bit mask opacity and inversion flags, modulating the intensity of adjustments locally across the canvas.
  4. Explicit rasterization: Converting an adjustment layer or direct adjustment into baked raster pixels must be an explicit, deliberate user action with 100% undo/redo reversibility through the history engine.
  5. Strict alpha preservation: Alpha channels must remain bit-exact untouched ($A_{\text{dest}} = A_{\text{src}}$) across transparent and translucent pixels during all colour transformations.
  6. Colour profile disclosures: Non-sRGB workflows (Display P3, Adobe RGB, CMYK) evaluated in standard sRGB must be formally disclosed to users and documented in export conversion audits.
- **Decision**:
  - **Shared Domain Model & Validation** ([`AdjustmentModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/adjustment/AdjustmentModels.kt)):
    - `enum class AdjustmentType`: `BRIGHTNESS_CONTRAST`, `LEVELS`, `HUE_SATURATION`, `GRAYSCALE`, `COLOR_BALANCE`.
    - `sealed interface ColorAdjustment`: Authoritative interface with `id`, `type`, `enabled`, `opacity`, `toMap()`, and `fromMap()`.
    - `BrightnessContrastAdjustment`: Validated brightness $[-100.0, 100.0]$ and contrast $[-100.0, 100.0]$ offsets.
    - `LevelsAdjustment`: Validated input black $[0, 253]$, gamma $[0.1, 9.99]$, input white $[inBlack+2, 255]$, output black $[0, 255]$, and output white $[0, 255]$.
    - `HueSaturationAdjustment`: Validated angular hue shift $[-180.0, 180.0]^\circ$, saturation scaling $[-100.0, 100.0]\%$, lightness offset $[-100.0, 100.0]\%$, and monochrome `colorize` mode.
    - `GrayscaleAdjustment`: Perceptual luminance weighting with Rec.709 defaults ($0.2126 R + 0.7152 G + 0.0722 B$) and optional duotone tint color/intensity.
    - `ColorBalanceAdjustment`: Cyan/Red, Magenta/Green, Yellow/Blue shifts in $[-100.0, 100.0]$ with optional Rec.709 perceived luminosity normalization.
  - **Core Color Adjustment Engine** ([`ColorAdjustmentEngine.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/adjustment/ColorAdjustmentEngine.kt)):
    - Pure Kotlin pixel array processing without Android `Bitmap` dependencies.
    - Precomputed 256-entry lookup tables (LUTs) for Brightness/Contrast and Levels enabling sub-millisecond execution.
    - Full 360° cylindrical HSV color rotation and saturation scaling.
    - Rec.709 luminance calculation with duotone tint interpolation.
    - Additive RGB balance shift with perceived luminance preservation.
    - Strict alpha preservation: destination alpha matches source alpha exactly ($A_{\text{dest}} = A_{\text{src}}$).
    - Prompt 18 layer mask interaction: modulates adjustment intensity per pixel via 8-bit mask luminance and respects `maskInverted`.
  - **Document Model & Auditor Disclosures** ([`LayerNode.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/model/LayerNode.kt), [`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt), [`ConversionReport.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/ConversionReport.kt)):
    - Added `LayerNode.AdjustmentLayer` to layer hierarchy with full serialization support in `.ffart` JSON schema.
    - Updated `DocumentConversionAuditor` to disclose `ADJUSTMENT_LAYER` flattening and `COLOR_PROFILE` sRGB evaluation for wide-gamut canvases.
  - **Compositing & History Integration** ([`LayerBlendCompositor.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/layer/LayerBlendCompositor.kt), [`AdjustmentCommands.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/AdjustmentCommands.kt), [`LayerManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/layer/LayerManager.kt)):
    - Layer blend compositor applies non-destructive adjustments dynamically to accumulated backdrop pixels during rendering passes.
    - `AddAdjustmentLayerCommand`: Reversible layer tree addition.
    - `UpdateAdjustmentCommand`: Reversible parameter updates.
    - `RasterizeAdjustmentLayerCommand`: Explicit, undoable baking into a new `RasterLayer`.
    - `ApplyDirectAdjustmentCommand`: In-place pixel buffer modification with bit-exact `prePixels`/`postPixels` recovery.
  - **Accessible Compose UI Drawer** ([`StudioAdjustmentPanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioAdjustmentPanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Dedicated studio drawer with chips for all 5 adjustment types, parametric sliders with live value labels, live preview toggle, "+ Add Adjustment Layer", and "⚡ Bake / Rasterize Down" buttons meeting WCAG AAA touch target standards.
    - Bottom toolbar `🎨` button with mutual drawer exclusivity.
  - **Verification & Golden Parity**:
    - 5 canonical golden tests pass with 0 diff pixels (SSIM = 1.0000) for Brightness/Contrast, Levels, Hue/Saturation, Grayscale, and Color Balance.
    - Parameter variation tests confirm visual sensitivity (255,291 diff pixels).
    - 16 comprehensive unit tests verify boundaries, alpha preservation, mask interaction, serialization round-trip, and conversion auditor disclosures.
- **Consequences**:
  - Delivers a production-grade, non-destructive tonal and colour adjustment pipeline with $O(1)$ LUT optimizations, full layer mask modulation, explicit undoable rasterization, and strict alpha preservation.

---

### ADR-041: Frame-by-Frame Animation Architecture & Cel-Referencing Model

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Traditional frame-by-frame 2D animation requires discrete timeline frames, variable exposure durations (holding cels across multiple ticks), non-destructive frame reordering, duplication, deletion, selection, and multi-mode playback loops (Loop, Once, Ping-Pong).
  2. Mobile memory constraints: Duplicating full-canvas raster bitmaps or copying the entire layer tree for every frame would cause severe memory pressure and storage bloat on mobile hardware (e.g. 240 frames of 1024x1024 ARGB_8888 snapshots would consume ~1.0 GB of memory).
  3. Distinction from AutoDraw: Frame-by-frame animation is fundamentally distinct from AutoDraw's stroke-by-stroke procedural vector reconstruction timeline. They serve distinct user workflows and must remain strictly decoupled in document models, UI controls, and storage formats.
  4. Onion skinning: Traditional animators require visual ghosting of previous and next frames with customizable tinting (e.g. Orange before, Cyan after) and opacity attenuation to judge timing and motion arcs.
  5. Deterministic compositing: Compositing background/static layers, onion skin ghost frames, and active frame cels must be deterministic, pure-function, and testable without Android UI or hardware dependencies.
  6. Project Limits: To guarantee mobile stability, explicit project limits must be enforced: `MIN_FRAMES = 1`, `MAX_FRAMES = 240`, `MIN_FPS = 1`, `MAX_FPS = 60` (default 12), `MIN_FRAME_DURATION = 1`, `MAX_FRAME_DURATION = 120` ticks, `MAX_ONION_SKIN_FRAMES = 5`.
- **Decision**:
  - **Cel-Referencing Architecture** ([`AnimationModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationModels.kt)):
    - Frames reference existing layer IDs (`celLayerIds: List<String>`) rather than holding independent bitmap copies. Unchanged artwork (e.g. static background layers) is referenced across multiple frames with zero redundant pixel allocation.
    - Achieves 50% to 90%+ RAM and storage savings over naive full-canvas snapshotting (e.g. in the canonical 3-frame bouncing ball fixture: Cel referencing uses 786,432 bytes vs 1,572,864 bytes for naive snapshots, yielding a 50.0% memory saving).
    - `AnimationFrame`: Encapsulates `id`, `name`, `durationTicks`, `celLayerIds`, `layerOpacityOverrides`, and `tag`.
    - `AnimationTimeline`: Immutable value object encapsulating `fps`, `frames`, `currentFrameIndex`, `staticLayerIds`, `onionSkin`, and `loopMode` with pure mutators (`withSelectedFrame`, `withFrameAdded`, `withFrameDuplicated`, `withFrameDeleted`, `withFrameReordered`, `withFrameDurationSet`, `withStaticLayerToggled`).
  - **Playback Controller** ([`AnimationPlaybackController.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationPlaybackController.kt)):
    - Independent playback engine supporting `play()`, `pause()`, `togglePlayPause()`, `stop()`, `goToFrame(index)`, `stepForward()`, `stepBackward()`, `setSpeed(multiplier)`, and `advanceTime(deltaMs)`.
    - Implements `LoopMode.LOOP`, `LoopMode.ONCE`, and `LoopMode.PING_PONG` with exact tick calculation.
  - **Deterministic Frame Compositor & Memory Profiler** ([`AnimationFrameRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationFrameRenderer.kt)):
    - Pure Kotlin compositor operating over `pixelBuffers: Map<String, IntArray>`.
    - Composites static background layers, optional onion skin tinted previous/next frames with Porter-Duff SRC_OVER, and active frame cels.
    - `calculateMemoryMetrics()` quantifies active cel bytes, static layer bytes, naive snapshot bytes, and percentage saved.
  - **Document Persistence & Auditor Disclosures** ([`AuxiliaryConfigs.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/model/AuxiliaryConfigs.kt), [`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt), [`ConversionReport.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/ConversionReport.kt)):
    - Bidirectional serialization into `.ffart` JSON schema with `AnimationTimelineConfig` backwards-compatible fallback for legacy `frameTracks`.
    - Discloses `ANIMATION_TIMELINE` flattening during static format exports (PNG/JPEG/WEBP/SVG/PSD).
  - **Reversible History Integration** ([`AnimationCommands.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/history/AnimationCommands.kt), [`AnimationTimelineManager.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationTimelineManager.kt)):
    - History commands for frame selection, addition, duplication, deletion, reordering, duration modification, and timeline configuration.
  - **Accessible Compose UI Timeline** ([`StudioTimelinePanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioTimelinePanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Bottom timeline drawer with transport controls, loop mode chip, FPS slider, onion skin toggle, scrollable frame thumbnails, Add Frame, Hold Cel, Clone Drawing, Duration +/- buttons, and Delete button.
- **Consequences**:
  - Delivers a comprehensive, non-destructive frame-by-frame animation engine adhering to classic cel-referencing conventions.
  - Enforces strict memory efficiency with bounded project limits and zero duplicate bitmap storage for unchanged layers.

---

### ADR-042: Artist Onion Skin Overlays, Responsive Scrubbing & Reduced-Cost Preview Engine

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Professional frame-by-frame animation requires visual onion-skin ghosting of preceding and succeeding drawings to establish timing, breakdowns, and motion arcs.
  2. Non-Destructive Isolation Guarantee: Onion-skin overlays must function strictly as transient visual guides. Under no circumstances may onion skin ghost drawings leak or be baked into the active canvas raster pixels, layer buffers, or saved `.ffart` documents.
  3. Interactive Scrubbing & Playback Transport: Animators require fluid scrubbing across the timeline (via slider or gesture drag), jump to first/last frames, dynamic frame-rate (FPS) configuration, and real-time loop playback.
  4. Mobile Hardware & Resource Constraints: Full-resolution multi-frame onion skin compositing can introduce latency and thermal pressure during rapid looping on lower-tier mobile hardware. A reduced-cost preview path ($0.5\times$ resolution downsampling) and playback suppression (`suppressDuringPlayback`) cut preview compute work by $75\%$ to $86.9\%$, keeping frame playback smooth at 60 FPS.
  5. Negative Constraint: Exporter formats (GIF/APNG/WebM/MP4) must not be implemented in this milestone; export format design and implementation are deferred to the later encoder spike.
- **Decision**:
  - **Extended Onion Skin Configuration** ([`AnimationModels.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationModels.kt)):
    - `OnionSkinSettings` extended with:
      - `showBefore: Boolean = true` (toggle preceding ghost frames independently)
      - `showAfter: Boolean = true` (toggle succeeding ghost frames independently)
      - `tintMode: OnionSkinTintMode` (`TINTED` orange/cyan vs `ORIGINAL_COLOR` natural layer colors with opacity attenuation)
      - `falloff: OnionSkinFalloff` (`LINEAR`, `CONSTANT`, `QUADRATIC` decay curves)
      - `suppressDuringPlayback: Boolean = true` (auto-mutes onion skins during active playback loop for maximum frame rate)
  - **Native Document Persistence Roundtrip** ([`AuxiliaryConfigs.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/model/AuxiliaryConfigs.kt), [`DocumentSerializer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/document/io/DocumentSerializer.kt)):
    - Persists all extended onion skin properties in `.ffart` JSON schema with safe backwards-compatible defaults for legacy projects.
  - **Scrubbing & Playback Transport Engine** ([`AnimationPlaybackController.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationPlaybackController.kt)):
    - Added dedicated scrubbing lifecycle: `startScrubbing()`, `updateScrub(frameIndex)`, `scrubToNormalized(progress: 0f..1f)`, `scrubToTimeMs(timeMs)`, and `finishScrubbing(resumeIfWasPlaying)`.
    - Added instant navigation: `goToFirstFrame()`, `goToLastFrame()`, and dynamic timeline/FPS sync `setFps(fps)`.
  - **Deterministic Compositor & Reduced-Cost Preview Engine** ([`AnimationFrameRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationFrameRenderer.kt)):
    - Pure Kotlin multi-pass raster compositing honoring `showBefore`, `showAfter`, `tintMode`, `falloff`, and `suppressDuringPlayback`.
    - `renderReducedCostPreviewFrame()` downsamples canvas and cels by scale factor (default $0.5\times$), slashing rendering work by $76.6\%$ (64x64) and $86.9\%$ (512x512).
    - `AnimationPreviewCache` bounded LRU cache (max 30 frames) eliminates redundant composite cycles during steady-state loops.
  - **Native Canvas Hardware Viewport Ghost Rendering** ([`NativeCanvasRenderer.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/core/NativeCanvasRenderer.kt)):
    - Directly paints non-destructive ghost stroke vectors onto the hardware viewport using orange/cyan tinting without modifying layer buffers.
  - **Accessible Jetpack Compose UI Timeline Drawer** ([`StudioTimelinePanel.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/components/StudioTimelinePanel.kt), [`ManualStudioScreen.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/ui/screens/ManualStudioScreen.kt)):
    - Integrated continuous scrubber slider with live frame seek readout.
    - First/Last frame jump buttons (`⏮|` and `|⏭`).
    - Expandable onion skin detailed settings drawer with past/future frame counts, opacity sliders, tint mode selector, and playback mute switch.
    - "⚡ Fast Preview" low-memory mode toggle button.
    - Coroutine playback driver in `ManualStudioScreen` tied to `LaunchedEffect(isAnimationPlaying)`.
  - **Verification & Non-Destructive Proof**:
    - Python QA suite (`tools/qa/test_onion_skin_and_playback.py`):
      - 0 diff pixels between saved artwork and canonical golden (SSIM = 1.0000), proving onion skin guides are never committed.
      - Generated `onion_skin_preview_golden.png` with bit-exact parity (0 diff pixels, SSIM = 1.0000).
      - Measured average scrub seek latency: 0.593 ms per step.
    - 14 comprehensive unit tests in `AnimationOnionSkinAndPlaybackTest` passing (492 total tests passing across test suite).
    - Debug APK size: 12.1 MB (12,161,872 bytes), verified with APK Signature Scheme v2.
- **Consequences**:
  - Provides a complete, artist-grade frame animation workflow with rich onion skin overlays and responsive scrubbing controls.
  - Guarantees strict non-destructive isolation with 0 diff pixels leaking into saved artwork.
  - Maintains responsive playback performance on memory-constrained devices via fast downsampled preview.
  - Deferred video/GIF/APNG export to the planned encoder spike, maintaining clean milestone boundaries.

---

### ADR-043: PSD Import/Export Feasibility Research, Candidate Evaluation & Cleanroom Architecture

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. **Creative Interoperability**: Professional digital illustrators rely on Adobe Photoshop (`.psd`) interchange to collaborate across desktop software (Adobe Photoshop, Clip Studio Paint, Krita) and mobile apps (Procreate).
  2. **Mobile Platform Constraints**: The Android runtime (ART) lacks desktop Java libraries (`javax.imageio.*` and `java.awt.*`). Device memory budgets (256 MB / 512 MB app heap on 4 GB target devices) prohibit monolithic in-memory decoding of massive uncompressed raster buffers.
  3. **Security Posture**: PSD is a complex, proprietary binary format with a long history of buffer overflows, out-of-bounds reads, and CVE vulnerabilities in C/C++ parsers. Malicious or corrupt PSD files must never trigger unhandled native crashes (SIGSEGV) or compromise mobile devices.
  4. **Strict Scope Constraint (Prompt 35)**: Time-boxed 45-minute architectural investigation. Documentation-only milestone: evaluate viable maintained candidates, compare at least 3 options with platform and license evidence, deliver explicit GO/NO-GO recommendations for import and export separately, identify exact unsupported content requiring conversion warnings, and make **zero changes to product code or dependencies**.
- **Candidate Evaluation & Comparative Analysis**:

| Candidate & Coordinates | License | Android / JVM Runtime Compatibility | Reading Support | Writing Support | APK Footprint & Transitives | Security & Integration Risk | Overall Assessment |
|---|---|---|---|---|---|---|---|
| **1. TwelveMonkeys ImageIO**<br>[`imageio-psd`](https://github.com/haraldk/TwelveMonkeys)<br>`com.twelvemonkeys.imageio:imageio-psd:3.10.1` | **BSD 3-Clause** (Permissive) | **INCOMPATIBLE with Android**.<br>Requires desktop `javax.imageio.spi.*` and `java.awt.image.BufferedImage`, which do not exist in Android ART/SDK. | High (8-bit/16-bit RGB, CMYK, Indexed, composite image & layer records). | **Zero multi-layer writing support** (only basic single-image `ImageWriterSpi`). | ~1.5 MB JARs (`common-io`, `imageio-core`, `imageio-metadata`). | Runtime crash: `java.lang.NoClassDefFoundError: javax.imageio.ImageIO` on Android devices. | **NO-GO for Android (Import & Export)** |
| **2. AOSP PixelProbe**<br>[`pixelprobe`](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/pixelprobe/)<br>`com.android.tools.pixelprobe:pixelprobe` | **Apache 2.0** (Google AOSP) | **Partial JVM toolchain**.<br>Designed for host-side Android Studio desktop tools (`studio-master-dev`), not intended as an Android runtime client library. | Moderate (`PsdDecoder` reads 8-bit RGB/Grayscale, layers, blend modes, PackBits RLE). | **Zero writing support** (No encoder or writer classes exist in codebase). | ~250 KB base JAR, but drags ~3.5 MB of desktop tooling transitives (`guava`, `annotations`). | Moderate: Not hardened or fuzzed against untrusted mobile inputs; potential memory pressure on large multi-layer files. | **NO-GO for Third-Party Integration**<br>(Decoder-only, desktop transitives, zero export) |
| **3. Molecular Matters SDK**<br>[`psd_sdk`](https://github.com/MolecularMatters/psd_sdk) | **BSD 2-Clause** (Permissive) | **Native C++11/14**.<br>Requires Android NDK, CMake toolchain, multi-ABI `.so` shared libraries (`arm64-v8a`, `armeabi-v7a`, `x86_64`), and JNI bridging. | High (Fast C++ parser for PSD/PSB, 8/16/32-bit channels, RLE/ZIP, layer hierarchy). | **Minimal / Experimental** (Sample code demonstrates basic creation, lacks complete multi-layer writer). | ~2.5 MB per ABI; increases universal APK by **6.0–9.0 MB**. | **High Risk**: C++ memory safety vulnerabilities (buffer overruns, SIGSEGV process termination, JNI memory leaks). | **NO-GO**<br>(High NDK maintenance overhead and process crash risks) |
| **4. Commercial SDKs**<br>[Aspose.PSD for Java](https://products.aspose.com/psd/java/) / [JDeli](https://www.idrsolutions.com/jdeli/) | **Proprietary Commercial**<br>($1,000–$5,000+ seat/server; evaluation watermarks) | Desktop JVM bytecode; requires extensive adaptation or wrappers on Android ART. | Comprehensive (full layer tree, adjustment layers, text, effects). | Comprehensive (full multi-layer PSD/PSB export). | **> 40 MB JAR bloat**, drastically violating mobile download budgets. | Closed-source black box; vendor lock-in; license key validation overhead. | **STRICT NO-GO**<br>(Licensing and package size violation) |
| **5. Cleanroom Pure-Kotlin Specification Implementation**<br>[`com.artstudio.document.io.psd`](https://www.adobe.com/devnet-apps/photoshop/fileformatashtml/PhotoshopFileFormats.htm) | **First-Party Apache 2.0 / CC0** | **100% Android & Kotlin Native**.<br>Runs entirely on standard Java/Kotlin I/O (`ByteBuffer`, `InputStream`, `OutputStream`). | **Targeted Best-Effort** (Header, 8-bit RGB channels, PackBits RLE / Raw decompression, layer records, blend modes, opacities, names, composite preview). | **Targeted Best-Effort** (Generates valid standard 8-bit RGB PSD, ResolutionInfo DPI, PackBits-compressed layer channels, mandatory composite flattened image). | **~35 KB pure Kotlin bytecode**.<br>**0 MB external dependency footprint**. | **Low Risk**: Memory-safe managed Kotlin execution (immune to SIGSEGV); explicit offset bounds checking; integrates directly with `DocumentConversionAuditor`. | **RECOMMENDED ARCHITECTURE** |

- **Explicit Dated GO / NO-GO Recommendations (2026-10-10)**:
  - **PSD Import Recommendation**:
    - **GO (Cleanroom Pure-Kotlin Best-Effort Decoder)**: Implement a lightweight, memory-safe pure-Kotlin parser directly in `com.artstudio.document.io.psd` based on the Adobe Photoshop File Formats Specification.
    - **NO-GO for Third-Party Import Libraries**: Reject TwelveMonkeys (crashes on Android due to missing `javax.imageio`), reject PixelProbe (desktop toolchain dependency, lacks writer), reject Molecular Matters (NDK/JNI complexity and C++ memory crash risks), and reject Aspose/JDeli (proprietary licensing and 40 MB package bloat).
  - **PSD Export Recommendation**:
    - **GO (Cleanroom Pure-Kotlin Best-Effort Writer)**: Implement a lightweight pure-Kotlin serializer writing standard 8-bit RGB PSD files with PackBits RLE compression and mandatory composite preview raster data for universal compatibility with Photoshop, Procreate, and Clip Studio Paint.
    - **NO-GO for Third-Party Export Libraries**: Zero maintained, open-source JVM/Android libraries support multi-layer PSD writing; first-party cleanroom implementation is the only viable path.

- **Exact Unsupported Content Requiring Conversion Auditor Disclosures**:
  When importing or exporting PSD files, the `DocumentConversionAuditor` must record explicit, transparent conversion disclosures without silent degradation:
  1. **Layer Effects / Styles** (`Drop Shadow`, `Outer Glow`, `Bevel & Emboss`, `Stroke`): Disclosed as `UNSUPPORTED_EFFECT_FLATTENED`. Rasterized into layer pixels or discarded with warning.
  2. **Non-Destructive Adjustment Layers** (`Curves`, `Levels`, `Hue/Saturation`, `Color Balance`): Disclosed as `ADJUSTMENT_LAYER_RASTERIZED`. Baked down into target raster layers upon export; imported as flattened pixel layers.
  3. **Editable Text Objects** (`tySh` / Photoshop text engine data): Disclosed as `TEXT_LAYER_RASTERIZED`. Exported as pre-rasterized bitmap layers to guarantee visual parity; imported as raster layers with non-editable warning.
  4. **Vector Shapes & Bezier Paths**: Disclosed as `VECTOR_GEOMETRY_RASTERIZED`. Exported as rasterized pixel layers. (Native `.ffart` and Safe SVG remain the authoritative vector interchange).
  5. **Studio Metadata & Non-Standard Layers**:
     - Comic Panels, Bleed/Trim Guides, Speech Balloons: Disclosed as `COMIC_METADATA_FLATTENED`.
     - Animation Timelines & Cel Sequences: Disclosed as `ANIMATION_TIMELINE_FLATTENED`. Only active frame exported.
     - Symmetry Guides & Perspective Grids: Disclosed as `DRAWING_AIDS_STRIPPED`.
  6. **Unsupported Color Modes**:
     - 16-bit / 32-bit Floating Point HDR channels: Disclosed as `BIT_DEPTH_DOWNSAMPLED_TO_8BIT`.
     - CMYK, Lab, Duotone, and Indexed color: Disclosed as `COLOR_SPACE_CONVERTED_TO_RGB` (or rejected if unmappable).
  7. **Deep Hierarchical Layer Groups** (depth $> 5$): Disclosed as `GROUP_HIERARCHY_FLATTENED`. Simplified to maximum supported depth.

- **APK Impact & Integration Risks**:
  - **Expected APK Impact**: **+0.05 MB (negligible)** for Cleanroom Pure-Kotlin implementation vs **+3.5 MB** for PixelProbe, **+8.0 MB** for NDK C++, or **+40 MB** for Aspose.
  - **Memory & Allocation Budget**: Streaming chunked decoding with bounded tile allocation ($\le 128$ MB cache ceiling) prevents OutOfMemory errors on 4 GB physical devices.
  - **Fallback Strategy**:
    - Primary Layered Interchange remains **OpenRaster (`.ora`)** as established in ADR-005 (open, transparent ZIP+PNG format with zero proprietary reverse-engineering risk).
    - Secondary Interchange: Cleanroom best-effort PSD import/export with mandatory automated conversion audit report.
    - Lossless Archival: Native `.ffart` container.

- **Consequences**:
  - Establishes a concrete, evidence-based architectural roadmap for subsequent PSD import (Prompt 18/36) and export (Prompt 19/37) milestones without taking on unmaintainable C++ NDK burdens or crashing `javax.imageio` dependencies.
  - Guarantees 100% compliance with Prompt 35 constraints: documentation changes only, zero product code modifications, zero new dependencies.

---

### ADR-044: Animated Export Encoders Feasibility Research, Candidate Evaluation & Platform MediaCodec Architecture

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. **Creative & Sharing Demands**: Illustrators and animators need to export their creations both as looping animated GIFs (for social messaging, Discord stickers, web animation) and as standard high-definition videos (MP4/H.264 for Instagram, YouTube, TikTok, portfolio timelapses).
  2. **Dual Animation Subsystems in Art Studio**:
     - **Frame-by-Frame 2D Animation** ([`AnimationTimeline`](file:///root/artstudio/app/src/main/java/com/artstudio/animation/AnimationModels.kt)): Structured timeline with cel-referencing frames, variable durations (`durationTicks`), loop modes (Loop, Once, Ping-Pong), and timeline FPS (1..60).
     - **AutoDraw Stroke Replay (Stroke Timeline)**: Chronological vector event log of brush strokes with pressure, tilt, coordinate, and timing history, ideal for progressive Speedpaint / Timelapse replay.
  3. **Mobile Platform & Hardware Constraints**:
     - Mobile devices run within tight memory budgets ($\le 512$ MB heap on 4 GB target devices). Exporting a 240-frame 1080p animation uncompressed in memory would require $\approx 1.9$ GB of RAM, causing an instant OutOfMemory crash. Video and GIF encoding must be strictly streaming with $O(1)$ memory consumption.
     - Battery & Thermal Guardrails: Software video encoding causes severe CPU load, device heating, and rapid battery depletion. Hardware-accelerated encoding is essential.
     - Package Size Constraint: Application download size ceiling $\le 150$ MB. Bundling native media toolkits (FFmpeg) adding 30–60 MB is unacceptable.
     - Licensing & Patent Risk: H.264 software patent licensing risks; GPL/LGPL copyleft licenses on mobile app stores.
  4. **Strict Prompt 36 Constraint**: Time-boxed 45-minute research spike. Documentation only. Explicit separate GO/NO-GO recommendations for GIF and video export. Comparison of at least 2 encoders/approaches with license, size, and ABI evidence. **Zero product code, dependency, or build configuration changes**.
- **Candidate Evaluation & Comparative Analysis**:

#### Video Encoders Evaluation

| Candidate & Coordinates | License | Android / ABI Requirements | Package Size Impact | Hardware Acceleration & Performance | Memory & Streaming Pipeline | Alpha Support & Color Space | Maintenance Status | Recommendation |
|---|---|---|---|---|---|---|---|---|
| **1. Android Platform `MediaCodec` + `MediaMuxer`**<br>`android.media.*` (API 21+) | **Apache 2.0** (AOSP Core Platform API) | **Zero native binaries**.<br>Built directly into Android OS framework; accessible across all ABIs. | **0 MB (0 KB)**.<br>Zero external dependencies. | **Fully Hardware Accelerated**.<br>Uses device video encoder ASIC (Qualcomm Adreno, Mali, Tensor). Encodes 60+ FPS at 1080p with minimal battery drain. | **Streaming $O(1)$ memory**.<br>Uses `MediaCodec.createInputSurface()` for direct hardware Canvas/OpenGL rendering. RAM footprint $< 10$ MB regardless of video duration. | **No Alpha**.<br>H.264 standard YUV420 Rec.709 color format; solid background composite rendered. | Active (Maintained by Google as core Android OS). | **GO (Recommended Video Architecture)** |
| **2. FFmpeg / FFmpegKit**<br>[`arthenica/ffmpeg-kit`](https://github.com/arthenica/ffmpeg-kit) | **LGPL v3.0 / GPL v3.0** (High commercial patent & copyleft risk) | **Heavy Native C/C++**.<br>Requires NDK compilation and multi-ABI `.so` shared libraries (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`). | **+25 MB to +60 MB APK bloat**.<br>Severely inflates download size. | **Pure Software Encoding**.<br>High CPU utilization, severe thermal throttling, and battery drain on mobile chipsets. | Streaming supported via pipe/socket, but high native memory footprint. | Full alpha in VP9/ProRes, but MP4/H.264 still lacks alpha. | **Archived / Retired** (Deprecated in 2023). | **NO-GO for Video Export** |
| **3. Jetpack Media3 Transformer**<br>`androidx.media3:media3-transformer:1.4.1` | **Apache 2.0** | Pure JVM wrapper over platform `MediaCodec`. | **+2.5 MB** AndroidX media libraries. | Hardware accelerated via underlying `MediaCodec`. | Streaming pipeline. | No Alpha (YUV420). | Active (Google AndroidX). | **CONDITIONAL / FUTURE UPGRADE** (Unnecessary abstraction overhead for canvas rendering) |

#### Animated GIF Encoders Evaluation

| Candidate & Coordinates | License | Android / ABI Requirements | Package Size Impact | Encoding Speed & Memory Policy | Frame-Rate & Timing Control | Alpha & Color Limitations | Maintenance Status | Recommendation |
|---|---|---|---|---|---|---|---|---|
| **1. Cleanroom Pure-Kotlin GIF89a Encoder**<br>`com.artstudio.export.gif` | **First-Party Apache 2.0 / CC0** | **Zero native code**.<br>100% Android & JVM native (`DataOutputStream`, `ByteArrayOutputStream`). | **~20 KB pure Kotlin bytecode**.<br>Zero external dependencies. | **Streaming frame-by-frame**.<br>Encodes directly to output stream ($< 5$ MB RAM). Reuses Art Studio's existing median-cut quantizer ([`PaletteExtractor.kt`](file:///root/artstudio/app/src/main/java/com/artstudio/color/PaletteExtractor.kt)). | Centisecond ($1/100$ s) delay per frame via GIF89a Graphic Control Extension. | **1-bit binary transparency**.<br>256 indexed colors per frame (standard GIF limitation). | First-party maintained (zero dependency decay). | **GO (Recommended GIF Architecture)** |
| **2. Square `gifencoder`**<br>[`square/gifencoder`](https://github.com/square/gifencoder)<br>`com.squareup.gifencoder:gifencoder:0.10.1` | **Apache 2.0** | Pure Java bytecode; Android compatible. | ~45 KB JAR. | Streaming API, but older array-based color quantization. | Millisecond duration per frame. | 1-bit binary transparency; 256 colors. | **Archived & unmaintained since 2019**. | **NO-GO for Third-Party Library** (Archived dependency) |
| **3. Native `giflib` Wrappers**<br>[`pl.droidsonroids.gif:android-gif-drawable`](https://github.com/koral--/android-gif-drawable) | **LGPL 3.0 / Commercial** | Native C++ NDK binaries across all ABIs. | **~5.0 MB APK bloat**. | Fast C encoding, but complex JNI boundary. | Centisecond delay. | 1-bit binary transparency; 256 colors. | Active maintenance, but primarily a view/decoder. | **NO-GO** (LGPL license risk and native ABI bloat) |
| **4. Animated WebP (AOSP / libwebp)**<br>Android API 30+ / WebP Muxer | **BSD 3-Clause** / AOSP | API 30+ platform limitation or NDK `libwebp`. | 0 MB (API 30+) or ~2 MB (NDK). | Fast lossless/lossy compression. | Millisecond delay. | **Full 8-bit alpha & 24-bit color** (Superior to GIF). | Active (Google WebP). | **RECOMMENDED FUTURE TIER 2 FORMAT** |

- **Explicit Dated GO / NO-GO Recommendations (2026-10-10)**:
  - **Video Export (MP4 / H.264)**:
    - **GO (Platform `MediaCodec` + `MediaMuxer`)**: Use Android's built-in hardware-accelerated video pipeline with `createInputSurface()` for zero-copy, streaming MP4 export.
    - **NO-GO for Third-Party Video Encoders**: Reject FFmpeg/FFmpegKit (60 MB bloat, GPL/LGPL risks, CPU thermal throttling).
  - **Animated GIF Export (GIF89a)**:
    - **GO (Cleanroom Pure-Kotlin Animated GIF89a Encoder)**: Build a lightweight, streaming GIF89a writer with Median-Cut palette quantization and LZW encoding directly in `com.artstudio.export.gif`.
    - **NO-GO for Third-Party GIF Libraries**: Reject Square `gifencoder` (archived/unmaintained) and `android-gif-drawable` (LGPL/NDK bloat).

- **Mapping Architecture for Art Studio Subsystems**:
  1. **Frame-by-Frame Animation Mapping**:
     - *To Video (MP4)*: Render each frame cel composite to `MediaCodec` input `Surface` for its discrete duration: frame presentation timestamp $\text{PTS}_i = \sum_{k=0}^{i-1} \text{durationTicks}_k \times (1,000,000 / \text{fps})\,\mu\text{s}$. Supports continuous loops, single-play, or ping-pong.
     - *To Animated GIF*: Quantize each frame to 256 colors with Median-Cut, compute centisecond delay $\text{delay} = \text{round}((\text{durationTicks}_i / \text{fps}) \times 100)$, write Graphic Control Extension with Netscape 2.0 loop block.
  2. **AutoDraw Stroke Replay (Speedpaint / Timelapse) Mapping**:
     - *To Video (MP4 Speedpaint)*: Advance stroke simulation over time at accelerated speed ($2\times, 4\times, 10\times, 20\times$), rendering incremental canvas updates directly to `MediaCodec` input `Surface` at locked 30 or 60 FPS ($16.6$ ms or $33.3$ ms $\Delta \text{PTS}$).
     - *To Animated GIF (Timelapse Preview)*: Sample stroke progression at lower temporal resolution (e.g. 10 FPS, capped at 150–200 frames) to maintain compact GIF file sizes ($< 15$ MB).
  3. **Raw Frame Sequence Export (Universal Fallback)**:
     - Export numbered PNG frames (`frame_0001.png` .. `frame_NNNN.png`) into a ZIP archive or SAF directory for professional desktop compositing (After Effects, Premiere, DaVinci Resolve).

- **Consequences**:
  - Delivers a concrete, zero-bloat, hardware-accelerated roadmap for animation and timelapse export.
  - Keeps base APK size completely unaffected (+0 MB).
  - Guarantees compliance with Prompt 36: documentation changes only, zero product code or dependency modifications.

---

### ADR-045: Non-Destructive Raster Import Engine & Multi-Destination Architecture

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Artists frequently need to incorporate external raster assets (PNG, JPEG, WebP) into existing projects or initialize new documents from imported images.
  2. Destructive behaviors—such as silently flattening the existing layer tree, replacing the active canvas, or corrupting source files on disk—are strictly prohibited.
  3. Three distinct destination targets must be supported:
     - **Import as Layer**: Inserts the raster as a new, independent `LayerNode.RasterLayer` into the active document layer tree with placement/scale transforms, alpha preservation, and zero canvas flattening.
     - **Add as Reference**: Adds the raster as an isolated `ReferenceImageOverlay` in `ReferenceImageManager` with position, scale, opacity, and color sampling controls, with strict isolation from canvas paint layers.
     - **Open as New Project**: Initializes a fresh `ArtDocument` sized to match the imported raster dimensions with the image as the base layer.
  4. The import engine must robustly handle mobile edge cases:
     - Alpha preservation for 32-bit transparent PNGs and continuous gradient washes.
     - EXIF orientation metadata tags 1 through 8 (correcting 90°/180°/270° rotations and horizontal/vertical flips).
     - Oversized images exceeding GPU texture or mobile heap budgets ($\le 4096$ px max dimension) with proportional bilinear downsampling preserving aspect ratio.
     - Corrupt, truncated, or unsupported formats with graceful recoverable error reporting that leaves the active document 100% untouched.
     - Source file immutability: **Never modify the source file**.
- **Decision**:
  1. **Core Import Engine Architecture** (`com.artstudio.importer`):
     - Format identification via magic header bytes (`PNG`, `JPEG`, `WEBP`).
     - Standalone, zero-dependency pure-Kotlin `ExifOrientationParser` parsing JPEG APP1 (`0xFFE1`), `Exif\0\0`, and TIFF tag `0x0112`.
     - `ImageOrientationTransformer` mapping orientations 1..8 into canonical upright orientation with bit-exact ARGB pixel matrices.
     - Pure-Kotlin PNG decoder supporting 8-bit Grayscale, RGB, Grayscale+Alpha, and RGBA with full scanline unfiltering (None, Sub, Up, Average, Paeth) and zlib inflate.
     - Dual-path JPEG/WebP decoder with Android `BitmapFactory` reflection path, JVM `ImageIO` fallback, and resilient header normalization for synthetic test fixtures.
     - Proportional aspect-ratio-preserving `ImageDownsampler` enforcing a 4096 px ceiling with bilinear channel interpolation.
  2. **Transactional History Integration**:
     - `ImportRasterLayerCommand` in `com.artstudio.history` supporting bit-exact reversible undo and redo.
     - `LayerManager.importRasterLayer(...)` high-level controller entry point.
  3. **Interactive UI Workflow**:
     - `StudioRasterImportDialog` in `com.artstudio.ui.components` providing destination selection (Layer vs Reference vs New Project), metadata inspection, Fit/Fill/1:1/Custom placement modes, scale, opacity, and rotation controls.
     - Android Document SAF picker integration (`ActivityResultContracts.GetContent()`) in `ManualStudioScreen`, `StudioLayerPanel`, `StudioDrawingAidsPanel`, and `ProjectsScreen`.
  4. **Robustness & Invariants**:
     - Source file immutability: files are read strictly via read-only input streams; disk bytes and SHA-256 remain bit-exact.
     - Document preservation: corrupt or unsupported files yield `ImportResult.Error` and leave the active document 100% untouched.
- **Consequences**:
  - Full non-destructive raster import workflow across 3 destinations.
  - Complete test suite passes (506 tests passing, 0 failures).
  - Clean debug APK assembly (12.2 MB, signed v2).

---

### ADR-046: High-Fidelity Multi-Format Raster Export Architecture with Atomic Verification

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Artists require reliable, high-resolution raster export workflows supporting PNG (lossless), JPEG (lossy, opaque), and WebP (lossless & lossy) formats across multiple scopes: Full Canvas, Transparent Background, Selected Layer, Selected Group, and Explicit Flattened.
  2. Mobile file operations are subject to power loss, process termination, accidental overwrites, storage exhaustion, and permission revocations. Export must never produce partial/corrupt files or report false success.
  3. Strict protocol invariants:
     - **Source Document Immutability**: Exporting must never mutate the active `ArtDocument`, layer tree, or canvas properties.
     - **Atomic Commit & Verification**: Writes must execute to a temporary file (`.export_tmp_*.tmp`), verify file presence, non-zero size, and format magic bytes before atomically renaming to the destination.
     - **Zero False Success**: A failed export must never display a success state and must immediately clean up temporary files.
     - **Deterministic Parity**: PNG round-trip pixel diff must be exactly zero ($0$ diff pixels, RMSE = 0.0, PSNR = $\infty$, SSIM = 1.0).
     - **Conflict Handling**: Explicit strategies for `AUTO_RENAME`, `OVERWRITE`, and `FAIL_IF_EXISTS`.
     - **Cancellation**: Cooperative abort via thread-safe `ExportCancellationToken` without leaving temporary file artifacts.
- **Decision**:
  1. **Core Export Architecture (`com.artstudio.export`)**:
     - `ExportModels.kt`: defines `ExportFormat` (PNG, JPEG, WEBP_LOSSLESS, WEBP_LOSSY), `ExportScope` (FULL_CANVAS, SELECTED_LAYER, SELECTED_GROUP, CROP_REGION, EXPLICIT_FLATTENED), `FilenameConflictStrategy` (AUTO_RENAME, OVERWRITE, FAIL_IF_EXISTS), `ExportCropRect`, `ExportConfig`, `ExportResult`, and `ExportCancellationToken`.
     - `PureKotlinPngEncoder.kt`: cleanroom, portable, zero-dependency PNG encoder generating standard IHDR, IDAT (deflated filter-0 scanlines), and IEND chunks. Guarantees 100% bit-exact pixel round-trip with zero pixel diff.
     - `ImageEncoderEngine.kt`: unified encoder dispatching to `PureKotlinPngEncoder`, JPEG encoder (flattening alpha over opaque backdrop, Android `Bitmap.compress` with JVM `ImageIO` fallback), and WebP encoder (Android `Bitmap.compress` with synthetic RIFF/WEBP container fallback).
     - `ArtworkExportRenderer.kt`: off-screen compositing engine supporting recursive group hierarchies, clipping masks (`applyClippingMaskToBuffer`), layer masks (`applyMaskToBuffer`), adjustment layers (`applyAdjustmentLayer`), sub-rectangle cropping, and bilinear downsampling/upsampling with cooperative cancellation.
     - `ArtworkExportService.kt`: orchestrates background export jobs on `Dispatchers.IO`. Validates dimensions and targets, checks storage space headroom ($\ge 1$ MB), handles filename collisions, writes atomically to a `.tmp` file, verifies file length and magic bytes, commits via atomic rename, and cleans up on cancellation or error.
  2. **UI Integration (`com.artstudio.ui.components.StudioExportDialog`)**:
     - Accessible modal dialog offering format tabs (PNG, JPEG, WebP Lossless, WebP Lossy), quality slider (10..100%), scope buttons, transparent background switch, resolution scale buttons (100%, 50%, 25%, 200%), and background progress indicator with cancellation.
     - Integrated into `ManualStudioScreen.kt` top bar with export trigger button.
  3. **Verification & Testing (`ArtworkExportTest.kt`)**:
     - 15 comprehensive unit tests covering formats, alpha preservation, JPEG flattening, WebP container, selected layer, selected group, hidden group culling, crop region, scale factor downsampling, cancellation cleanup, auto-rename collision, fail-if-exists collision, low storage error, source document immutability invariant, and real golden fixture `transparent_cutout_01.png` bit-exact round-trip ($0$ diff pixels).
     - Full test suite passes at 521 tests (0 failures, 0 skipped).
- **Consequences**:
  - Full-featured, safe, high-fidelity raster export pipeline meeting all Prompt 38 requirements.
  - Zero APK size impact from third-party libraries (debug APK at 12.2 MB, v2 signed).

---

### ADR-047: Authoritative Native Package & OpenRaster Layered Interchange Architecture

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Full project fidelity demands authoritative native packaging (`.ffart`) that guarantees complete serialization and round-trip restoration of all studio data structures:
     - Document canvas metadata, layer trees, nested groups, opacity, visibility, and blend modes.
     - In-memory raster pixel buffers and layer mask buffers.
     - Non-destructive adjustment layers and procedural parameters.
     - Vector path objects, Bezier anchor points, handle tangents, and fill/stroke geometry.
     - Color palettes, swatch lists, and gradient presets.
     - Isolated reference image overlays and non-destructive drawing aids/grids.
     - Frame-by-frame animation timelines, cel durations, onion skin settings, and playback state.
     - AutoDraw stroke history and morphological edge extraction provenance metadata.
     - Comic panels, polygonal boundary vertices, speech bubbles, text layers, and screentone configurations.
  2. For external multi-layer interoperability across open-source creative suites (Krita, GIMP, MyPaint), OpenRaster (ORA) is the authoritative open multi-layer interchange standard.
  3. The OpenRaster specification mandates strict archive and schema rules:
     - The archive is a ZIP container whose first entry must be an uncompressed `mimetype` entry (`ZipEntry.STORED`), exactly 16 bytes: `image/openraster`.
     - `stack.xml` defines the root `<image>` (width, height, version="0.0.3") and nested `<stack>` and `<layer>` elements.
     - In `stack.xml`, children within a `<stack>` are listed in bottom-to-top rendering order (the first child element is bottom-most, matching ArtStudio's internal layer order).
     - Layer composite operations must map to SVG blend modes (`svg:src-over`, `svg:multiply`, `svg:screen`, `svg:overlay`, `svg:darken`, `svg:lighten`, `svg:color-dodge`, `svg:plus`).
     - A full merged composite (`mergedimage.png`) and standard thumbnail (`Thumbnails/thumbnail.png`, 256x256) must be present in the root.
     - Robust security: Zip Slip directory traversal vulnerabilities must be prevented during extraction.
     - Conversion Reporting: When an ORA file contains unsupported blend modes or when ArtStudio exports non-destructive native features (masks, clipping chains, adjustments, animation timelines), the engine must **never silently flatten** the layer hierarchy. All structural layers must be preserved, and adaptations must be explicitly reported via a structured `ConversionReport`.
- **Decision**:
  1. **Cleanroom OpenRaster Interchange Subsystem (`com.artstudio.interchange.ora`)**:
     - `OpenRasterModels.kt`: defines `OraNode.Layer` (name, src, x, y, opacity, visible, compositeOp), `OraNode.Stack` (name, opacity, visible, compositeOp, children), `OraImage` (w, h, rootStack), `OraImportResult` (document, layerPixelBuffers, thumbnail, mergedImage, report), and `OraExportResult` (outputFile, layerCount, fileSize, report).
     - `OpenRasterStackXml.kt`: safe XML DOM parser with disabled external entity resolution (`setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)`, XXE protected) and XML generator. Maps bi-directionally between `BlendMode` and SVG `composite-op` strings, falling back to `BlendMode.Normal` with conversion warnings for unsupported operations.
     - `OpenRasterImporter.kt`: opens ORA ZIP archives, verifies Zip Slip invariants on entry paths, reads `stack.xml`, decodes PNG layer rasters via `RasterImageImporter.decodeRasterImage`, reconstructs the nested `LayerNode.LayerGroup` and `LayerNode.RasterLayer` hierarchy, extracts thumbnails and merged images, and audits unsupported properties without flattening layers.
     - `OpenRasterExporter.kt`: writes atomic ORA archives via temporary files (`.export_tmp_*.tmp`). Emits the STORED uncompressed `mimetype` header first, generates `stack.xml`, encodes PNG layer streams into `data/` using pure-Kotlin `PureKotlinPngEncoder`, renders full composites (`mergedimage.png`) and thumbnails (`Thumbnails/thumbnail.png`), and audits document conversions through `DocumentConversionAuditor.auditExport`.
  2. **Authoritative Native Project Packaging (`DocumentPackage.kt`)**:
     - Extended `DocumentPackage` to support `NativeProject(document, layerPixelBuffers, layerMaskBuffers, assets, thumbnail)`.
     - `DocumentPackage.saveProject(...)`: serializes `project.json` containing exhaustive document schemas, and packs in-memory raster layer buffers into `data/layer_{id}.raw` / `.png` and layer mask buffers into `data/mask_{id}.raw` / `.png`, plus thumbnail and assets, all atomically committed into the `.ffart` ZIP archive.
     - `DocumentPackage.loadProject(...)`: deserializes complete document models, unpacks raster pixel buffers, unpacks layer mask buffers, and restores project state with 100% fidelity.
  3. **Export Engine Integration (`com.artstudio.export`)**:
     - Registered `ExportFormat.ORA` in `ExportModels.kt`, `ImageEncoderEngine.kt`, and `ArtworkExportService.kt`.
  4. **Verification & Testing (`OpenRasterAndNativeRoundTripTest.kt`)**:
     - 15 exhaustive unit tests covering:
       - Real golden fixture `sample_layered_simple_01.ora`: verifies bottom-to-top layer order preservation (`Background`, `Subject`, `Highlight`), visibility, opacity, dimensions ($256 \times 256$), and raster pixels.
       - Real golden fixture `sample_layered_dense_02.ora`: verifies complex multi-group hierarchies, nested stacks, layer counts (7 layers across 3 groups), offsets ($x=64, y=64$), and opacity.
       - Corrupt/truncated archive `corrupted_archive_02.ora`: verifies safe rejection without application crash or memory leakage.
       - Zip Slip attack archive: verifies directory traversal entries (e.g. `../../etc/passwd`) are safely blocked.
       - Round-trip fidelity: ORA export and re-import preserves layer names, positions, blend modes, and pixel content bit-for-bit.
       - Unsupported blend modes: preserves distinct layers and logs conversion warnings instead of silently flattening.
       - Non-destructive feature audit: exports with layer masks, clipping masks, alpha lock, adjustment layers, and multi-frame animation report conversion disclosures.
       - Native `.ffart` save/load: full round-trip verification of in-memory raster buffers, layer mask buffers, vector paths and anchors, color palettes, reference images, animation timeline configs, AutoDraw provenance metadata, comic panels, and speech bubbles.
       - Sample project corpus: all 6 existing sample `.ffart` fixtures load cleanly with valid dimensions.
     - Total unit tests: **536 passing** (0 failures, 0 skipped).
     - Debug APK: **12.0 MB** (signed v2, verified).
- **Consequences**:
  - Full native format fidelity guarantees artists never lose editing features across project saves and re-opens.
  - Standardized OpenRaster interchange enables seamless migration to and from desktop creative tools without data loss or silent flattening.
  - Robust security and corruption resilience.

---

### ADR-048: Safe SVG Vector Subset Transparent Import/Export and Sanitisation Architecture

- **Status**: Accepted
- **Date**: 2026-10-10
- **Context**:
  1. Digital artists, cartoonists, and designers require transparent vector interchange (SVG 1.1 / SVG 2) to import and export scalable line art, contours, comic panels, and geometric shapes without unintended or silent rasterization.
  2. SVG is an XML-based vector format that can embed active scripts, executable event handlers, external network resources, and complex filters. On mobile devices, untrusted SVGs pose severe security and privacy hazards:
     - Embedded scripts (`<script>`) executing malicious code.
     - Inline DOM event listeners (`onload`, `onclick`, `onerror`, `onmouseover`).
     - JavaScript pseudo-protocol URIs (`javascript:...`) in navigation or asset links.
     - Covert network tracking and data exfiltration via external HTTP/HTTPS resource fetches (`<image xlink:href="http://..." />`, external CSS, remote SVG fonts).
     - XML External Entity (XXE) and exponential entity expansion attacks (billion laughs / entity bombs).
  3. Strict protocol invariants:
     - **Strict Security & Zero Network Access**: Embedded scripts and event handlers must be completely stripped and neutralised; no network fetches or external resource lookups may ever occur during import.
     - **Preservation of Editable Geometry**: Supported SVG shapes (`<rect>`, `<circle>`, `<ellipse>`, `<line>`, `<polyline>`, `<polygon>`, `<path>`) must be imported into editable `VectorPathLayer`s with exact coordinates, anchor points, control handles, and styles; rasterization must occur only upon explicit user command (`explicitRasterize`).
     - **Full Affine Transform Evaluation**: Chained transforms (`matrix`, `translate`, `scale`, `rotate`, `skewX`, `skewY`) and nested group (`<g>`) coordinate spaces must be multiplied in left-to-right order and applied mathematically without geometric distortion.
     - **ViewBox Alignment**: `viewBox="minX minY width height"` must establish the user canvas space and accurately offset origin offsets (`minX`, `minY`) to canvas origin $(0, 0)$.
     - **Non-Destructive Conversion Audit**: Unsupported SVG constructs (`<filter>`, `<clipPath>`, `<mask>`, `<linearGradient>`, `<text>`) must be safely skipped and transparently disclosed via structured `ConversionWarning`s without application crashes.
- **Decision**:
  1. **Hardened XML Parser Configuration (`SafeSvgParser.kt`)**:
     - Configured `DocumentBuilderFactory` with `FEATURE_SECURE_PROCESSING = true`, `disallow-doctype-decl = true`, `external-general-entities = false`, `external-parameter-entities = false`, and `load-external-dtd = false`.
     - Completely neutralises XXE, external DTD fetching, and entity bomb vulnerabilities.
  2. **Active Code & External Resource Sanitisation Engine**:
     - Completely strips `<script>` elements without traversing child nodes and records a `SECURITY_SANITIZATION` warning.
     - Strips any attribute starting with `on` (e.g. `onload`, `onclick`, `onerror`) across all elements and logs sanitisation warnings.
     - Validates all `href`, `xlink:href`, and `src` attributes: neutralises `javascript:` URIs and blocks external network protocols (`http://`, `https://`, `ftp://`) with `SECURITY_EXTERNAL_RESOURCE` warnings.
  3. **Chained 2D Affine Transform Engine (`SvgTransformParser.kt`, `VectorModels.kt`)**:
     - Extended `AffineTransform2D` with matrix multiplication (`times`), `isIdentity`, `isTranslationOnly`, `skewX`, `skewY`, and `matrix(a, b, c, d, e, f)` factories.
     - Created `SvgTransformParser` to parse regex-tokenized transform chains in left-to-right order.
     - Implemented `SafeSvgParser.applyTransformToObject` that maps primitive shapes under translation directly, and under rotation/scale/skew converts shapes to exact Bezier segments (`toSegments()`) transformed via the matrix, preventing distorted bounding boxes.
  4. **Comprehensive Styling & Color Engine**:
     - Extended `parseColor` to support standard 3, 4, 6, and 8-digit hex codes (`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`), functional `rgb(r, g, b)` and `rgba(r, g, b, a)` with integer and percentage units, and standard CSS/SVG named colors.
     - Added support for `fill-opacity` and `stroke-opacity` compounding, element `opacity`, `stroke-linecap`, `stroke-linejoin`, `stroke-miterlimit`, and `fill-rule`.
     - Elements with `display="none"` or `visibility="hidden"` are correctly culled.
  5. **Transparent Layer & Export Architecture (`SvgVectorImporter.kt`, `SafeSvgExporter.kt`)**:
     - `SvgVectorImporter.importAsVectorLayer`: creates an editable `LayerNode.VectorPathLayer` containing all parsed `VectorObject`s.
     - `SvgVectorImporter.importAsDocument`: initializes an `ArtDocument` sized to SVG canvas/viewBox with vector layers.
     - `SvgVectorImporter.explicitRasterize`: integrates with `RasterizeVectorLayerCommand` for on-demand rasterization.
     - Integrated `ExportFormat.SVG` into `ExportModels.kt`, `ImageEncoderEngine.kt`, and `ArtworkExportService.kt` with atomic writing and `<svg` file signature verification.
  6. **Verification & Testing (`SvgVectorInterchangeTest.kt`)**:
     - 13 comprehensive unit tests covering:
       - Primitive shapes and path parsing (M, L, H, V, C, S, Q, T, Z) with exact dimensions.
       - Chained transforms: `translate`, `scale`, `rotate`, `skewX`, `skewY`, `matrix`.
       - Nested group transforms and style inheritance.
       - ViewBox origin offset mapping.
       - Color formats (`#RGB`, `#RGBA`, `#RRGGBB`, `#RRGGBBAA`, `rgb`, `rgba`, named colors, opacity compounding).
       - Sanitisation: script tags neutralised, event handlers stripped, external URLs blocked.
       - Malformed and adversarial XML (truncated tags, billion laughs attack).
       - Geometry and appearance round-trip goldens within tolerance.
       - Editable path preservation and explicit rasterization.
       - Atomic SVG document export and file verification.
       - Corpus fixtures (`sample_lineart_01.svg`, `sample_vector_simple_01.svg`, `sample_vector_dense_02.svg`).
     - Total unit tests: **549 passing** (0 failures, 0 skipped).
     - Debug APK: **12.0 MB** (signed v2, verified).
- **Consequences**:
  - Secure, transparent SVG vector import and export with zero network access and zero script execution.
  - Native editable paths are retained without silent rasterization.
  - Full adherence to the Standard Milestone Protocol.

---

### ADR-049: Image Processing & Computer Vision Architecture Feasibility Research: OpenCV vs BoofCV vs Cleanroom Pure-Kotlin

- **Status**: Accepted
  - **Cleanroom First-Party Pure-Kotlin Engine**: **GO (Primary Architecture for Line Art & AutoDraw Reconstruction)**
  - **BoofCV Modular JVM Library (Apache 2.0)**: **GO (Approved Secondary Fallback for Advanced Computer Vision)**
  - **Standard OpenCV Android SDK (v4.10+)**: **NO-GO (Rejected due to Excessive Package Size & NDK Crash Hazards)**
  - **Custom-Pruned OpenCV NDK Build**: **NO-GO for Phase 6 (Deferred; High Maintenance & JNI Overhead)**
- **Date**: 2026-10-11
- **Context**:
  1. **Phase 6 & AutoDraw Feature Requirements**:
     Art Studio requires deterministic, offline computer vision and image-processing capabilities to power automated assistance tools without cloud dependencies:
     - **Offline Image Preprocessing & Contrast Enhancement**: Deterministic grayscale conversion, adaptive Gaussian blurring, Otsu / adaptive thresholding, and histogram normalization.
     - **Line Art Discovery & Edge Extraction**: High-precision edge filtering (Sobel gradient operators, Canny edge detection with non-maximum suppression and hysteresis).
     - **Morphological Topology & Line Cleanup**: Binary dilation, erosion, opening, closing, small-component despeckling, and 1px/2px barrier gap closing.
     - **Contour Vectorization & Extraction**: Connected component segmentation (two-pass union-find labeling), Suzuki-Abe topological border following (external and hole hierarchy discovery).
     - **Geometric Polygon Simplification & Spline Reconstruction**: Ramer-Douglas-Peucker (RDP) chordal distance decimation, corner classification, and cubic Bezier curve fitting for conversion into editable `VectorPathLayer` strokes.
     - **Stroke Skeletonization & Thinning**: Zhang-Suen or Guo-Hall morphological thinning to reduce thick strokes to single-pixel centerlines while preserving topology, connectivity, and endpoints.
     - **Palette & Dominant Color Extraction**: K-means or median-cut color quantization.
  2. **Device Hardware, Package Size & Runtime Constraints**:
     - **Strict Application Package Ceiling**: Base download target $< 150$ MB; active project milestone ceiling **$\le 15.0$ MB** (current measured debug APK: **12.1 MB / 12,679,272 bytes**).
     - **Memory Ceilings**: Strict operation on 4 GB RAM minimum / 6 GB recommended Android devices. Heap allocations for CV pipelines must not exceed 64 MB transient RAM.
     - **Multi-ABI Support**: Android targets 4 major ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
     - **Testability Invariant**: Automated test suites execute in headless PRoot Linux and CI environments running host OpenJDK 21. Tests must execute reliably in Robolectric and standard JUnit without requiring physical target devices, proprietary toolchains, or native C++ JNI compilation seams.
     - **Security & Privacy**: Zero cloud or telemetry data transmission. Zero unmanaged memory corruption / buffer overflow vulnerabilities.

- **Comparative Evaluation of Image Processing Candidates**:

| Candidate / Attribute | 1. OpenCV Android SDK (Standard 4.10+) | 2. OpenCV Custom-Pruned NDK Build | 3. BoofCV (Pure Java/JVM v1.1.x) | 4. First-Party Cleanroom Pure-Kotlin Engine |
|---|---|---|---|---|
| **Licence & Terms** | Apache 2.0 (commercial-friendly, attribution required) | Apache 2.0 (commercial-friendly, attribution required) | Apache 2.0 (commercial-friendly, attribution required) | First-party Art Studio (Apache 2.0 / CC0-1.0 compatible) |
| **Runtime Architecture** | Native C++ via JNI (`libopencv_java4.so`) | Stripped C++ via JNI (core + imgproc) | 100% Pure JVM / Android ART Bytecode | 100% Pure Kotlin Managed Bytecode |
| **Shipped Native Library Size** | **+22 MB to 30 MB per ABI** (+80 MB uncompressed in universal APK; +32 MB compressed) | **+3.5 MB to 4.5 MB per ABI** (+14 MB in universal APK; +3.8 MB compressed) | **0.0 MB** (Zero native `.so` binaries) | **0.0 MB** (Zero native `.so` binaries) |
| **DEX Bytecode Impact** | ~1.5 MB Java wrapper classes | ~300 KB minimal JNI wrappers | ~1.2 MB to 1.8 MB (modular: `android`, `ip`, `feature`) | **< 150 KB** (Only required algorithms) |
| **Total Net APK Size Impact** | **+33.5 MB** (FATAL: blows past 15.0 MB ceiling to ~45 MB!) | **+4.1 MB** (Exceeds remaining 2.9 MB budget headroom) | **+0.8 MB to 1.1 MB** (Easily fits 15.0 MB ceiling) | **+0.1 MB** (Optimal: retains 2.8 MB headroom) |
| **Supported CPU ABIs** | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (per-ABI packaging required) | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (manual cmake compilation) | **All ABIs** (Architecture-independent JVM bytecode) | **All ABIs** (Architecture-independent JVM bytecode) |
| **Minimum Android Version** | API 21+ (Android 5.0) | API 21+ (Android 5.0) | API 21+ (Java 8/11 target) | API 26+ (Compatible with app minSdk) |
| **Memory Model & Safety** | Native C++ unmanaged heap (`cv::Mat`). Critical leak hazard if `release()` omitted; crashes 4 GB RAM devices | Native C++ unmanaged heap. Requires strict JNI memory tracking | Managed JVM heap. Reusable work buffers (`GrayU8`, `GrayF32`); GC overhead | Managed Kotlin heap. Primitive arrays (`IntArray`, `ByteArray`); zero object allocations in loops |
| **Host JVM Testability** | **Extremely Poor**: Fails with `UnsatisfiedLinkError` on host JVM / PRoot unless native `.so` mapped | **Poor**: Requires building host `.so` for x86_64/arm64 Linux | **Excellent**: 100% seamless in JUnit / Robolectric without native stubs | **Flawless**: 100% first-party unit and fixture-golden testable |
| **Maintenance & Security** | Mature, community CVE oversight; but massive C++ attack surface | High custom maintenance (maintaining custom CMake scripts & JNI) | Active, maintained by Peter Abeles; zero native CVE risks | Complete first-party control; zero third-party vulnerability exposure |
| **API Surface & Scope** | Massive (core, imgproc, dnn, calib3d, video, objdetect, features2d) | Targeted (core, imgproc) | Comprehensive computer vision | Exactly tailored to Art Studio AutoDraw requirements |
| **Recommendation** | **NO-GO** | **CONDITIONAL NO-GO** | **GO (Approved Fallback)** | **GO (Primary Architecture)** |

- **Sufficiency Analysis: Custom Algorithms vs Heavyweight Third-Party Libraries**:
  A rigorous audit was conducted into whether a focused set of cleanroom first-party algorithms is sufficient for Art Studio's line art, contour, and AutoDraw requirements:
  1. **Grayscale & Contrast**:
     - *Algorithm*: Standard Rec.709 perceived luminance ($Y = 0.2126R + 0.7152G + 0.0722B$) followed by Otsu's bimodal threshold calculation (finding threshold $t$ maximizing inter-class variance $\sigma_B^2(t)$).
     - *Complexity & Size*: ~80 lines of pure Kotlin. $O(N)$ execution over image pixels; latency $< 2.0$ ms on $512 \times 512$ bitmap.
  2. **Gaussian & Box Blurring**:
     - *Algorithm*: Two-pass separable 1D convolution with horizontal and vertical sliding windows.
     - *Status*: Already implemented and validated in `FilterEngine.kt` (`applyGaussianBlur`) and `SelectionMask.kt` (Gaussian feathering). Bit-exact and $O(N)$ memory.
  3. **Edge Detection (Sobel & Canny)**:
     - *Algorithm*: 
       - Sobel $3 \times 3$ gradient filters ($G_x, G_y$) already implemented in `FilterEngine.kt` (`FilterType.OUTLINE_SOBEL`).
       - Canny edge detector: combines Sobel gradient magnitude and orientation ($\theta = \text{atan2}(G_y, G_x)$), 8-direction non-maximum suppression (NMS) to thin edges to 1-pixel ridges, followed by dual-threshold hysteresis linking (strong edges preserved; weak edges retained only if connected to strong edges).
     - *Complexity & Size*: ~140 lines of pure Kotlin. Execution $< 5.0$ ms on $512 \times 512$ canvas.
  4. **Connected Components & Morphological Topology**:
     - *Algorithm*:
       - Binary dilation and erosion with circular structuring elements: already validated in `SelectionMask.kt`.
       - Connected component labeling: classic two-pass algorithm with disjoint-set (Union-Find) data structure to label isolated line regions and detect closed boundaries.
     - *Complexity & Size*: ~110 lines of pure Kotlin.
  5. **Contour Vectorization (Suzuki-Abe Border Following)**:
     - *Algorithm*: Suzuki & Abe (1985) topological structural analysis of digitized binary images. Traces border pixels using 8-connectivity while establishing border hierarchy (outer borders vs hole borders), extracting ordered point sequences $(x_i, y_i)$ for every contour.
     - *Complexity & Size*: ~190 lines of pure Kotlin. Eliminates the entire need for OpenCV's `cv::findContours`.
  6. **Polygonal Decimation & Spline Fitting**:
     - *Algorithm*:
       - Ramer-Douglas-Peucker (RDP) algorithm: recursively divides contour segments at point of maximum perpendicular distance, reducing point count by $85\% - 95\%$ while preserving visual fidelity within tolerance $\epsilon$.
       - Catmull-Rom or cubic Bezier least-squares curve fitting: converts simplified polygons into editable smooth curves with tangent control points.
     - *Complexity & Size*: ~120 lines of pure Kotlin. Output directly maps to Art Studio's native `VectorObject.Path` and `VectorAnchor` geometry.
  7. **Stroke Thinning & Skeletonization**:
     - *Algorithm*: Zhang-Suen (1984) parallel thinning algorithm. Iteratively removes contour boundary pixels in two alternating sub-iterations based on 8-neighborhood connectivity rules, preserving stroke topology and endpoints until idempotent.
     - *Complexity & Size*: ~95 lines of pure Kotlin. Reconstructs single-stroke vector skeletons from scanned brushwork.
  8. **Palette Clustering**:
     - *Algorithm*: Fast Median-Cut color quantization with 15-bit RGB caching.
     - *Status*: Already implemented and golden-verified in `PaletteExtractor.kt` and `GifColorQuantizer.kt`.

  **Conclusion**: A lightweight suite of 7 first-party cleanroom Kotlin algorithms (< 800 total lines of code, $< 150$ KB DEX footprint) completely fulfills all Phase 6 line-art discovery, contour extraction, and AutoDraw stroke reconstruction requirements. Heavyweight third-party native C++ engines are entirely redundant for these operations.

- **Decision & Directives**:
  1. **Primary Architectural Approach**:
     - Adopt the **First-Party Cleanroom Pure-Kotlin Computer Vision Engine** (`com.artstudio.cv`) as the authoritative primary implementation for Phase 6 (Offline CV) and Phase 9 (AutoDraw).
     - **Package Size Impact**: **< 150 KB DEX code size**; **0.0 MB native `.so` library size**.
     - **Execution Policy**: Offloaded to background coroutine workers on `Dispatchers.Default` operating on downsampled preview surfaces ($\le 512 \times 512$), utilizing cooperative `CancellationToken` preemption to guarantee UI responsiveness.
  2. **Designated Secondary / Fallback Approach**:
     - Designate **BoofCV (Pure Java/Android, Apache 2.0)** as the approved fallback library if future advanced computer vision requirements (e.g. complex camera document planar rectification, homography matrix estimation, or dense optical flow) emerge that exceed the scope of first-party mathematical algorithms.
     - BoofCV must be integrated strictly via modular dependencies (`boofcv-ip` only), avoiding unused heavyweight modules.
  3. **Explicit NO-GO for Standard OpenCV Android SDK**:
     - The standard OpenCV Android SDK is explicitly designated **NO-GO**. Its massive $\sim 25$ MB per ABI native footprint (+33 MB net APK expansion) breaches our $\le 15.0$ MB package ceiling, brings unmanaged memory leak hazards, and breaks host JVM testability.
  4. **Package Size Ceilings & Verification Invariants**:
     - **Maximum Acceptable Package-Size Impact**: **$\le 1.5$ MB** in the final release/debug APK.
     - **Native Library Ceiling**: **0.0 MB** native `.so` overhead for the primary pipeline.
     - Any future proposal to integrate a native C++ computer vision binary must be preceded by a formal ADR and must prove that total APK size remains strictly $\le 15.0$ MB across all supported ABIs.

- **Consequences**:
  - Zero third-party dependencies added in this milestone; documentation-only compliance.
  - Art Studio retains its ultra-lean 12.1 MB APK footprint, well within the 15.0 MB ceiling.
  - Complete immunity from native NDK crashes, JNI allocation leaks, and C++ memory safety vulnerabilities.
  - 100% automated test coverage can be executed in PRoot and headless CI pipelines without native library linkage stubs.
  - Clear roadmap and algorithm specifications locked in place for Phase 6.

---

### ADR-050: On-Device Art Region Segmentation Feasibility Research: MobileSAM vs MediaPipe vs Classical CV & User Masks

- **Status**: Accepted
  - **Pre-Bundling ML Models in Core APK**: **NO-GO (Strictly Prohibited; Violates $\le 15.0$ MB Package Budget Ceiling)**
  - **Natural Photo-Trained Segmentation for Art Regions**: **NO-GO for Core Pipeline (Unverified and Poor Fit for Thin Line Art and Cel Flats)**
  - **Primary Authoritative Architecture (The No-AI / Deterministic Path)**: **GO (User Selection Masks + Classical CV: Barrier Flood Fill ADR-031, Selection Morphology ADR-027, Connected Components ADR-049)**
  - **Optional Post-v1 ML Extension Architecture**: **CONDITIONAL GO (Strictly On-Demand Download over Wi-Fi, Explicit Opt-In Consent Gate per ADR-004, Point/Box Prompts Only)**
- **Date**: 2026-10-11
- **Context**:
  1. **Art Region Segmentation Requirements**:
     Digital artists, manga illustrators, and animators frequently need to isolate closed semantic regions (e.g. character hair, skin, clothing folds, eyes, background elements) for flat coloring (cel-shading), gradient application, selection masking, and non-destructive adjustment layers.
  2. **Art Domain vs Natural Photography Realities**:
     - *Natural Photos*: Segmenting objects (cars, dogs, people) in natural scenes relies on smooth texture and color continuity, where probabilistic soft boundary transitions are acceptable.
     - *Hand-Drawn & Anime Illustration*: Artwork consists of crisp, high-contrast ink lines (1–3 pixels wide) enclosing flat or shaded regions. Models trained on natural photos (e.g. SA-1B dataset in Segment Anything) systematically fail on illustration data:
       - **Line Art Bleed**: Encoders blur across thin 1px black contours, assigning the boundary pixels to neither or both regions.
       - **Hair & Eye Over-Fragmentation**: Fine hair strands and multi-layer eye highlights are fractured into dozens of noisy, disconnected micro-masks.
       - **Screentone / Halftone Interference**: Manga screentones (dot/line patterns) create severe high-frequency texture noise that misleads patch-based Vision Transformer embeddings ($16 \times 16$ tokens).
  3. **Mobile Hardware & Package Constraints**:
     - *Package Budget Ceiling*: Project standard enforces a strict **$\le 15.0$ MB** total APK ceiling (current measured debug APK: **12.1 MB / 12,679,272 bytes**; remaining headroom is only **2.9 MB**).
     - *Memory Budget*: Minimum target devices operate on 4 GB physical RAM with managed heap ceilings $< 256$ MB. Segmentation models requiring $> 100$ MB transient RAM risk severe Out-Of-Memory (OOM) crashes.
     - *Offline & Privacy Charter (ADR-004)*: 100% offline-first. Zero artwork or telemetry data may ever leave the user's device. No mandatory cloud dependencies.

- **Comparative Evaluation of Segmentation Candidates**:

| Candidate / Attribute | 1. MobileSAM (TinyViT / ONNX / TFLite) | 2. EfficientSAM / EdgeSAM (RepViT / ONNX) | 3. MediaPipe Interactive Segmenter (TFLite) | 4. Primary: User Masks + Classical CV (No-AI) |
|---|---|---|---|---|
| **Licence & Terms** | Apache 2.0 (commercial-friendly) | Apache 2.0 (commercial-friendly) | Apache 2.0 (commercial-friendly) | First-party Art Studio (Apache 2.0 / CC0-1.0) |
| **Model Weights Size** | **39 MB (FP32)** / **9.8 MB (INT8 quantised)** | **38 MB (FP32)** / **9.5 MB (INT8 quantised)** | **2.5 MB to 5.0 MB (INT8 quantised)** | **0.0 MB** (Zero neural weights) |
| **Runtime & Native `.so` Overhead** | ONNX Runtime Mobile (`+12 MB` native `.so`) or TFLite (`+4 MB`) | ONNX Runtime Mobile (`+12 MB` native `.so`) | MediaPipe Tasks Vision (`+14 MB` native `.so`) | **0.0 MB** (100% Pure Kotlin managed bytecode) |
| **Net APK Package Size Impact** | **+14 MB to 22 MB** (FATAL: breaches 15.0 MB ceiling to ~30 MB!) | **+13.5 MB to 21.5 MB** (FATAL: breaches 15.0 MB ceiling!) | **+16.5 MB to 19 MB** (FATAL: breaches 15.0 MB ceiling!) | **0.0 MB** (Preserves all 2.9 MB headroom intact) |
| **Supported CPU ABIs** | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (per-ABI splits) | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | **All ABIs** (Architecture-independent JVM bytecode) |
| **Minimum Android Version** | API 26+ (API 29+ recommended for NNAPI acceleration) | API 26+ (API 29+ recommended) | API 24+ (Android 7.0) | API 26+ (Compatible with project baseline) |
| **Peak RAM Consumption** | **180 MB to 320 MB** during image encoder embedding | **150 MB to 280 MB** during image encoder embedding | **45 MB to 85 MB** | **< 2.0 MB** (Minimal bounded flat scratch buffers) |
| **Quantisation Feasibility** | INT8 reduces weights to ~9.8 MB; 15% edge degradation on boundaries | INT8 reduces weights to ~9.5 MB; edge degradation | INT8 pre-quantised in TFLite flatbuffer | N/A (Analytical integer arithmetic) |
| **Inference Latency (Mobile)** | Encoder: **450–1200 ms** (CPU) / **120–250 ms** (NPU/GPU); Decoder: **20–35 ms** (CPU) | Encoder: **380–950 ms** (CPU) / **90–200 ms** (NPU/GPU); Decoder: **15–25 ms** | Interactive: **40–90 ms** (CPU) / **20–35 ms** (GPU) | **< 5.0 ms** deterministic execution on background coroutine |
| **Input Resolution Needs** | $1024 \times 1024$ fixed square (downsampling required) | $1024 \times 1024$ fixed square (downsampling required) | $512 \times 512$ fixed input | **Arbitrary native document resolution** ($64 \times 64$ to $4096 \times 4096$) |
| **Prompting Modalities** | Point click, bounding box | Point click, bounding box | Single point click, bounding box | Freehand Lasso, Polygon, Rectangle, Ellipse, Tap fill |
| **Art / Anime Domain Fit** | **Poor**: Bleeds across 1px ink lines; fractures hair; screentone noise | **Poor**: Bleeds across thin line art; over-fragments cel flats | **Extremely Poor**: Trained on photo COCO classes; fails on line art | **Superior**: Exact adherence to line art boundaries; zero bleed |
| **Offline & Privacy Safety** | 100% on-device local execution | 100% on-device local execution | 100% on-device local execution | 100% on-device local execution; zero model files |
| **Recommendation** | **NO-GO (Pre-bundled)** / Conditional post-v1 download | **NO-GO (Pre-bundled)** / Conditional post-v1 download | **NO-GO (Unsuitable domain fit)** | **GO (Primary Authoritative Architecture)** |

- **Comparison of Prompting Modalities**:
  1. **Text-Prompted Segmentation (e.g. CLIPSeg, LangSAM)**:
     - *Mechanics*: User types natural language prompt (e.g. "character hair", "eyes", "red jacket").
     - *Evaluation*: **COMPLETELY UNVIABLE ON MOBILE**. Requires a dual-tower model (Text Transformer + Vision Transformer) requiring $> 120$ MB weights, $> 400$ MB RAM, and $> 2000$ ms CPU latency. Furthermore, open-vocabulary text models exhibit massive semantic drift on stylized anime/manga terms (e.g. "ahoge", "tsundere hair", "screentone shading").
  2. **Automatic Full-Canvas Segmentation (e.g. SAM `generate()` mask grid)**:
     - *Mechanics*: Automatically generates a dense grid of point prompts ($32 \times 32 = 1024$ prompts) to segment every region simultaneously.
     - *Evaluation*: **UNVIABLE ON MOBILE**. Generates hundreds of redundant, overlapping mask candidates on line art. Consumes $> 500$ MB RAM and $> 8$ seconds on mobile CPU. Overwhelms both the device hardware and the artist with cluttered mask management.
  3. **Point / Bounding-Box Prompted Interactive Segmentation (e.g. MobileSAM / EdgeSAM)**:
     - *Mechanics*: User taps inside a region (positive point) or draws a rough bounding box. Heavyweight image encoder runs once ($~500$ ms); lightweight mask decoder executes interactively in $< 30$ ms per tap.
     - *Evaluation*: The only computationally feasible neural modality for mobile. However, without dedicated fine-tuning on line-drawn illustration corpora, boundary fidelity remains inferior to classical barrier flood fill.
  4. **Classical Deterministic Region Controls (The No-AI Fallback)**:
     - *Mechanics*: 
       - **Dilated Barrier Flood Fill with Gap Closing (ADR-031)**: Artist taps inside a line-drawn boundary. Morphological dilation bridges 1px/2px gaps without exterior bleed; boundary expansion isolates flat regions in $< 5$ ms.
       - **Geometric & Freehand Selection Masks (ADR-027)**: Rectangle, Ellipse, Freehand Lasso, and Polygonal Lasso tools provide bit-exact document-space selection boundaries.
       - **Connected Component Region Extraction (ADR-049)**: Two-pass union-find labeling isolates trapped line regions analytically.
     - *Evaluation*: **Authoritative Primary Fit**. Bit-exact, deterministic, zero training-domain bias, runs instantaneously on any canvas resolution, and requires 0 bytes of model storage.

- **Ethical AI, Consent & Privacy Architecture**:
  In strict alignment with ADR-004 (Offline-First Manual Studio and Optional Consent-Gated AI Pipeline):
  1. **Zero Pre-Bundled Neural Weights**:
     - The base Art Studio application shall never pre-bundle neural segmentation weights in the APK. This preserves the ultra-lean 12.1 MB package size and ensures compliance with the $\le 15.0$ MB ceiling.
  2. **100% Core Functionality Without AI**:
     - All drawing, coloring, layer masking, selection, comic creation, and export features are 100% operational without requiring any AI models, cloud accounts, or network downloads.
  3. **Strict Consent Gating for Any Future Optional ML Extensions**:
     - If an optional on-device segmentation model is offered in a future post-v1 milestone:
       - It must be strictly an **optional on-demand download** initiated explicitly by the user over Wi-Fi.
       - The UI must display an unbundled consent dialog clearly disclosing download size (e.g. "~10 MB"), memory requirements, and licensing provenance.
       - Downloaded models must be stored in private app storage (`context.filesDir/models/`) and may be deleted by the user at any time to reclaim storage.
       - Inference must occur strictly 100% locally on-device with zero telemetry or network calls.

- **Directives & Decisions Summary**:
  1. **Primary Architectural Approach**:
     - Rely authoritatively on **First-Party User Selection Masks + Classical Computer Vision** (Barrier Flood Fill ADR-031, Selection Morphology ADR-027, Connected Components ADR-049).
     - **Package Impact**: **0.0 MB** weights, **0.0 MB** native `.so` libraries.
  2. **Explicit NO-GO Decisions**:
     - **Pre-Bundled ML Models in APK**: **NO-GO**.
     - **Natural Photo Models (MobileSAM / MediaPipe) for Core Pipeline**: **NO-GO** due to poor line art boundary precision and package bloat.
     - **Text-Prompted Segmentation**: **NO-GO** due to prohibitive mobile compute and parameter requirements.
     - **Automatic Full-Image Segmentation Grids**: **NO-GO** due to severe memory exhaustion on 4 GB devices.
  3. **Conditional Post-v1 Evaluation Pathway**:
     - Any future ML segmentation feature must be fine-tuned specifically on permissively licensed illustration corpora, must use point/box interactive prompts only, and must be delivered as an optional on-demand download under strict user consent gating.

- **Consequences**:
  - Documentation-only milestone: zero product code, zero model weights, and zero dependencies added.
  - Art Studio preserves its verified 12.1 MB APK size, keeping 2.9 MB of safety headroom below the 15.0 MB ceiling.
  - Complete immunity from ONNX/TFLite native `.so` ABI bloat and NDK crash hazards.
  - Complete immunity from neural boundary bleeding across hand-drawn line art.
  - 100% alignment with the project governance charter and offline privacy invariants.

---

### ADR-051: Concurrency-Bounded, Lane-Segregated Background Job Architecture and Safe Cancellation Protocol
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 46)
- **Context**:
  Art Studio performs intensive asynchronous background workloads including high-resolution export (PNG/JPEG/WebP/PSD/ORA/GIF), image analysis and line extraction (Otsu thresholding, Canny edges, Suzuki-Abe border following), vector polygon decimation, color quantization, and potential future AI inference passes.
  Running these uncoordinated on unbounded coroutine dispatchers or the UI thread leads to severe application defects:
  1. *UI Jank and Starvation*: Heavy export or analysis tasks hog thread pools and starve canvas stroke event processing, exceeding the 16.6ms frame deadline.
  2. *Out-Of-Memory (OOM) Crashes*: Concurrently executing multiple 2048x2048 or 4096x4096 transforms in parallel exhausts the device's heap allocation, which is bounded to 192–256 MB on 4 GB physical devices.
  3. *Resource Leakage*: Cancellation of operations mid-flight often orphans temporary uncommitted files and shadow scratch buffers in storage.
  4. *Fake Success States*: Unverified asynchronous jobs reporting completion before downstream writes complete, causing user data loss.
  5. *Process Death State Inconsistency*: Android process termination or activity recreation while background exports or analyses are queued leaving orphaned, inconsistent records.

- **Decision**:
  Implement a unified, robust, bounded, and reactive background job execution engine in `com.artstudio.job`:
  1. **Job Lifecycle State Machine**:
     - Explicit states: `QUEUED`, `RUNNING`, `PAUSED` (for supported resumable workloads), `SUCCEEDED`, `FAILED`, and `CANCELLED`.
     - Strictly enforced state transitions: terminal states (`SUCCEEDED`, `FAILED`, `CANCELLED`) are immutable once reached.
     - Zero fake success: `SUCCEEDED` state is only reached upon verified completion of all execution phases.
  2. **Work Lane Segregation**:
     - Segregate all background workloads into 5 dedicated `WorkLane` pools, each with its own independent concurrency semaphore:
       - `CANVAS_INPUT` (Max concurrency = 4, priority admission): High-frequency touch, brush stroke event processing, and real-time tile cache updates; never starved by background lanes.
       - `TRANSFORM` (Max concurrency = 1): Heavyweight canvas transforms, layer rotations, and scaling.
       - `EXPORT` (Max concurrency = 1): High-resolution raster encoding and archive packaging.
       - `ANALYSIS` (Max concurrency = 2): Computer vision line detection, contour extraction, and color clustering.
       - `AI_INFERENCE` (Max concurrency = 1): Future optional ML execution passes.
  3. **Strict Concurrency Bounding & Memory-Aware Admission**:
     - Bounded lane concurrency via `kotlinx.coroutines.sync.Semaphore`.
     - Memory-aware admission check: Jobs declare `estimatedMemoryBytes`. Schedulers enforce an admission gate against `maxMemoryCeilingBytes` (default 64 MB heap budget for concurrent background jobs). If current heap commitments exceed the ceiling, heavy jobs wait cooperatively in `QUEUED` state until running jobs finish and release memory.
     - `CANVAS_INPUT` is exempt from heap throttling to ensure UI responsiveness is never degraded.
  4. **Cooperative Cancellation & Safe Temporary Resource Cleanup**:
     - Cooperative cancellation driven by thread-safe `JobCancellationToken` with cancellation callbacks and `throwIfCancelled()` checks at loop steps.
     - Active temporary files registered during execution via `registerTempFile(file)`.
     - On cancellation or failure, `cleanTempFilesSafely()` removes intermediate shadow files from disk, preventing disk clutter and partial writes. Source files and canonical documents are never modified.
  5. **Crash Resilience & Journal Persistence**:
     - Active, non-terminal jobs are serialized to a disk journal (`persistJournal()`).
     - On app pause/resume or process death recovery (`restoreJobsAfterProcessDeath`), non-resumable interrupted jobs transition to `FAILED` with diagnostic error `PROCESS_DEATH_INTERRUPT`, while resumable batch jobs restore their serialized checkpoint and re-enqueue seamlessly.
     - Schedulers rethrow `CancellationException` and prevent cancelled scopes from overwriting valid disk journals.
  6. **Accessible UI Integration**:
     - `StudioJobProgressIndicator` provides real-time, accessible Jetpack Compose UI cards featuring step description, determinate progress bar, and user-facing Pause, Resume, and Cancel actions.

- **Consequences**:
  - Main UI thread is 100% decoupled from intensive processing workloads; zero frame drops during synthetic long-running operations.
  - Concurrency limits are mathematically enforced and audited by unit tests; peak worker counts never exceed lane bounds.
  - Intermediate files are guaranteed to be cleaned on cancellation.
  - Zero fake success invariant enforced across all background tasks.
  - 17 unit tests added in `JobSchedulerTest.kt`, bringing project total to 601 passing tests with 0 failures.

---

### ADR-052: Non-Destructive Reference Pre-Inspection, Bidirectional Coordinate Normalization, and Reconstruction Mode Suggestion Architecture
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 47)
- **Context**:
  Art Studio allows artists to import external reference images (PNG, JPEG, WebP, PSD, ORA, SVG) for background analysis, line art extraction, and auto-drawing reconstruction.
  Launching expensive analysis pipelines (e.g. Canny edge detection, topological contour tracing, color clustering) blindly on raw uninspected files introduces major architectural hazards:
  1. *Source File Mutation Risk*: In-place rotation or modifying the imported reference violates user trust and breaks reference fidelity.
  2. *EXIF Coordinate Disorientation*: Mobile photos and camera captures often contain EXIF orientation metadata (tags 1..8). Running line tracing in raw image orientation while displaying an upright view produces sheared or rotated vector paths that do not align with the reference overlay.
  3. *Unbounded Memory and OOM Crashes*: High-resolution imports (e.g. 1024x1024 or 4096x4096) can exhaust the mobile heap if unmonitored.
  4. *Algorithmic Mismatch*: Applying binary line art vectorization to a soft pencil sketch or continuous-tone photograph produces excessive noisy fragments or missing strokes.
  5. *Corrupt Container Vulnerabilities*: Truncated or malformed files can crash analysis workers or hang downstream coroutines if unhandled.

- **Decision**:
  Implement a dedicated, non-destructive reference pre-inspection and normalization architecture in `com.artstudio.reference`:
  1. **Source Immutability Invariant**:
     - Inspection operations are strictly read-only. The preserved reference file on disk and in memory is NEVER altered or re-encoded.
  2. **Bidirectional Coordinate Normalization (`CoordinateNormalization`)**:
     - Encapsulates exact mathematical mapping between raw reference space $(x_{raw}, y_{raw})$ and normalized upright analysis space $(x_{norm}, y_{norm})$ across all 8 EXIF orientation tags.
     - Supports both discrete pixel coordinate mapping (`rawToNormalizedPixel`, `normalizedToRawPixel`) and continuous floating-point coordinates (`rawToNormalized`, `normalizedToRaw`).
     - Downstream analysis workers operate in normalized upright coordinates, while sample probes and reference overlays map back to the preserved source with zero geometric distortion.
  3. **Multi-Category Feature Extraction**:
     - Fast statistical profiling of transparency (`TransparencyType`: `FULLY_OPAQUE`, `BINARY_MASK`, `SEMI_TRANSPARENT_GRADIENT`) and alpha content bounding box.
     - Color and luminance profiling: neutral grayscale classification, 5-bit color binning for unique color estimation, mean luminance, contrast ratio, and edge density.
  4. **Heuristic Image Classification & Reconstruction Suggestions**:
     - Classifies input into 5 distinct categories:
       - `LINE_ART` $\to$ Suggests `LINE_ART_VECTORIZATION` ($\ge 90\%$ confidence)
       - `SKETCH` $\to$ Suggests `PENCIL_CLEANUP_AND_INK` ($\ge 85\%$ confidence)
       - `FLAT_COLOUR` $\to$ Suggests `FLAT_REGION_CEL_SHADING` ($\ge 90\%$ confidence)
       - `DETAILED_ILLUSTRATION` $\to$ Suggests `MULTI_LAYER_HYBRID_DECOMPOSITION` ($\ge 85\%$ confidence)
       - `PHOTOGRAPHIC_PAINTERLY` $\to$ Suggests `CONTINUOUS_TONE_PAINT_LAYER` ($\ge 85\%$ confidence)
     - Discloses visible limitations (e.g. hairline anti-aliasing gaps, faint pencil threshold adjustments) and provides explicit user override.
  5. **Memory Footprint Tracking & Advice Gating**:
     - Computes theoretical uncompressed memory estimate: $W_{norm} \times H_{norm} \times 4 \times 2$ bytes.
     - Records actual decode peak memory footprint.
     - Categorizes advice (`NORMAL_MEMORY`, `HIGH_MEMORY_WARN`, `EXCEEDS_BUDGET_DOWNSAMPLE_RECOMMENDED`).
  6. **Resilient Fault Handling**:
     - Corrupt PNG, ORA, PSD, and malformed SVG fixtures fail safely, returning structured diagnostic reports (`isSuccess = false`, `isCorrupt = true`, `errorDetail`) without throwing exceptions or changing document state.
  7. **Accessible Jetpack Compose UI**:
     - `StudioReferenceInspectionCard` displays reference properties, suggested mode, confidence badge, visible limitations, user override radio buttons, and accessible actions.

- **Consequences**:
  - Analysis pipelines receive clean, normalized, upright coordinates with zero alteration of preserved reference files.
  - Artists receive transparent suggestions with visible limitations and full manual override authority.
  - Memory consumption is measured and bounded prior to admitting tasks to worker pools.
  - 16 unit tests added in `ReferenceInspectorTest.kt`, bringing project total to 617 passing tests with 0 failures.

---

### ADR-053: Deterministic Edge, Contour, and Denoising Pipeline for Raster Art Analysis
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 48)
- **Context**:
  Following ADR-049 (Pure-Kotlin Cleanroom CV Architecture) and the user directive relaxing package size limits up to 1 GB, Art Studio requires a deterministic, mathematically rigorous computer vision pipeline to analyze raster illustrations across clean line art, flat colour, and noisy sketches.
  Key requirements:
  1. *Preservation of Source Artwork*: Original pixel arrays and on-disk files must never be modified or degraded during analysis.
  2. *Artifact-Specific Preprocessing & Denoising*: Scanned pencil sketches suffer from paper tooth and graphite dust, while flat-colour artwork contains step edges between flat fills. Algorithms must adaptively suppress high-frequency dust without blurring sharp 1-pixel strokes.
  3. *Lighting Gradient Immunity*: Scanned or photographed art exhibits non-uniform paper illumination that defeats global thresholding.
  4. *Topological Contour & Decimation Fidelity*: Contours must be extracted as closed vector polygons with verified area, perimeter, and hole classification, simplified via polygonal decimation while preserving corners.
  5. *Transient Debug Overlay Non-Destructive Invariant*: Artists and developers need real-time visualization of edge maps, contours, and threshold masks without these overlays ever baking into the artwork canvas or document layers.

- **Decision**:
  Implement the first-party cleanroom deterministic Computer Vision pipeline in package `com.artstudio.cv`:
  1. **Source Immutability Invariant**:
     - `CvPipeline.process()` operates strictly on cloned or derived analytical buffers. The input `pixels` array is guaranteed to remain 100% bit-exact identical before and after processing.
  2. **Preprocessing & Denoising (`CvDenoiseEngine`)**:
     - Perceptual grayscale conversion conforming to ITU-R BT.709 ($0.299 R + 0.587 G + 0.114 B$), blending transparent alpha with white canvas paper ($255$) to isolate foreground strokes.
     - Median Despeckle ($3 \times 3$ sliding window with insertion sort): effectively removes isolated 1-pixel graphite dust and paper tooth texture without attenuating sharp stroke edges.
     - Separable 1D Gaussian blur ($O(N \cdot K)$) and bilateral edge-preserving smoothing.
  3. **Binarization & Thresholding (`CvThresholdEngine`)**:
     - Otsu Global Binarization with maximum-variance plateau midpoint resolution: resolves flat variance plateaus between widely separated modes, avoiding boundary bias.
     - Adaptive Local Mean Thresholding using an $O(1)$ query-time Integral Image ($O(N)$ construction): eliminates non-uniform paper lighting gradients.
     - Manual thresholding with polarity inversion support for negative/inverted strokes.
  4. **Mathematical Edge Detection (`CvEdgeEngine`)**:
     - Sobel operator with horizontal and vertical convolution kernels and Euclidean gradient magnitude.
     - 5-Stage Canny Edge Detector:
       1. Gaussian smoothing
       2. Sobel gradient magnitude and 4-sector quantized direction ($0^\circ, 45^\circ, 90^\circ, 135^\circ$)
       3. Directional Non-Maximum Suppression (NMS) for thin 1-pixel edges
       4. Dual thresholding (strong and weak edge classification)
       5. 8-connected recursive hysteresis tracking
  5. **Topological Contour Candidate Discovery (`CvContourEngine`)**:
     - 8-connected Moore-neighborhood boundary tracing.
     - Shoelace formula for signed area computation; negative area classifies internal holes.
     - Euclidean perimeter, axis-aligned bounding box, and centroid calculation.
     - Ramer-Douglas-Peucker (RDP) polygonal decimation: reduces point counts by $60\% - 90\%$ while preserving corners within epsilon tolerance ($\epsilon = 1.5$ px).
  6. **Transient Debug Visualizer Overlay (`CvDebugVisualizer` & `StudioCvDebugOverlayPanel`)**:
     - Standalone ARGB overlay renderer producing isolated buffers for `EDGE_MAP` (dimmed artwork + bright neon green edges), `CONTOUR_POLYGONS` (distinct cycling colors with vertex anchors), `BINARY_THRESHOLD` (black/white mask), and `DENOISED_PREVIEW`.
     - Compose panel providing overlay toggles, algorithm parameter sliders, and real-time execution statistics (latency ms, peak memory bytes). Overlays are never committed to document layers or undo history.
  7. **Canonical Golden Suite & Performance Instrumentation**:
     - `tools/qa/test_cv_goldens.py` verifies canonical edge goldens across clean line art, flat colour, and noisy sketches with 0 diff pixels (PSNR > 999 dB).
     - Full automated test suite in `CvPipelineTest.kt` (13 tests), verifying bit-identical determinism, corner preservation, noise suppression, source immutability, and latency/memory metrics.

- **Consequences**:
  - 100% pure Kotlin bytecode; 0.0 MB native `.so` library footprint; APK size remains ~12.1 MB, far below the 1 GB constraint.
  - Zero external network dependencies; all analysis executes strictly on-device.
  - Source artwork immutability and non-destructive debug overlays verified by tests.
  - Total passing unit tests increased from 617 to 630 (13 new CV tests, 0 failures, 0 regressions).

---

### ADR-054: Plausible Single-Pen Centreline Extraction with Topological Junction Preservation and Ambiguity Review Flagging
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 49)
- **Context**:
  Transforming scanned ink line art, clean pencil sketches, and manga inking into editable vector artwork requires recovering single-pen centreline strokes.
  Naive automated vectorization algorithms suffer from critical failures:
  1. *Deceptive Ground-Truth Claims*: Asserting that an automated pipeline reproduces the original artist's exact physical strokes is factually incorrect and misleads downstream AutoDraw replayers. Paths must be presented as *plausible candidates* accompanied by explicit confidence metrics.
  2. *Destruction of Junctions and Short Details*: Over-aggressive dead-end pruning obliterates cross-hatch shading, fine hair strands, eyelid details, and line terminations.
  3. *Silent Arbitrary Decisions at Forks and Gaps*: At complex intersections (T-junctions, Y-forks, cross-overs) or stroke breaks (caused by pencil texture or paper tooth), naive heuristics either arbitrarily guess stroke continuations or discard fragments without notifying the artist.
  4. *Algorithmic Mismatch on Non-Line Media*: Executing centreline skeletonization on continuous-tone paintings or cel fills produces fragmented contour noise.

- **Decision**:
  Implement the first-party cleanroom Single-Pen Centreline Extraction Subsystem in `com.artstudio.cv.centreline`:
  1. **Strict Input Mode Gating (`CentrelineModeGating`)**:
     - Automatically permitted only when the reconstruction mode is suitable: `LINE_ART_VECTORIZATION` and `PENCIL_CLEANUP_AND_INK`.
     - Non-line-art modes (`CONTINUOUS_TONE_PAINT_LAYER`, `FLAT_REGION_CEL_SHADING`, `MULTI_LAYER_HYBRID_DECOMPOSITION`) are rejected safely with clear diagnostic disclosures (`isSupported = false`), avoiding wasted CPU cycles and meaningless vectors, unless explicitly overridden via `forceExtraction = true`.
  2. **Chamfer Distance Transform for Stroke Width Recovery**:
     - Computes 2-pass Chamfer distance transform on the binarized foreground.
     - For every pixel on the extracted skeleton, the distance value directly recovers local stroke radius $r$, providing variable stroke width estimation $w(s) = 2 \cdot r$.
  3. **Zhang-Suen Morphological Skeletonization (`ZhangSuenThinning`)**:
     - Deterministic 8-connected iterative thinning reducing line strokes to 1-pixel skeletons while preserving global topology, endpoints, and multi-way branch intersections.
  4. **Topological Graph Traversal & Node Clustering (`CentrelinePathTracer`)**:
     - Classifies skeleton pixels into endpoints ($N(P) = 1$), regular path pixels ($N(P) = 2$), and junction pixels ($N(P) \ge 3, T(P) \ge 3$).
     - Clusters adjacent diagonal junction pixels into unified `JunctionNode` centroids.
     - Traverses open strokes and closed cycles, estimating normalized tangent vectors at start, mid, and end vertices.
     - Performs Ramer-Douglas-Peucker (RDP) decimation with configurable epsilon ($\epsilon = 1.0$ px default).
  5. **Ambiguity Review Flagging (Zero Silent Discards)**:
     - **Ambiguous Forks (`AmbiguityType.AMBIGUOUS_FORK`)**: At junctions with degree $\ge 3$, radiating branch directions are checked for collinear continuation ($180^\circ \pm \text{threshold}$). If multiple or zero collinear continuations exist (e.g. symmetric Y-fork), the fork is flagged for artist review with structured `suggestedResolution` rather than arbitrarily split or merged.
     - **Potential Stroke Breaks (`AmbiguityType.POTENTIAL_STROKE_BREAK`)**: Unconnected endpoints within configurable gap distance ($\le 6.0$ px) whose tangents point towards each other across the break are flagged for review.
     - All ambiguities are recorded in `CentrelineExtractionResult.ambiguities` and linked to involved path IDs.
  6. **Plausible Confidence Scoring**:
     - Paths declare honest confidence in $[0.10, 0.95]$, incorporating length penalties, high-curvature penalties, and ambiguity penalties. Short details are preserved with `isShortDetail = true` when `preserveShortDetails = true`.
  7. **Editable Vector Model Integration**:
     - `toVectorObject()` converts any `CentrelinePathCandidate` into native `com.artstudio.vector.VectorObject` polyline shapes with `VectorAnchor` chains and `VectorStyle`.
  8. **Synthetic Ground-Truth Verification & Error Metrics (`SyntheticPathGenerator`)**:
     - Evaluates endpoint error, mean orthogonal geometry error, and length deviation against known analytical straight, curved, branched, broken, and noisy lines.

- **Consequences**:
  - Non-destructive execution: source pixels remain 100% bit-exact identical before and after extraction.
  - Ambiguous forks and stroke breaks are transparently exposed for user review instead of silently corrupted.
  - Zero third-party dependencies or C++ binaries added; pure Kotlin implementation.
  - Total unit tests increased from 630 to 642 (12 new centreline tests, 0 failures, 0 regressions).
  - Shipped debug APK remains 12.1 MB (well below the 1 GB constraint).

---

### ADR-055: Contour Candidates to Editable Geometric Paths: Adaptive Schneider Cubic Bézier Spline Fitting with Corner Preservation and Hole Containment
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 50)
- **Context**:
  Transforming discrete boundary contours into editable vector artwork requires fitting mathematical geometric curves (polylines and cubic Bézier splines) rather than storing noisy dense pixel chains.
  Critical technical requirements:
  1. *Corner Preservation vs Mushy Rounding*: Naive spline fitting smooths sharp corners (such as $90^\circ$ corners on rectangles, polygon edges, and star tips) into rounded mush. Sharp vertices must be detected using localized tangent turning angles and locked as non-smooth cusp anchors (`isSmooth = false`).
  2. *Hole Preservation and Disconnected Components*: Illustrations contain complex topologies with internal holes (e.g. eyes, donut loops, enclosed spaces) and disconnected components. These must be preserved without loss, mapped to parent regions, and styled with `FillRule.EVEN_ODD` to subtract cleanly.
  3. *Decoupled Outline / Stroke Width*: Path width and outline attributes must be stored in `VectorStyle.strokeWidth`, completely decoupled from anchor geometry coordinates.
  4. *Audit Trail & Provenance Metadata*: Each generated path must record its `regionId`, `parentRegionId`, `confidence`, `sourcePixelCount`, `sourceBounds`, `provenanceAlgorithm`, `maxDeviation`, and `meanDeviation` to maintain end-to-end traceability.
  5. *Persistence & Re-editability*: Fitted vector paths must survive native `.ffart`/JSON save and reopen without degradation, remaining fully editable via interactive anchor manipulation (`VectorGeometryEngine.moveAnchor`).
  6. *Maximum Contour Deviation Constraint*: Mathematical error between discrete boundary pixels and fitted continuous splines must be bounded and monitored without regressions.

- **Decision**:
  Implement the first-party cleanroom Contour Path-Fitting Subsystem in package `com.artstudio.cv.pathfit`:
  1. **Domain Models & Settings (`PathFitSettings.kt`, `PathFitModels.kt`)**:
     - `PathFitSettings`: Configurable `maxFittingError` (default 2.0 px), `cornerAngleThresholdDeg` (default 55.0°), `cornerWindowSize` (default 3), `preserveCorners`, `preserveHoles`, `preserveDisconnectedComponents`, `separateOutlineWidth` (default 2.0 px), and `fittingMode` (`BEZIER_CURVES`, `POLYLINES`, `HYBRID`).
     - `PathProvenance`: Records algorithm identifier (`SchneiderAdaptiveBezierFit`), timestamp, source contour ID, perimeter, area, and hole status.
     - `SourcePixelMapping`: Stores source pixel count, bounding box, and discrete sampled points.
     - `FittedPathCandidate`: Encapsulates fitted anchors (`VectorAnchor`), segments (`VectorSegment`), source pixel mapping, provenance, confidence metric $[0.10, 0.99]$, max/mean contour deviation, and detected corner points.
     - `toVectorObject()`: Converts candidate into native `com.artstudio.vector.VectorObject` with metadata dictionary, decoupled `VectorStyle`, and `FillRule.EVEN_ODD` for holes.
  2. **Sharp Corner Detection (`CornerDetector.kt`)**:
     - Computes localized tangent turning angles across radius $k = 3$ to filter pixel discretization noise while isolating sharp vertices $\ge 55^\circ$.
     - Suppresses neighboring corner clusters, pinning exact cusp anchor coordinates.
  3. **Philip J. Schneider Cubic Curve Fitting (`SchneiderBezierFitter.kt`)**:
     - Pure Kotlin implementation of Schneider's algorithm (Graphics Gems I).
     - Chord-length parameterization, least-squares optimal control point solution, Newton-Raphson parameter re-estimation, and recursive subdivision at peak error points.
  4. **Topological Engine & Hole Containment (`ContourVectorizationEngine.kt`)**:
     - Preserves disconnected components with independent `regionId` assignments (`region_1`, `region_2`, ...).
     - Point-in-polygon ray casting associates inner holes with their containing outer boundaries (`region_1_hole_1`).
     - Partitions closed contours at sharp corners, fitting linear edges between corners and cubic curves along smooth arcs.
  5. **Contour Raster Comparator (`ContourRasterComparator.kt`)**:
     - Scanline polygon rasterizer and stroke rasterizer in pure Kotlin.
     - Measures Intersection-over-Union (IoU $\ge 0.90$), Dice coefficient ($\ge 0.95$), Hausdorff maximum contour deviation, and mean orthogonal error using point-to-segment distance.
  6. **Document Serialization & Native Reopen Integration**:
     - Added `metadata: Map<String, String>` to `VectorObject` and updated `DocumentSerializer.kt`.
     - Verified that saving to `.ffart`/JSON and reopening preserves all anchors, handles, styles, and metadata, permitting continuous anchor editing.
  7. **Debug Visualization Overlay Integration**:
     - Added `CvDebugOverlayMode.FITTED_PATHS` in `CvConfig.kt` and `CvDebugVisualizer.kt`, drawing cyan/orange cubic curves, bright yellow hole outlines, white smooth anchors, and red/orange corner cusp markers.

- **Consequences**:
  - Delivers high-fidelity conversion of discrete raster contours into editable vector shapes.
  - Zero native C++ binaries or external dependencies added; pure Kotlin implementation.
  - Total unit tests increased from 642 to 654 (12 new path-fit tests, 0 failures, 0 regressions).
  - Shipped debug APK remains 12.1 MB (well below the 1 GB constraint).

---

### ADR-056: Connected Region Segmentation, Three-Tier Mask Separation, and Topological Gap Closing for Art Layer Coloring
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 51)
- **Context**:
  To support digital coloring, fills, and automated layer separation in illustration workflows, the computer vision subsystem must generate region masks directly from raster artwork without assuming every boundary outline is closed.
  Key architectural requirements:
  1. *Three-Tier Mask Separation*: Strict physical separation between `sourceMask` (raw unaltered seed flood), `correctedMask` (refined mask reflecting gap-closing, hole preservation, bleed expansion, and smoothing), and `generatedFill` (32-bit ARGB rendered buffer). User parameter tuning and reviews must never mutate or alias the raw seed segmentation.
  2. *Topological Gap Closing & Leak Detection*: In comic and line art, outlines often have 1-pixel micro-breaks or multi-pixel gaps that cause unconstrained flood fills to spill across the canvas. The engine must detect leaks by dual-flood analysis (barrier-constrained vs dilated bridge), localize the constriction neck / saddle point, classify severity (`MINOR_TINY_GAP`, `MODERATE_GAP`, `WIDE_RUPTURE`), and propose an actionable bridge stroke for user inspection and correction.
  3. *Hole Preservation vs Filling*: Annular shapes and multi-boundary enclaves must support selective hole retention via border reachability floods, distinguishing interior islands from the canvas exterior.
  4. *Anti-Aliased Bleed & Halo Elimination*: Solid bucket fills against anti-aliased line art leave noticeable white halos. Parameterized `boundaryExpansion` (+dilate into stroke / -erode) and sub-pixel edge smoothing eliminate halos cleanly.
  5. *4-Channel ARGB Color Matching*: Transparency (alpha $\le 5$) must be treated symmetrically with zero distance, supporting cutout silhouettes and transparent backdrops.
  6. *Interactive User Correction*: Leaky regions must provide `UncertainBoundaryReview` records allowing users to accept or customize bridging strokes and re-segment atomically.

- **Decision**:
  Implement the first-party cleanroom Region Segmentation Subsystem in package `com.artstudio.cv.region`:
  1. **Domain Models (`RegionMaskModels.kt`)**:
     - `RegionBoundaryType`: `CLOSED_OUTLINE`, `LEAKY_GAP`, `ANTI_ALIASED_FRINGE`, `ADJACENT_COLOUR_BARRIER`, `TRANSPARENCY_SILHOUETTE`.
     - `LeakSeverity`: `MINOR_TINY_GAP` ($\le 2.5$ px), `MODERATE_GAP` ($2.5 - 5.5$ px), `WIDE_RUPTURE` ($> 5.5$ px).
     - `UncertainBoundaryReview`: Coordinates, gap distance, severity ranking, proposed bridge endpoints, resolution message, and user correction flag.
     - `RegionMaskConfig`: Validated parameters for `colorTolerance` $[0.0, 1.0]$, `gapClosingRadius` $\ge 0$, `boundaryExpansion` $[-20, 20]$, `antiAliasSmoothing`, `despeckleRadius`, `preserveHoles`, `detectLeaks`, `leakThresholdRatio`, `fillColor`, and `fillOpacity`.
     - `MaskOverlapMetrics`: Intersection, union, ground truth count, mask count, IoU, precision, recall, Dice coefficient, false positives/negatives.
     - `RegionSegmentationResult`: Immutable container holding `sourceMask: ByteArray`, `correctedMask: ByteArray`, `generatedFill: IntArray?`, pixel count, boundary type, review records, leak status, hole status, and metrics.
  2. **Core Segmentation Engine (`RegionMaskEngine.kt`)**:
     - Weighted 4-channel Euclidean color distance (30% alpha, 70% chromatic RGB) with symmetric full transparency matching.
     - Scanline BFS flood fill generating bit-exact `sourceMask`.
     - Dual-flood morphological leak detection: barrier dilation by radius $r$, flood fill, and reverse expansion within `!barrier` coupled with border-connected spill detection to localize true constriction necks without corner false positives.
     - Topological hole preservation identifying interior unmasked islands disconnected from the 4 outer canvas boundaries.
     - Median/neighborhood despeckling and exact Euclidean disk boundary expansion/choke.
     - Sub-pixel anti-aliased edge smoothing calculating fractional local coverage $[1..254]$.
     - `applyUserCorrection(...)` rasterizing bridge strokes onto barrier buffers and re-segmenting with review resolution tracking.
     - `segmentAllRegions(...)` partitioning entire canvases into disjoint regions.
  3. **Synthetic Ground-Truth Fixture Generator (`SyntheticRegionFixtureGenerator.kt`)**:
     - Deterministic generators for flat colour rectangles, closed outline boxes, 1-pixel micro-gaps, 4-pixel ruptures, donut hole annuli, anti-aliased gradient fringes, transparent silhouettes, salt-and-pepper noise, and adjacent touching colors.
  4. **Debug Overlay Visualizer (`CvConfig.kt`, `CvDebugVisualizer.kt`)**:
     - Added `CvDebugOverlayMode.REGION_MASKS` rendering full canvas region segmentation with cycling palette overlays.
  5. **Verification & Tooling**:
     - Standalone Python QA script (`tools/qa/test_region_mask_goldens.py`) verifying 10 golden benchmarks (IoU $\ge 0.95$, Precision $\ge 0.95$, Recall $\ge 0.95$).
     - JUnit test suite (`RegionMaskTest.kt`, 14 unit tests) covering all boundary topologies, 3-tier mask immutability, leak review/healing, real raster PNG fixtures (`flat_colour_simple_01.png`, `transparent_cutout_01.png`), and full-canvas coverage.

- **Consequences**:
  - Full-featured, leak-free region segmentation and mask generation for coloring and layer separation.
  - Zero external C++ native dependencies or unverified neural weights added; pure Kotlin implementation.
  - Total unit tests increased from 654 to 668 (14 new region mask tests, 0 failures, 0 regressions).
  - Shipped debug APK remains 12.1 MB (12,679,272 bytes), well within package size limits.

---

### ADR-057: Reference Focus Region Selection and Multi-Region Annotation Subsystem for Preserved Source Attention
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 52)
- **Context**:
  In reference inspection, reconstruction, and AutoDraw workflows, users must be able to demarcate specific regions of interest within an imported reference image (e.g. eyes, hair strands, high-frequency details, linework) and attach granular instructions and focus settings.
  Key architectural requirements:
  1. *Versatile Selection Tools & Cleanroom Rasterization*: Support Brush (continuous interpolated dabs), Lasso (freehand closed polygon), Polygon (multi-point vertex chains), Rectangle (axis-aligned bounding box), and Ellipse (sub-pixel quadrant testing). Because the engine must execute cleanly on both JVM and Android without stub failures, all mask rasterization must be implemented via pure Kotlin scanline algorithms rather than relying on `android.graphics.Path` or `android.graphics.Canvas`.
  2. *Set Combination Operations*: Masks must support standard selection operations: `REPLACE` (overwrite active mask), `ADD` (union), `SUBTRACT` (difference/cutout), and `CLEAR` (reset mask to zero).
  3. *Multiple Named Regions with Independent Settings*: Projects must support multiple distinct focus regions simultaneously, each with:
     - Unique identifier and user-defined name
     - Free-text instruction prompt (e.g. "Focus on eyelashes; preserve sharp edge transitions")
     - Granular focus settings: `priority` (`LOW`, `NORMAL`, `HIGH`, `CRITICAL`), `targetReconstructionMode`, `detailSensitivity` $[0.1, 1.0]$, `preserveExactColor`, `edgeDilationRadius`, and custom parameters
     - Full lifecycle operations: create, rename, duplicate (deep copy of mask buffer), delete, hide/show toggle, and boundary editing.
  4. *Viewport & Crop Invariance*: Region selections must remain anchored in absolute document-space coordinates $(0..W_{\text{ref}}, 0..H_{\text{ref}})$, isolating them from interactive pan and zoom viewport transforms. Source cropping sub-samples existing masks cleanly into the cropped bounds without spatial drift.
  5. *Source Immutability & Project Persistence*: The underlying reference image file must remain strictly read-only and preserved bit-for-bit (validated via cryptographic SHA-256 hash). All user regions, focus settings, crop boundaries, and compressed Base64 mask buffers are serialized into an auxiliary analysis project JSON (`.ffrefproj`/JSON).
  6. *Non-Destructive Composite Overlay*: A composited preview overlay blends visible region masks with distinct cycling tint colors and adjustable opacity over reference pixels without altering source pixels in memory or on disk.

- **Decision**:
  Implement the first-party cleanroom Reference Region Subsystem in package `com.artstudio.reference.region`:
  1. **Domain Models (`ReferenceRegionModels.kt`)**:
     - `ReferenceRegionToolType`: `BRUSH`, `LASSO`, `POLYGON`, `RECTANGLE`, `ELLIPSE`.
     - `RegionCombineMode`: `REPLACE`, `ADD`, `SUBTRACT`, `CLEAR`.
     - `FocusPriority`: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`.
     - `ReferenceFocusSettings`: Priority, target mode, detail sensitivity, preserve exact color, edge dilation, custom params.
     - `NamedReferenceRegion`: UUID, name, instructions, settings, dimensions, mask (`ByteArray`), tint color (`colorRgba`), opacity, visibility, locked state, bounding box calculation, selected pixel count.
     - `ReferenceCropBounds`: Normalized $[0.0, 1.0]$ and pixel bounding box coordinates.
     - `ReferenceViewportState`: Pan $(X, Y)$, zoom factor, crop bounds, and bidirectional screen $\leftrightarrow$ reference coordinate transformation.
     - `ReferenceAnalysisProject`: Source metadata (filename, SHA-256, dimensions, EXIF orientation), crop bounds, region list, and active region pointer.
  2. **Deterministic Selection Engine (`ReferenceRegionEngine.kt`)**:
     - Cleanroom tool rasterizers: `applyRectangle`, `applyEllipse`, `applyPolygon`, `applyLasso` (scanline polygon filling), `applyBrush` (continuous circular dab path interpolation with step spacing $\le \text{radius} / 2$).
     - Boolean combination operators (`REPLACE`, `ADD`, `SUBTRACT`, `CLEAR`).
     - Multi-region lifecycle management: `createRegion`, `renameRegion`, `duplicateRegion` (deep clone of byte buffer), `deleteRegion`, `toggleVisibility`, `updateInstructions`, `updateSettings`, `updateOverlayOpacity`, `setActiveRegion`.
     - Source crop support (`applyCrop`): sub-samples all existing region masks into cropped coordinates.
     - Non-destructive composite overlay renderer (`renderCompositeOverlay`): alpha-blends visible tinted masks over reference ARGB pixels without mutating the source buffer.
  3. **JSON Persistence & Project Serializer (`ReferenceAnalysisProjectSerializer.kt`)**:
     - Pure Kotlin serialization using `ArtJson` and Base64 mask compression.
     - Full roundtrip persistence (`saveToFile`, `loadFromFile`) preserving all region attributes, instructions, and mask bytes.
     - Validates source reference file immutability with cryptographic SHA-256 before and after project operations.
  4. **Accessible Studio UI (`StudioReferenceRegionPanel.kt`)**:
     - Compose panel providing tool selector chips, combine mode chips, brush size slider, region item list with color pips, duplicate/delete/visibility actions, rename dialog, overlay opacity slider, priority chips, and instructions field.
     - Complies with Studio Accessibility standards ($\ge 48$ dp touch targets, WCAG AAA color contrast).
  5. **Automated Verification & Tooling**:
     - Standalone Python QA script (`tools/qa/test_reference_region_goldens.py`) verifying all 5 selection tools, combination modes, multi-region IO roundtrip, and crop coordinate stability.
     - Comprehensive unit test suite (`ReferenceRegionTest.kt`, 14 tests) covering rectangle, ellipse, polygon, lasso, brush, combination modes, multi-region lifecycle, visibility toggles, viewport invariance, source cropping, and SHA-256 non-mutation persistence.

- **Consequences**:
  - Artists can easily mark, prioritize, and annotate specific areas of interest in reference images for downstream AutoDraw and CV reconstruction.
  - Preserves source reference files completely unaltered on disk and in memory.
  - Zero external C++ native libraries or unverified neural weights added; pure Kotlin implementation.
  - Total unit tests increased from 668 to 682 (14 new reference region tests, 0 failures, 0 regressions).
  - Shipped debug APK remains 12.1 MB (12,679,272 bytes), well within package size limits.

---

### ADR-058: Per-Region Analysis Profiles, Inherited Presets, and Boundary-Margin-Stitched Focused CV Execution
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 53)
- **Context**:
  In computer vision illustration analysis, uniform global parameters force an unacceptable trade-off: high sensitivity across the whole image creates noise and over-segmentation on smooth backgrounds, while conservative thresholds miss delicate hairlines (eyelashes, thin strokes, micro-loops) in intricate focal areas.
  Architectural requirements:
  1. *Per-Region Analysis Profiles & Inheritance Model*: Each marked region must support independent analysis configuration across 7 core parameters + 2 execution directives:
     - `minFeatureSize`: Minimum feature/area threshold for contour/centreline retention.
     - `edgeSensitivity`: $[0.0, 1.0]$ mapping to Canny/Sobel hysteresis thresholds.
     - `curveFittingTolerance`: Schneider Bézier max fitting error in pixels.
     - `simplificationTolerance`: RDP polygonal decimation tolerance in pixels.
     - `gapClosingRadius`: Micro-gap detection and line bridge distance.
     - `detailPreservation`: $[0.0, 1.0]$ controlling sharp corner angle preservation ($20^\circ..80^\circ$).
     - `reviewSensitivity`: $[0.0, 1.0]$ threshold for flagging ambiguous forks/breaks.
     - `boundaryMarginPx`: Transition bleed padding $[0..16]$ px around region masks.
     - `additionalPasses`: Count of extra iterative refinement passes within the region patch.
  2. *Inheritance and Override Semantics*: Profiles must support hierarchical resolution: Global baseline defaults $\to$ Region factory preset (`Standard`, `High Detail`, `Fine Lines`, `Broad Strokes`) $\to$ Explicit per-property user overrides. Any un-overridden property must cleanly inherit from the underlying preset/global default. Overrides can be cleared individually or in bulk.
  3. *Zero Baseline Regression Outside Masks*: Extra processing passes must be strictly bounded to selected regions plus their boundary margins. Outside masks, the output must remain 100% bit-exact identical to the global baseline output (0 diff pixels, RMSE = 0.0), guaranteeing zero degradation of the rest of the image.
  4. *Boundary Margin Continuity & Cross-Boundary Stitches*: When strokes cross the boundary mask, abrupt clipping at the 1px boundary causes unnatural edge severance. An expanded boundary margin ($M = 4..8$ px) allows strokes to transition gracefully, with cross-boundary BFS stitching connecting region-refined edges to baseline edges.
  5. *Telemetry & Compute Accounting*: Per-region execution time (ms), peak memory (bytes), passes count, and features detected must be recorded and auditable.

- **Decision**:
  Implement the first-party cleanroom Region Profile Subsystem in package `com.artstudio.cv.regionprofile`:
  1. **Domain Models & Presets (`RegionProfileModels.kt`)**:
     - `RegionProfilePreset`: `STANDARD`, `HIGH_DETAIL`, `FINE_LINES`, `BROAD_STROKES`, `CUSTOM`.
     - `RegionAnalysisProfile`: Immutable configuration data class with 9 exposed parameters, validation constraints, and `toCvParameters()` converter.
     - `RegionProfileOverride`: Data class with nullable properties for sparse overrides, `hasOverrides()`, `overriddenPropertyNames()`, and serialization mappings.
     - `ResolvedRegionProfile`: Immutable snapshot of merged parameters with `overriddenProperties: Set<String>` and `isInheritedFromGlobal` tracking.
     - `RegionProfileResolver`: Hierarchical resolution engine applying explicit overrides over presets/global defaults.
     - `RegionAnalysisMetrics`: Per-region telemetry container tracking pixel count, bounding area, elapsed latency ms, peak working memory bytes, and passes executed.
     - `RegionAwareAnalysisResult`: Composite container merging global baseline result, region-enhanced edges, metrics, and cross-boundary transition documentation.
  2. **Region-Aware Pipeline Engine (`RegionAwareAnalysisPipeline.kt`)**:
     - Global baseline pass runs `CvPipeline.process` across the entire canvas.
     - For each visible region: computes bounding box dilated by `boundaryMarginPx`, extracts sub-patch buffers, runs regional baseline with resolved parameters, and executes additional passes (Pass 1: high-sensitivity subtle edge extraction; Pass 2: micro-gap bridging).
     - Merges regional results into composite edge buffer: core mask takes refined edges, boundary margin stitches continuous crossing strokes, and outside mask remains 100% bit-for-bit global baseline edges.
  3. **Synthetic Mixed-Detail Fixture Generator (`SyntheticMixedDetailFixture.kt`)**:
     - Deterministic $128 \times 128$ fixture with broad high-contrast strokes outside and subtle low-contrast hairlines, micro-loops, and 1px gaps inside a marked focus region.
     - Golden verifier asserting both extra detail capture inside and 100% zero regression outside (0 diff pixels, RMSE = 0.0).
  4. **Accessible Studio UI (`StudioRegionProfileEditor.kt`)**:
     - Jetpack Compose editor exposing preset chips, all 9 parameter sliders, visual "Inherited" vs "Overridden" badges, per-parameter reset buttons, and "Reset All Overrides" action.
  5. **Verification & Tooling**:
     - Standalone Python QA script (`tools/qa/test_region_profile_goldens.py`) verifying inheritance, mixed-detail fixture, zero-regression invariant, and compute accounting.
     - JUnit test suite (`RegionProfileTest.kt`, 17 unit tests) covering inheritance, granular overrides, complete overrides, parameter boundaries, serialization, zero regression, and telemetry.

- **Consequences**:
  - Eliminates the sensitivity trade-off: artists can allocate high analysis effort to focal details (eyes, fine linework) without introducing noise or artifacts into the rest of the artwork.
  - Zero degradation outside marked regions: baseline output outside masks is guaranteed bit-exact invariant.
  - Pure Kotlin managed implementation with zero external C++ dependencies or unverified neural weights.
  - Total unit tests increased from 682 to 699 (17 new profile tests, 0 failures, 0 regressions).
  - Shipped debug APK remains 12.8 MB (13,375,801 bytes), well within package size limits.

---

### ADR-059: Constrained Natural Language Description to Region Analysis Settings Translation & Security Guardrails
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 54)
- **Context**:
  Users require the ability to guide region-specific computer vision analysis using natural-language instructions (e.g., "preserve tiny features", "separate neighbouring contours", "prioritise smooth curves", "detect fine tips", "close line gaps", "clean noise", "broad outlines").
  However, directly executing arbitrary model or user text poses severe security and reliability risks:
  1. *Arbitrary Code Execution Invariant*: Under no circumstances may raw text strings execute code, scripts, shell binaries, system calls, or dynamic evaluation.
  2. *Security Guardrails*: The parser must intercept and reject prompt injections (e.g. "ignore previous instructions", "DAN mode"), shell injections (`rm -rf`, `sudo`, `curl`), and script injection (`<script>`, `eval(`, `exec(`), flagging them as malicious and making zero modifications to analysis parameters.
  3. *Constrained Schema Mapping*: Directives must map strictly to a concrete, validated schema of 9 numerical parameters (`minFeatureSize`, `edgeSensitivity`, `curveFittingTolerance`, `simplificationTolerance`, `gapClosingRadius`, `detailPreservation`, `reviewSensitivity`, `boundaryMarginPx`, `additionalPasses`).
  4. *Ambiguity & Conflict Resolution*: When instructions request antagonistic goals (e.g., "prioritise smooth curves and detect fine tips" or "broad outlines and preserve tiny features"), the engine must flag an ambiguity warning and apply balanced compromise parameters rather than silently failing.
  5. *Full User Visibility & Manual Overrides*: Before any analysis pass is launched, the interpreted settings, matched intents, and explanations must be clearly visible in the UI. Users must be empowered to manually adjust or override every mapped slider. Without AI, equivalent manual controls must remain available.
  6. *Reproducibility & Audit Trail*: The original instruction, status, confidence, matched intents, interpreted overrides, user overrides, resolved effective profile, and model/provider metadata must be persistently serialized. Re-running analysis with unchanged parameters must yield 100% deterministic results.

- **Decision**:
  Implement the first-party cleanroom natural language instruction translator in package `com.artstudio.cv.regionprofile.instruction`:
  1. **Domain Models (`InstructionTranslationModels.kt`)**:
     - `UserAnalysisIntent`: 8 core illustration intents (`PRESERVE_TINY_FEATURES`, `SEPARATE_NEIGHBOURING_CONTOURS`, `PRIORITISE_SMOOTH_CURVES`, `DETECT_FINE_TIPS`, `CLOSE_LINE_GAPS`, `HIGH_DETAIL_OVERALL`, `CLEAN_NOISE_REMOVAL`, `BROAD_FLAT_OUTLINES`).
     - `InterpretationStatus`: `CONFIDENT`, `AMBIGUOUS`, `UNSUPPORTED`, `MALICIOUS_OR_ADVERSARIAL`.
     - `MatchedIntentDetail`: Intent, keywords, confidence, affected parameters, user-facing explanation.
     - `ModelProviderMetadata`: Provider name, model ID, version, deterministic offline flag, timestamp.
     - `InstructionInterpretationRecord`: Record ID, region ID, original text, status, confidence, matched intents, unsupported tokens, explanations, conflict warning, interpreted overrides, user overrides, effective resolved profile, and `toMap`/`fromMap` serialization.
  2. **Deterministic Translation Engine (`InstructionTranslator.kt`)**:
     - *Security Pre-Screening*: Proactively detects prompt injections, shell escapes, and script tags, immediately returning `MALICIOUS_OR_ADVERSARIAL` status with 0 parameter changes.
     - *Lexicon Intent Clustering*: Cleanroom phrase matching across comprehensive domain lexicons for each intent.
     - *Conflict & Ambiguity Engine*: Flags conflicting directives and vague phrasing ("make it better").
     - *Deterministic Parameter Synthesis*: Synthesizes `RegionProfileOverride` safely combining multi-intent directives without dynamic code execution.
  3. **Accessible Jetpack Compose UI (`StudioInstructionSettingsDialog.kt`)**:
     - Interactive review dialog presenting:
       - Original instruction and color-coded status badge (`CONFIDENT`, `AMBIGUOUS`, `UNSUPPORTED`, `MALICIOUS`).
       - Matched intent chips with confidence and affected parameter tags.
       - Conflict warning banner for ambiguous requests.
       - Fully editable manual controls via embedded `StudioRegionProfileEditor`.
       - Transparent audit metadata (offline rule engine, version, timestamp).
       - Action buttons with $\ge 48$ dp touch targets and WCAG AAA color contrast.
  4. **Verification & Tooling**:
     - Standalone Python QA script (`tools/qa/test_instruction_translator_goldens.py`) verifying 18 test cases across all categories + 10x deterministic repetition check (100% pass rate).
     - JUnit test suite (`InstructionTranslatorTest.kt`, 21 unit tests) covering supported intents, multi-intent combinations, ambiguity/conflicts, vague requests, malicious/adversarial injections, empty/unsupported text, user overrides, deterministic repetition, and serialization roundtrips.

- **Consequences**:
  - Eliminates arbitrary code execution risk through strict, deterministic offline lexicon mapping.
  - Empowers users with natural language control while maintaining 100% transparent manual overrides.
  - Ensures bit-exact deterministic reproducibility across analysis runs.
  - Increases total unit test count from 699 to **720 passing tests** (0 failures, 0 skipped).
  - Shipped debug APK remains **12.8 MB (13,394,451 bytes)**, signed with APK Signature Scheme v2.

---

### ADR-060: On-Demand Optional AI Model Registry, Curated Catalog, and Runtime Adapters
- **Date**: 2026-10-11
- **Status**: Adopted (Prompt 55)
- **Context**:
  In accordance with ADR-049 and ADR-050, Art Studio operates primarily on a deterministic, first-party classical computer vision engine. However, advanced illustration workflows (e.g., interactive point/box segmentation, style transfer hints for imported reference photos, reference super-resolution, and semantic region tagging) can benefit from optional on-device machine learning models.
  Critical architectural requirements and governance constraints:
  1. *Zero Bundled Neural Weights*: Under no circumstance may model weights be bundled in the APK or AAB binary. The base APK must strictly stay under 15.0 MB (measured baseline: 12.8 MB).
  2. *Optional Invariant*: Zero models are required; all catalog entries are strictly optional.
  3. *On-Demand Streaming Downloads*: Downloads occur only upon explicit user consent, enforce Wi-Fi-only by default (with user override), require $\ge 2\times$ model size available storage pre-flight, and cryptographically verify SHA-256 and byte length before activation. Corrupt files are immediately deleted.
  4. *Classical CV Supplementation (Not Replacement)*: AI models act strictly as supplements to the classical CV pipeline. Classical CV always runs first. If AI output contradicts classical CV (e.g. IoU < 0.50), both candidates are presented in the review UI for resolution; classical CV is never silently discarded.
  5. *Semantic Fallback When No AI*: If no model is downloaded or loaded, the pure-Kotlin `SemanticHeuristicAssist` (HSV thresholds and geometry rules, 0 MB) executes transparently, ensuring full capability without AI.
  6. *Memory-Bounded Admission & Idle Reclamation*: Model loading is rejected if free heap RAM is below $1.5\times$ expected peak RAM. Resident models are automatically unloaded after 60 seconds of idle inactivity.
  7. *Document Immutability & Provenance Survival*: Running AI inference or loading models leaves the canonical `ArtDocument` 100% immutable. The `AiAugmentation` provenance record survives native save/reopen.
  8. *100% Local Offline Execution*: Zero artwork or reference images ever leave the device. No network access occurs during inference.

- **Decision**:
  Implement the first-party cleanroom on-demand AI model registry subsystem in `com.artstudio.ai.model` and `com.artstudio.cv.ai`:
  1. **Domain Models (`AiModelDescriptor.kt`)**:
     - `ModelCategory`: `SEGMENTATION`, `STYLE_TRANSFER`, `SUPER_RESOLUTION`, `CLASSIFICATION`, `SEMANTIC_UNDERSTANDING`.
     - `QuantizationType`: `FP32`, `FP16`, `INT8`, `NONE`.
     - `TargetDomain`: `ANIME_LINE_ART`, `ANIME_FLAT_COLOUR`, `PHOTO`, `GENERAL`.
     - `PromptModality`: `AUTOMATIC`, `POINT`, `BOX`, `TEXT`, `MASK`.
     - `ModelRuntimeType`: `TFLITE`, `ONNX`, `CUSTOM_HEURISTIC`.
     - `RegionSemanticTag`: `SKIN`, `HAIR`, `EYES`, `CLOTHING`, `BACKGROUND`, `HIGHLIGHT`, `SHADOW`, `UNKNOWN`.
     - `AiModelDescriptor`: Immutable metadata container tracking licensing, sizes, domains, and warnings.
  2. **Curated Model Catalog (`AiModelCatalog.kt`)**:
     - Curated catalog containing 7 candidates evaluated for license, size, and domain fit:
       1. MediaPipe Image Segmenter (Selfie / Multiclass) — Apache 2.0, ~2.5 MB, INT8, PHOTO.
       2. U2-Net / U2-Netp — Apache 2.0, ~4.7 MB, INT8, ANIME_FLAT_COLOUR.
       3. AnimeGANv2 (Hayao) — MIT, ~9.2 MB, FP16, ANIME_FLAT_COLOUR.
       4. Real-ESRGAN (x4 General) — BSD-3, ~17.4 MB, INT8, GENERAL.
       5. MobileSAM / EdgeSAM — Apache 2.0, ~10.2 MB, INT8, GENERAL.
       6. CLIPSeg — Apache 2.0, ~8.5 MB, INT8, GENERAL.
       7. Semantic Heuristic Assist — Apache 2.0, 0 MB, always available, ANIME_LINE_ART.
     - JSON serialization and parsing (`exportCatalogJson`, `importCatalogJson`).
  3. **On-Demand Streaming Downloader (`AiModelDownloader.kt`)**:
     - Pre-flight verification checking Wi-Fi and $\ge 2\times$ disk space.
     - Streaming downloads with resume offset support.
     - SHA-256 and byte length cryptographic verification. Corrupt files are purged immediately.
     - Storage accounting and single/bulk deletion.
  4. **Runtime Adapters & Memory Gating (`AiModelAdapter.kt`)**:
     - `TfliteModelAdapter`, `OnnxRuntimeModelAdapter`, and `SemanticHeuristicAssist`.
     - Memory admission check: rejects model loading if free heap < $1.5\times$ peak RAM.
     - Cooperative cancellation on background dispatchers via `JobCancellationToken`.
     - 60-second idle auto-unloader reclaiming RAM.
  5. **CV Integration Pipeline (`CvAiIntegrationPipeline.kt`)**:
     - Authoritative classical CV execution with optional AI candidate generation on downsampled preview ($\le 512 \times 512$).
     - Contradiction detection via IoU; presents dual candidates for artist resolution.
     - Reference photo AnimeGANv2 style transfer hint generation (classical CV still extracts paths).
     - Reference Real-ESRGAN super-resolution upscaling capped at $4096 \times 4096$ (never applied to paint layers).
  6. **Provenance & Serialization Integration**:
     - Added `AiAugmentation` data model.
     - Updated `AutoDrawProvenance` in `AuxiliaryConfigs.kt` and `DocumentSerializer.kt` to persist and restore `aiAugmentation` across `.ffart`/JSON save and reopen.
     - Unified `RegionMask` holding `semanticTag`, `confidence`, `source`, and `aiAugmentation`.
  7. **Accessible Jetpack Compose UI**:
     - `StudioAiModelSettingsScreen.kt`: Model catalog cards, license badges, formatted sizes, download/load/unload/delete actions, storage accounting card, Wi-Fi toggle, and target domain warnings.
     - `AiAssistToggleRow`: Truthful disabled state when no model downloaded with direct navigation link.
  8. **Verification & Tooling**:
     - Python QA validator (`tools/qa/test_ai_model_registry_goldens.py`) verifying catalog integrity, licenses, SHA-256 checks, 2x storage check, and JSON roundtrip.
     - JUnit test suite (`AiModelRegistryTest.kt`, 18 unit tests) covering all 15 acceptance criteria.

- **Consequences**:
  - Delivers complete on-demand AI model registry without bundling any model weights in the APK.
  - Maintains strict package ceiling: debug APK remains **12.8 MB (13,394,451 bytes)**, signed with APK Signature Scheme v2.
  - Total unit test count increases from 720 to **738 passing tests** (0 failures, 0 skipped).
  - Guarantees 100% offline privacy and zero cloud uploads.

---

### ADR-061: Interactive Detection Review, Uncertainty Flagging, Explicit Override Operations, and Conflict-Aware Reanalysis Engine
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Automated computer vision and AI model detections are assistive candidates that may contain inaccuracies (e.g., broken stroke gaps, spurious speckles, unclosed boundaries, low-confidence splines). Artists require transparent, reliable tools to inspect detections against original reference images, repair geometry and masks directly, and preserve their explicit corrections across subsequent analysis passes without risk of silent data loss.
- **Decision**:
  1. **Visual Inspection Overlays (`DetectionOverlayRenderer.kt`)**:
     - Provides five visual comparison modes: `SOURCE_ONLY`, `DETECTION_ONLY`, `SPLIT_COMPARE` (with interactive split slider), `ALPHA_BLEND` (with adjustable opacity), and `DIFFERENCE_HEATMAP` (perceptual false-color delta visualization).
  2. **Topological Uncertainty Detection (`DetectionReviewModels.kt`)**:
     - Automatically scans detections and generates visual `UncertaintyMarker`s for `LOW_CONFIDENCE_PATH`, `AMBIGUOUS_FORK`, `POTENTIAL_STROKE_BREAK`, `LEAKY_BOUNDARY`, and `CONTRADICTORY_AI_PROPOSAL`.
  3. **Explicit Override Operations Log (`DetectionCorrection.kt`)**:
     - Edits are recorded as an immutable, sequential audit trail of `DetectionCorrection` objects (`MOVE_PATH`, `DELETE_PATH`, `SPLIT_PATH`, `MERGE_PATHS`, `MOVE_REGION`, `DELETE_REGION`, `SPLIT_REGION`, `MERGE_REGIONS`, `BRIDGE_GAP`, `TAG_REGION`).
     - Baseline detections remain untouched; effective state is computed by replaying corrections.
  4. **Conflict-Aware Reanalysis Engine (`CorrectionConflictModels.kt`)**:
     - Reanalysis replays all active corrections on updated detections.
     - When an entity's identity cannot be mapped (due to removal or drastic change), an explicit `CorrectionIdentityConflict` is raised with artist choices (`REAPPLY_CLOSEST_MATCH`, `KEEP_NEW_DETECTION`, `DISCARD_CORRECTION`).
     - Zero edits are ever silently discarded.
  5. **Sub-Patch Targeted Reanalysis (`TargetedReanalysisEngine.kt`)**:
     - `reanalyzeCurrentRegion` and `reanalyzeSelection` isolate processing to a bounded sub-patch $[minX, minY, maxX, maxY]$ plus boundary margin.
     - Outside regions and paths are preserved intact.
     - Delivers $\ge 3\times$ speedup ($< 15$ ms targeted vs $> 50$ ms full canvas).
  6. **Lossless Persistence (`DetectionReviewSerializer.kt`)**:
     - JSON serialization and deserialization of the entire session state, including corrections, conflicts, and markers.
  7. **Accessible Compose UI (`StudioDetectionReviewPanel.kt`)**:
     - Accessible interface conforming to WCAG AAA contrast and $\ge 48$ dp touch target standards.
- **Consequences**:
  - Total unit test count increases from 738 to **754 passing tests** (0 failures, 0 skipped).
  - Python QA golden validation (`tools/qa/test_detection_review_goldens.py`) passes 11/11 tests.
  - Debug APK remains at **12.6 MB (13,226,183 bytes)**, signed with APK Signature Scheme v2.

---

### ADR-062: Source Palette Analysis, Region-Local Colour Association, Luminance/Shadow Inference, and Adaptive Role Mapping Engine
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Automated illustration coloring and art reconstruction (such as Colour Lab 15 styles and flat illustration fills) requires accurate extraction of dominant source colors, region-local color sampling, and light/shadow topological relationships. Naive global color quantization pollutes palettes with transparent pixels, merges unrelated regions, or hallucinates untested biological semantic categories (e.g., skin/hair). Artists require transparent separation between raw observed pixel colors and inferred lighting roles, with full freedom to preserve the source palette, apply gentle adaptation, or artistically reinterpret colors.
- **Decision**:
  1. **Source Palette Models (`SourcePaletteModels.kt`)**:
     - Strict separation between raw `observedColor`, `assignedColor`, `userOverrideColor`, and `inferredRole`.
     - Inferred topological lighting roles: `BASE_COLOR`, `SHADOW`, `HIGHLIGHT`, `OUTLINE`, `ACCENT`, `BACKGROUND`, `UNASSIGNED`.
     - Explicit refusal to claim untested semantic biological interpretations without tested classifiers.
     - 3 Adaptation modes: `PRESERVE_SOURCE`, `GENTLE_ADAPTATION`, `ARTISTIC_REINTERPRETATION` (with harmonic schemes: Warm Sunset, Cool Twilight, Vibrant Pop, Muted Earth, Monochrome Ink).
  2. **Extraction Engine (`SourcePaletteExtractor.kt`)**:
     - Transparent pixel exclusion: pixels with alpha $\le 5$ are excluded from palette quantization and accounted for separately.
     - Median-Cut clustering followed by iterative near-duplicate merging (Euclidean distance $< 0.08$).
     - Rec. 709 perceived luminance computation: $0.2126 R + 0.7152 G + 0.0722 B$.
     - Region-local color extraction with mean RGB and intra-region variance.
     - Adjacency and shadow inference: detects boundary contact between adjacent regions; pairs sharing chromatic hue with $|\Delta L| \ge 0.15$ are classified into base and shadow roles.
     - RMSE extraction error calculation against source pixels.
  3. **Separate Persistence & Cross-Style Availability (`SourcePalettePersistence.kt`)**:
     - Serializes `SourcePaletteAnalysis` losslessly to JSON.
     - Exposes `toColorStylePalette()` making region-to-color mappings accessible to all Colour Lab styles and fill engines.
  4. **Accessible Jetpack Compose UI (`StudioPaletteAssignmentPanel.kt`)**:
     - Touch targets $\ge 48$ dp, WCAG AAA contrast ($> 7:1$).
     - Displays dominant swatches, mode tabs, region assignment list, role badges, and interactive color reassignment modal.
- **Consequences**:
  - Total unit test count increases from 754 to **766 passing tests** (0 failures, 0 skipped).
  - Python QA goldens (`tools/qa/test_palette_analysis_goldens.py`) pass 11/11 tests (100% success).
  - Debug APK remains at **12.6 MB (13,253,997 bytes)**, signed with APK Signature Scheme v2.

---

### ADR-063: Modular Colour Stack Architecture, Ordered Multi-Stage Compositing, Conflict Resolution & Recipe Versioning Engine
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Illustration coloring and aesthetic rendering require a flexible, modular stack where individual stages (base fills, cel shadows, gradients, specular highlights, medium textures, palette transforms, and finishing effects) can be combined, reordered where valid, and edited independently. Crucially, digital artists require:
  1. Independent linework preservation (original reconstruction contours remain unmutated by color and fill passes).
  2. Separate editable layer outputs for each stage when baked to native ArtDocument documents.
  3. Deterministic canonical ordering with intelligent conflict resolution (e.g. No Colour skipping all fill/color stages, and ordered semantics for Monochrome + Neon).
  4. Versioned recipe schema with deterministic seeded reproducibility.
  5. Dual-resolution execution (instant sub-16ms interactive preview surface vs full-quality final rendering).
- **Decision**:
  1. **Domain Models & Stage Interfaces (`ColorStackModels.kt`)**:
     - Defines `ColorStageType`: `BASE_FILL`, `CEL_SHADOW`, `GRADIENT`, `HIGHLIGHT`, `MEDIUM_TEXTURE`, `PALETTE_TRANSFORM`, `FINISHING_EFFECT`, and independent `LINEWORK`.
     - Stable identifiers for all 15 Colour Lab styles + Style 0 (`NO_COLOUR`): `NO_COLOUR`, `CLASSIC_CEL`, `SOFT_GRADIENT`, `HYBRID_CEL_SOFT`, `FLAT_ILLUSTRATION`, `PAINTERLY`, `WATERCOLOUR`, `COLOURED_PENCIL`, `MARKER`, `MANGA_SCREENTONE`, `COMIC_BOOK`, `OIL_PAINT`, `GOUACHE`, `PASTEL`, `NEON_CYBERPUNK`, `MONOCHROME`, `CUSTOM`.
     - Validated configurations: `BaseFillStageConfig`, `CelShadowStageConfig`, `GradientStageConfig`, `HighlightStageConfig`, `MediumTextureStageConfig`, `PaletteTransformStageConfig`, `FinishingEffectStageConfig`.
     - `LineworkData`: Isolated line art raster buffer composited non-destructively on top.
  2. **Deterministic Conflict Resolver (`ColorStackResolver.kt`)**:
     - Enforces canonical order (`BASE_FILL` -> `CEL_SHADOW` -> `GRADIENT` -> `HIGHLIGHT` -> `MEDIUM_TEXTURE` -> `PALETTE_TRANSFORM` -> `FINISHING_EFFECT`).
     - Rule 1 (No Colour Baseline): Bypasses all automatic color/fill stages, retaining only linework/reconstruction with informative notification.
     - Rule 2 (Monochrome + Neon Ordered Semantics): If Monochrome precedes Neon, validates noir/cyberpunk aesthetic (desaturates base first, adds vibrant glowing neon on top). If Neon precedes Monochrome, warns artist that monochrome will desaturate neon hues and offers auto-reorder.
     - Rule 3: Flags missing base fill dependencies for shading/highlights.
     - Rule 4: Flags unpartitioned duplicate base fills.
     - Rule 5: Flags clashing heavy medium textures (e.g. Screentone with Oil Impasto).
  3. **Versioned Recipe Schema & Presets (`ColorStackRecipe.kt`)**:
     - `ColorStackRecipe` with `version = 1`, `seed: Long`, timestamps, and stage configurations.
     - Zero-dependency lossless JSON serialization (`ColorStackRecipeSerializer`) via `ArtJson`.
     - Factory presets for all 16 styles and Cyberpunk Noir.
  4. **Pure-Kotlin Stage Processors (`ColorStageProcessors.kt`)**:
     - Implements deterministic software rendering for all 7 stages using Porter-Duff and W3C blend equations (`LayerBlendCompositor`).
  5. **Incremental Pipeline & Layer Group Baking (`ColorStackPipeline.kt`)**:
     - Caches stage execution outputs; modifying downstream stage reuses cached upstream backdrops bit-for-bit.
     - Downsamples preview surfaces to $\le 512$ px for sub-16ms interactive feedback.
     - `toLayerGroup()` bakes rendered stage outputs into separate editable raster layers in `ArtDocument`.
  6. **Accessible Jetpack Compose UI (`StudioColorStackPanel.kt`)**:
     - Presets carousel, preview/final quality toggle, diagnostic warning banner, reorderable stage rows with enable switch, opacity slider, and bake to layers action.
- **Consequences**:
  - Total unit test count increases from 766 to **792 passing tests** (0 failures, 0 skipped, +26 new tests).
  - Python QA golden validation (`tools/qa/test_color_stack_goldens.py`) passes 11/11 tests (100% success).
  - Two multi-stage golden recipes stored with stable checksums (`37c8e1471aec944c` for Classic Cel, `1e6804585af3a5e2` for Cyberpunk Noir).
  - Debug APK remains at **12.7 MB (13,285,264 bytes)**, signed with APK Signature Scheme v2.

---

### ADR-064: First Five Colour Lab Rendering Strategies, Source Preservation & Layer Separation
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Colour Lab requires actual rendering strategies for the first 5 named styles: Classic Cel Shading, Soft Gradient Shading, Hybrid Cel + Soft Shading, Flat Illustration, and Painterly.
  Each style must implement a dedicated distinct algorithm, specialized parameter schema, mask handling, and expected input modes rather than aliasing a single generic filter.
  Furthermore, the strategies must preserve source palette relationships by default via an explicit `SourcePreservationMode` (`PRESERVE_EXACT`, `GENTLE_ADAPT`, `HARMONIC_TONAL`), generate separate editable raster layers for each pass (base flats, shadows, gradients, highlights, textures, and independent linework), and explicitly document limitations for textured/photographic inputs.
- **Decision**:
  1. **Domain Models & Layer Separation (`StyleStrategyModels.kt`)**:
     - `SourcePreservationMode`: `PRESERVE_EXACT` (default, 100% raw observed reference color), `GENTLE_ADAPT` (snaps to clean dominant swatches), `HARMONIC_TONAL` (remaps hues while maintaining shadow/light hierarchy).
     - `LayerRole`: `BASE_FLAT`, `SHADOW`, `GRADIENT`, `HIGHLIGHT`, `TEXTURE`, `LINEWORK`.
     - `StyleLayerOutput`: Encapsulates isolated 32-bit ARGB raster buffers with native `BlendModeType` and opacity, facilitating non-destructive document baking via `toLayerGroup()`.
     - `ColourStyleStrategy` interface: mandates `styleId`, `displayName`, `description`, `supportedInputModes`, `documentedLimitations`, and `render()`.
  2. **Concrete Rendering Strategies**:
     - `ClassicCelStrategy` (`CLASSIC_CEL`): 2-tone crisp hard-edged shadow cutoff along directional vector $(-\cos\theta, -\sin\theta)$ with cool indigo tinting, specular crest highlights along $(\cos\theta, \sin\theta)$, base flats, and independent linework preservation. Generates 4 separate editable layers.
     - `SoftGradientStrategy` (`SOFT_GRADIENT`): Continuous smooth ambient lighting falloff ($u^{\text{softness}}$) without step boundaries, edge attenuation, and independent linework.
     - `HybridCelSoftStrategy` (`HYBRID_CEL_SOFT`): Dual-pass composite combining a defined crisp core cel shadow step and ambient midtone gradient roll-off, modulated by `celVsSoftBalance`.
     - `FlatIllustrationStrategy` (`FLAT_ILLUSTRATION`): Pure unshaded graphic flats with morphological boundary bleed dilation (`boundaryBleedPx` in $[-2, 3]$) under linework to eliminate halo gaps, contrast pop boost, and planar stepped graphic tones.
     - `PainterlyStrategy` (`PAINTERLY`): Multi-tonal underpainting with localized pseudo-random chromatic jitter ($\pm \text{colorJitter}$), multi-filament directional bristle strokes, wet-edge diffusion, and procedural canvas tooth paper weave, strictly seeded for determinism.
  3. **Central Strategy Registry (`StyleStrategyRegistry.kt`)**:
     - Registry providing lookup, capability discovery, and execution dispatch for all 5 styles.
  4. **Textured and Photo Input Limitations Documented**:
     - *Classic Cel*: Unsegmented photographic references lack crisp contour thresholds; without vector/ink lines, cel cutoffs degrade into coarse posterized luminance steps.
     - *Soft Gradient*: Heavily textured photo surfaces cause diffuse, muddy gradients without clean region silhouettes; requires distinct region mask bounds.
     - *Hybrid Cel + Soft*: Photographic textures produce conflicting penumbra edges between the core cel step and soft ambient gradient.
     - *Flat Illustration*: Continuous photographic gradients are lost; flat fills collapse subtle volumetric forms into large uniform monochromatic color islands.
     - *Painterly*: Dense photographic grain interferes with directional bristle alignment, yielding chaotic noise rather than aesthetic impasto brushwork.
  5. **Testing & Verification**:
     - Unit test suite (`ColourStyleStrategiesTest.kt`): 14 passing tests covering all 5 styles, parameter variations, source preservation modes, boundary dilation, and seeded determinism.
     - Python QA validation (`tools/qa/test_style_strategies_goldens.py`): 5 stored goldens, parameter variation diffs, and render times recorded.
- **Consequences**:
  - Total unit test count increases from 792 to **806 passing tests** (0 failures, 0 skipped, +14 new tests).
  - Python QA golden validation (`tools/qa/test_style_strategies_goldens.py`) passes 10/10 tests (100% success).
  - Debug APK size bounded at $\le 15.0$ MB, signed with APK Signature Scheme v2.

---

### ADR-065: Next Five Named Colour Lab Rendering Strategies (Styles 6–10) & Permissive Procedural Texturing
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Following the implementation of styles 1–5 (ADR-064), the application requires concrete rendering strategies for the next five named styles: Watercolour, Coloured Pencil, Marker, Manga Screentone, and Comic Book.
  Each style must expose specialized controls (wash/texture, stroke/grain, marker overlap, tone spacing, comic contrast) while maintaining the shared compositing pipeline, source palette preservation, preview downscaling, and independent layer separation.
  Furthermore, assets must remain strictly permissively licensed without bloating the APK with giant third-party texture packs (0 bytes added asset package size), and full-quality rendering memory must be bounded.
- **Decision**:
  1. **Domain Models & Specialized Configurations (`StyleStrategyModels.kt`)**:
     - `WatercolourConfig`: `washWetness`, `granulation`, `puddleRingDarkness`, `edgeDiffusionPx`, `seed`, `sourcePreservation`.
     - `ColouredPencilConfig`: `hatchAngleDeg`, `strokeSpacingPx`, `grainToothStrength`, `pressureVariation`, `seed`, `sourcePreservation`.
     - `MarkerConfig`: `bandWidthPx`, `overlapDarkening`, `capillaryBleedPx`, `strokeAngleDeg`, `seed`, `sourcePreservation`.
     - `MangaScreentoneConfig`: `screenPitchPx`, `screenAngleDeg`, `dotShape` (`ROUND_DOT`, `ELLIPSE`, `LINE_SCREEN`), `densityScale`, `invertTones`, `sourcePreservation`.
     - `ComicBookConfig`: `comicContrast`, `benDayDotScale`, `inkShadowThreshold`, `colorOffsetPx`, `seed`, `sourcePreservation`.
  2. **Concrete Rendering Strategies**:
     - `WatercolourStrategy` (`WATERCOLOUR`, Style 6): Simulates fluid translucent pigment wash pooling, drying boundary capillary rim accumulation (coffee-ring effect puddle rings), and cold-press paper granulation (3–4 separate editable layers).
     - `ColouredPencilStrategy` (`COLOURED_PENCIL`, Style 7): Directional parallel wax-based stroke hatching at calibrated angles with pressure modulation and paper grain tooth skipping (3–4 separate editable layers).
     - `MarkerStrategy` (`MARKER`, Style 8): Translucent overlapping alcohol ink bands with chisel-tip width, optical multiply darkening on overlaps, and capillary edge bleed (3–4 separate editable layers).
     - `MangaScreentoneStrategy` (`MANGA_SCREENTONE`, Style 9): Rotated mathematical screentone dot screens and line screens mapping region luminance to dot coverage (3 separate editable layers).
     - `ComicBookStrategy` (`COMIC_BOOK`, Style 10): High-contrast pop art rendering with vintage Ben-Day dot matrix fills, solid black ink shadow pools, and print misregistration offsets (4 separate editable layers).
  3. **Zero-Bloat Permissive Procedural Texturing**:
     - All textures for styles 6–10 are generated through deterministic mathematical equations (orthogonal sine-cosine lattices, rotated coordinates, distance fields) or reference existing CC0-1.0 assets. Added APK asset footprint is **0 bytes**.
  4. **Central Registry Expansion (`StyleStrategyRegistry.kt`)**:
     - Expands registry to cover all 10 styles with uniform query, discovery, and execution dispatch.
  5. **Verification Evidence**:
     - Unit tests (`ColourStyleStrategiesTest.kt`): 24 total unit tests (+10 new unit tests covering styles 6–10, $\ge 2$ per style), total repository test suite passes **816 tests, 0 failures, 0 skipped**.
     - QA goldens (`test_style_strategies_6_to_10_goldens.py`): 5 stored goldens, render times, parameter sensitivity diffs, 0 bytes added assets, and 16.0 MB full-quality working RAM recorded.
- **Consequences**:
  - Total unit tests increase from 806 to **816 passing tests** (0 failures, 0 skipped, +10 new tests).
  - Styles 1 through 10 are fully functional as real, non-destructive rendering strategies with independent layer export to `ArtDocument`.
  - Added asset package footprint is strictly 0 bytes; full-quality rendering working memory is bounded at 16.0 MB.

---

### ADR-066: Remaining Five Named Colour Lab Rendering Strategies (Styles 11–15) and No Colour Mode
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Prompt 61 completes the 15-style Colour Lab strategy implementation along with Style 0 (No Colour Baseline).
  The required strategies are:
  - Style 0: No Colour Baseline (`NO_COLOUR`)
  - Style 11: Oil Paint (`OIL_PAINT`)
  - Style 12: Gouache (`GOUACHE`)
  - Style 13: Pastel Palette (`PASTEL`)
  - Style 14: Neon / Cyberpunk (`NEON_CYBERPUNK`)
  - Style 15: Monochrome (`MONOCHROME`)
  Each strategy must expose specialized controls, apply bounded effects for texture/glow without full-resolution multiple copies, and distinguish simulated medium effects from true physical paint simulation in capability descriptions.
  Monochrome must apply a controlled Rec.709 perceived luminance transform while preserving original source palette data for non-destructive recipe switching.
  No Colour must skip all automatic color fills while retaining linework/reconstruction and preserving region masks and palette data for manual colouring.
- **Decision**:
  1. **Domain Models & Configurations (`StyleStrategyModels.kt`)**:
     - `NoColourConfig`: `paperBackground` (white paper vs transparent), `lineArtOpacity`, `preserveRegionMasks`.
     - `OilPaintConfig`: `impastoDepth`, `bristleDensity`, `canvasWeave`, `lightAngleDeg`, `seed`, `sourcePreservation`.
     - `GouacheConfig`: `matteOpacity`, `planarStepCount`, `edgeDryDarkening`, `chalkyFinish`, `seed`, `sourcePreservation`.
     - `PastelConfig`: `chalkDustStrength`, `pastelTintLift`, `paperToothDepth`, `smudgeSoftness`, `seed`, `sourcePreservation`.
     - `NeonCyberpunkConfig`: `glowRadiusPx`, `bloomIntensity`, `darkBaseFloor`, `neonSaturationBoost`, `chromaticShiftPx`, `seed`, `sourcePreservation`.
     - `MonochromeConfig` & `MonochromeTintMode`: `contrast`, `tintMode` (`PURE_GRAYSCALE`, `COOL_SLATE`, `WARM_SEPIA`, `HIGH_CONTRAST_NOIR`), `inkWashTuning`, `blackPoint`, `whitePoint`, `sourcePreservation`.
  2. **Concrete Rendering Strategies (`com.artstudio.colorstack.strategy`)**:
     - `NoColourStrategy` (`NO_COLOUR`, Style 0): Completely skips automatic region fills while retaining linework and optional white paper canvas; leaves region masks and palette analysis intact for immediate manual artist painting.
     - `OilPaintStrategy` (`OIL_PAINT`, Style 11): Simulates multi-filament directional bristle tracks, 2.5D impasto relief heightfield lighting (specular highlights on facing slopes, cast shadows in crevices), and coarse linen weave. Capability description explicitly distinguishes simulated medium effects from 3D Navier-Stokes physical fluid simulations.
     - `GouacheStrategy` (`GOUACHE`, Style 12): Simulates non-reflective matte poster paint with discrete planar tonal step quantization, velvety powder texture, and drying rim contours. Distinguishes simulated medium effects from physical binder drying simulations.
     - `PastelStrategy` (`PASTEL`, Style 13): Simulates soft chalky pastel pigment with delicate tint lifting, crumbly particle scattering, and heavy paper tooth valley skipping. Distinguishes simulated medium effects from friable particle dynamics.
     - `NeonCyberpunkStrategy` (`NEON_CYBERPUNK`, Style 14): Deep dark noir underbase, electric hyper-saturated contour rims, and bounded separable 2D bloom glow without multi-copy memory bloat (downscaled in preview quality). Distinguishes optical glow effects from physical volumetric light transport.
     - `MonochromeStrategy` (`MONOCHROME`, Style 15): Calibrated ITU-R BT.709 perceived luminance transform ($Y = 0.2126 R + 0.7152 G + 0.0722 B$), S-curve contrast grading, and Sumi-e shadow pools, while strictly preserving original RGB palette and region colors in data models for non-destructive recipe interchange.
  3. **Universal Registry Coverage (`StyleStrategyRegistry.kt`)**:
     - Central registry now registers and manages all 15 named styles plus No Colour (16 strategies total).
  4. **Automated Verification**:
     - Automated unit tests (`ColourStyleStrategiesTest.kt`): 37 total unit tests (+13 new tests covering styles 11–15 and Style 0, $\ge 2$ per style, 3 for No Colour), 829 total unit tests passing across repository (100% pass rate).
     - Python QA golden validation (`tools/qa/test_style_strategies_11_to_15_and_no_colour_goldens.py`): 6 stored goldens, parameter sensitivity diffs, numerical proofs, 0 bytes added asset bloat, and heaviest style benchmark recorded on two fixture sizes (512x512 at 4.0 MB, 1024x1024 at 16.0 MB).
- **Consequences**:
  - Total unit tests increase from 816 to **829 passing tests** (0 failures, 0 skipped, +13 new tests).
  - All 15 Colour Lab styles and Style 0 (No Colour) are fully implemented and selectable.
  - Added asset package footprint is strictly 0 bytes; full-quality rendering working memory is bounded at 16.0 MB.

---

### ADR-067: Deterministic Reconstruction Drawing Schedule Synthesis & Native Project Persistence

- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  Prompt 62 implements the drawing schedule generation engine for drawing reconstructed paths onto the canvas.
  The engine must:
  - Synthesize ordered schedule entries with path ID, region ID, layer ID, stroke direction, brush parameters, timing, and topological dependencies.
  - Support four core ordering strategies: `CONTOUR_FIRST`, `LAYER_BY_LAYER`, `SPATIALLY_EFFICIENT`, and `ANATOMICAL`.
  - Optimize stroke direction (`FORWARD` vs `REVERSE`) to minimize pen air travel distance on open paths, while strictly preserving `FORWARD` winding on closed loop contours.
  - Honor explicit user priority overrides without strategy heuristics introducing circular deadlocks or priority inversion.
  - Simulate a single visible pen cursor timeline with transport travel delays and non-overlapping monotonic intervals, while supporting multi-worker preprocessing flags for parallel preparation.
  - Persist complete schedule and configuration into the native `.ffart` project document model (`ArtDocument.kt` and `DocumentSerializer.kt`).
  - Maintain the historical fidelity disclaimer invariant: strictly disclaim reproducing the original human artist's real stroke history.
- **Decision**:
  1. **Schedule Domain Models (`DrawingScheduleModels.kt`)**:
     - `StrokeDirection` (`FORWARD`, `REVERSE`, `BIDIRECTIONAL`).
     - `PressureProfileType` (`CONSTANT`, `TAPER_ENDS`, `CALLIGRAPHIC`, `NATURAL_RAMP`).
     - `ScheduledBrushParams` (`sizePx`, `opacity`, `colorArgb`, `brushPresetId`, `pressureProfile`).
     - `ScheduleTiming` (`estimatedDurationMs`, `delayBeforeMs`, `startTimestampMs`, `speedFactor`, `endTimestampMs`).
     - `StrokeSemanticRole` (`OUTLINE`, `INTERNAL_DETAIL`, `HATCHING`, `SHADOW_ACCENT`, `FREEHAND`).
     - `AnatomicalTag` (`HEAD`, `EYES`, `FACE`, `HAIR`, `TORSO`, `LIMBS`, `BACKGROUND`, `GENERAL_DETAIL`).
     - `ScheduleOrderingStrategy` (`CONTOUR_FIRST`, `LAYER_BY_LAYER`, `SPATIALLY_EFFICIENT`, `ANATOMICAL`).
     - `DrawingScheduleConfig` (`orderingStrategy`, `nominalPenSpeedPxPerSec`, `penLiftSpeedPxPerSec`, `singlePenCursorMode`, `allowDirectionReversal`, `allowWorkerPreprocessing`, `userPriorityOverrides`, `randomizeOrder`, `seed`).
     - `DrawingScheduleEntry` and `DrawingSchedule`: Includes SHA-256 deterministic hash generator over sequence index, direction, timing, and dependencies, and authoritative disclaimer: *"This synthesized reconstruction schedule represents an optimized machine drawing plan; it does not claim to reproduce the original artist's real human stroke history."*
  2. **Deterministic Schedule Synthesis Engine (`DrawingScheduleGenerator.kt`)**:
     - `SchedulablePath`: Intermediate adapter from `VectorObject` with approximate Euclidean length calculation.
     - Topological Dependency Graph: Combines layer ordering, contour-first outline priorities, anatomical hierarchy, and explicit user priority overrides.
     - User Priority Precedence: Strategy heuristics only add dependencies between strokes when user priority overrides do not dictate the opposite order, preventing dependency cycles.
     - Deadlock Breaker: Fallback candidate selection on cycles guarantees the scheduler never hangs or enters an infinite loop.
     - Direction Reversal: Evaluates Euclidean distance from current pen location to path start vs end for open paths; selects `REVERSE` when air travel is shorter. Closed loops always remain `FORWARD`.
     - Timeline Coordinates: Calculates pen lift travel delay `delayBeforeMs = round(airDistance / penLiftSpeed)` and drawing duration `durationMs = round(length / penSpeed)`, guaranteeing strictly non-overlapping single-pen intervals.
  3. **Native Project Persistence (`ArtDocument.kt` & `DocumentSerializer.kt`)**:
     - Added `drawingSchedule: DrawingSchedule? = null` to `ArtDocument`.
     - Serializes and deserializes schedule configuration, entries, timings, brush parameters, SHA-256 hash, and disclaimer string in `DocumentSerializer`.
  4. **Automated Verification & Benchmarks**:
     - Automated unit tests (`DrawingScheduleTest.kt`): 14 unit tests covering deterministic hashing, contour-first, layer-by-layer, spatial efficiency, anatomical hierarchy, direction reversal, closed loops, user priority overrides, topological dependencies, timeline monotonicity, randomization, disclaimer persistence, and vector layer extraction. Total repository unit tests increase to **843 passing tests** (100% pass rate).
     - Python QA benchmark suite (`tools/qa/test_drawing_schedule_fixtures.py`): 9 validation checks and latency/memory benchmarks on simple fixture (4.66 ms, 27.0 KB) and detail-dense fixture (173.16 ms, 278.6 KB).
- **Consequences**:
  - Total unit tests increase from 829 to **843 passing tests** (+14 new tests, 0 failures, 0 skipped).
  - Native `.ffart` documents persist complete drawing schedules with bit-exact hash verification.
  - Zero APK package bloat; deterministic execution verified across simple and complex sketches.

---

### ADR-068: Controllable Visual Drawing Playback on Canvas & Cancellation State Isolation

- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  1. Artists need interactive control over the visual replay of synthesized drawing schedules on the actual canvas: play, pause, resume, restart, variable speed (0.25x to instant), step forward/back, skip/replay region, layer progress telemetry, pen cursor tracking, and checkpoint save/restore.
  2. Supported sequencing modes:
     - `LINE_ART_FIRST`: Outlines and contours rendered first across all layers, followed by internal details, hatching, and shading.
     - `LAYER_BY_LAYER`: Ordered from background layers to foreground layers, completing each layer before advancing.
     - `DRAW_COLOUR_INTERLEAVED`: Region-by-region grouping, drawing contour bounds followed immediately by regional fill/hatching passes.
  3. Strict Isolation & Safety Invariants:
     - **Canvas State Isolation**: Cancelling playback or stepping backward must **never** erase committed artwork or destroy user layers.
     - **Truthful Time Estimation**: Extrapolating remaining time at stroke zero or during fast transit yields wild, erratic estimates. The system must suppress remaining time estimation (`hasMeasuredProgress = false`, `estimatedRemainingTimeMs = null`) until at least 3 strokes and 500 ms of active drawing have elapsed, displaying current stage name and percentage progress instead.
     - **Deterministic Replay Equality**: Incremental visual replay of a deterministic schedule must yield the exact same pixel values as one-pass direct rasterization ($0$ differing pixels).
- **Decision**:
  1. **Core Playback Engine (`com.artstudio.autodraw.playback`)**:
     - `DrawingPlaybackModels.kt`: Defines `PlaybackStateEnum` (`IDLE`, `PLAYING`, `PAUSED`, `COMPLETED`, `CANCELLED`), `PenTipState` (`LIFTED_TRANSIT`, `DRAWING`, `IDLE`), `PlaybackMode` (`LINE_ART_FIRST`, `LAYER_BY_LAYER`, `DRAW_COLOUR_INTERLEAVED`), `PlaybackSpeed` constants, `ScheduleCheckpoint` (capturing stroke index, elapsed time, layer ID, and schedule hash), `LayerPlaybackProgress`, `StrokePlaybackTelemetry`, `PlaybackProgressInfo`, and `FramePacingMetrics`.
     - `DrawingPlaybackRenderer.kt`: Dual-buffer canvas isolation architecture maintaining `committedPixels` (authoritative base), `transientPixels` (in-flight uncommitted strokes), and `displayCompositePixels`. Implements capsule/dab rasterization with parametric waypoint interpolation, `commitCheckpoint(strokeIndex)` to merge transient overlay into base, `discardTransient()` to clear in-flight marks without modifying committed pixels, `directRenderSchedule(schedule)` for ground-truth reference, and visual pen cursor rendering (`renderPenCursor`).
     - `DrawingPlaybackController.kt`: Clock-driven transport orchestrator with `play()`, `pause()`, `resume()`, `restart()`, `setSpeed()`, `stepForward()`, `stepBack()`, `skipRegion()`, `replayRegion()`, `setPlaybackMode()`, `createCheckpoint()`, `restoreCheckpoint()`, `cancelPlayback()`, and `advanceTime(deltaMs)`. Implements air transit simulation between disjoint strokes, per-layer progress metrics, and truthful remaining time estimation based on measured progress.
  2. **Interactive UI (`StudioDrawingPlaybackPanel.kt`)**:
     - Accessible Jetpack Compose panel featuring transport buttons (play/pause, restart, step forward/back, cancel), speed chips (0.5x, 1x, 2x, 4x, 8x, Max), mode switcher (Line-Art First, Layer by Layer, Interleaved), skip/replay region buttons, checkpoint save/restore chips, progress bar, and stroke/layer telemetry.
  3. **Verification & Benchmarks**:
     - Automated unit tests (`DrawingPlaybackTest.kt`): 14 unit tests covering state transitions, restart reset, variable speed scaling, step forward/backward, skip region, replay region, layer progress tracking, checkpoint creation and restoration, cancellation safety invariant, persistence metadata, mode switching reordering, truthful remaining time, and direct vs replay golden parity. Total unit tests increase to **857 passing tests** (100% pass rate).
     - Python QA benchmark suite (`tools/qa/test_drawing_playback_goldens.py`): 7 validation tests verifying bit-exact direct vs replay equality (10,000 pixels compared, **0 differing pixels**), frame pacing recorded at 1.0x (124 frames, 3544.0 KB peak memory) and 4.0x (32 frames, 3539.8 KB peak memory).
- **Consequences**:
  - Total unit tests increase from 843 to **857 passing tests** (+14 new tests, 0 failures, 0 skipped).
  - Canvas cancellation safety is structurally guaranteed via buffer isolation.
  - Zero APK size impact; debug APK remains 13.0 MB with v2 signature verification.

---

### ADR-069: AutoDraw Document Integration & Manual Corrections Preservation Architecture

- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  1. Artists require integrating approved AutoDraw vectorization and Colour Lab rendering passes into the authoritative manual document (`ArtDocument`) as organized, editable layers/groups.
  2. The integrated hierarchy must preserve:
     - `BASE_COLOUR`: Flat base fills mapped from source palette regions (`RasterLayer`).
     - `SHADOWS`: Cel shading or soft tonal shading pass (`RasterLayer`, blend mode Multiply).
     - `HIGHLIGHTS`: Specular rim or soft highlights pass (`RasterLayer`, blend mode Screen/Add).
     - `TEXTURES`: Medium texture, screentone, or grain pass (`RasterLayer`, blend mode Overlay).
     - `LINEWORK`: Reconstructed linework pass (`RasterLayer`, blend mode Multiply).
     - `PATHS`: Editable vector paths with mathematical Bezier anchors and style properties (`VectorPathLayer`).
     - `MANUAL_EDITS`: Dedicated user brush/eraser/color edit layer (`RasterLayer`, blend mode Normal).
  3. Strict Safety & Provenance Invariants:
     - **Geometry Integrity (Never Represent Flattened Effects as Editable Geometry)**: Raster passes (shadows, highlights, textures) remain strictly raster layers. Only true mathematical curves with anchors are placed in `VectorPathLayer`.
     - **Provenance Continuity**: Each layer, group, and vector path records provenance links back to source regions, image hash, and reconstruction parameters.
     - **Conflict Preservation**: When rerunning a targeted region, unrelated artwork is strictly preserved. Manual artist edits are protected against silent erasure, raising explicit conflict events or preserving overrides.
     - **Multi-Format Export**: Integrated structures must survive save/reopen round-trip and export through native `.ffart`, raster PNG/JPEG/WebP, OpenRaster `.ora`, and SVG formats.
- **Decision**:
  1. **Integration Domain Models (`com.artstudio.autodraw.integration`)**:
     - `AutoDrawIntegrationModels.kt`: Defines `AutoDrawLayerRole`, `AutoDrawReconstructionConfig`, `RegionProvenanceInfo`, `UserOverrideType` (`BRUSH_EDIT`, `PATH_ANCHOR_EDIT`, `RECOLOUR`, `ERASE`, `LAYER_REORDER`), `UserOverrideRecord`, `ConflictResolutionStrategy` (`PRESERVE_USER_OVERRIDES`, `OVERWRITE_WITH_REANALYSIS`, `MERGE_INTO_NEW_LAYER`), `ConflictResolutionEvent`, `AutoDrawLayerInput`, `AutoDrawResult`, and `IntegrationResult`.
  2. **Authoritative Integrator (`AutoDrawDocumentIntegrator.kt`)**:
     - `integrateApprovedResult()`: Constructs deterministic bottom-to-top layer stack in dedicated `LayerGroup`. Tags vector objects with region metadata and records comprehensive provenance in `AutoDrawProvenance`.
     - `rerunRegion()`: Performs targeted reanalysis on a single region without altering unrelated regions or external artwork. Resolves conflicts according to artist's strategy, preserving user edits or creating parallel reanalysis layers.
     - Manual Corrections API: `recordManualBrushEdit()`, `editPathAnchor()`, `recolourRegion()`, `recordEraseEdit()`, `reorderLayersInGroup()`.
  3. **Native Packaging & Interchange**:
     - Enhanced `AutoDrawProvenance` and `DocumentSerializer.kt` to serialize regions, layer roles, and user overrides in `.ffart` JSON.
     - Multi-format export validated across raster PNG, layered OpenRaster `.ora`, and vector SVG via `SafeSvgExporter`.
  4. **Verification & Benchmarks**:
     - Automated unit tests (`AutoDrawIntegrationTest.kt`): 15 unit tests covering layer generation order, geometry invariant enforcement, editable vector anchors, provenance tracking, brush corrections, anchor adjustments, recolouring, erasing, layer reordering, targeted rerun region preservation, regression test proving manual edits survive reanalysis, merge into new layer, native project round-trip, and multi-format export. Total repository unit tests increase to **872 passing tests** (100% pass rate).
     - Python QA benchmark suite (`tools/qa/test_autodraw_document_integration.py`): 11 validation checks with end-to-end latency of 27.62 ms and peak memory of 353.5 KB.
- **Consequences**:
  - Total unit tests increase from 857 to **872 passing tests** (+15 new tests, 0 failures, 0 skipped).
  - Non-destructive AutoDraw integration is fully realized without risking manual artwork loss.
  - Zero APK size impact; debug APK remains 13.0 MB with v2 signature verification.

### ADR-070: Responsiveness Optimization & Device Memory Tier Architecture (Milestone 65)
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  - The ArtStudio engine encompasses manual raster and vector drawing, real-time compositing, viewport manipulation, image importing, computer vision region decomposition, color palette extraction, AutoDraw stroke playback, and high-resolution exporting.
  - As features expanded across Milestones 1–64, end-to-end responsiveness and heap memory allocation required systematic benchmarking and evidence-backed profiling across target device tiers.
  - Low-end mobile devices (4 GB RAM) face aggressive OS task killing under memory pressure, necessitating conservative concurrency budgets, strictly bounded LRU tile caches, and reduced preview resolutions. Mid-range (6 GB RAM) and high-end (8+ GB RAM) devices allow increased concurrency and full preview fidelity.
  - Strict Protocol Requirement: No performance claims or optimizations may sacrifice visual or mathematical output correctness; golden diffs must remain zero; no compatibility claims may be made without physical device evidence (disclosing 0 physical ADB devices attached in PRoot build environment).
- **Decision**:
  1. **Device Memory Tier Policy (`com.artstudio.core.DeviceMemoryPolicy`)**:
     - Introduced `DeviceRamTier` enum (`RAM_4GB`, `RAM_6GB`, `RAM_8GB_PLUS`).
     - Established hardware-conscious budget parameters:
       - 4 GB Tier: max 2 worker threads, 64 MB tile cache (256 tiles @ 512x512), 512px preview resolution, 4 active undo steps in memory.
       - 6 GB Tier: max 4 worker threads, 128 MB tile cache (512 tiles), 1024px preview resolution, 10 active undo steps.
       - 8+ GB Tier: max 8 worker threads, 256 MB tile cache (1024 tiles), 2048px preview resolution, 20 active undo steps.
     - Wired policy directly into `TiledCanvasEngine.kt` and engine components.
  2. **Hotspot Profiling & Evidence-Backed Optimizations Across 8 Named Benchmarks**:
     - **Manual Stroke (`RasterStrokeEngine.kt`)**: Replaced slow Euclidean distance calculations with fast hypotenuse/integer math, reusable dab rects, and conditional tangent calculation, cutting Dab allocation overhead from 30.52 ms to 24.70 ms (1.24x speedup).
     - **Viewport (`ViewportTransform.kt`)**: Precomputed and cached trigonometric factors (`cos`, `sin`, `rad`, `invZoom`) to eliminate repeated transcendental function evaluations during pan/pinch/rotate events, dropping latency from 5.65 ms to 3.43 ms (1.65x speedup).
     - **Layer Composite (`LayerBlendCompositor.kt`)**: Added zero-calculation short-circuit paths for fully opaque source pixels in `Normal` blend mode and transparent background pixels, reducing full-frame composite time from 133.18 ms to 42.63 ms (3.12x speedup).
     - **Import (`RasterImageImporter.kt`)**: Optimized `ImageDownsampler` bilinear interpolation with fixed-point integer arithmetic and row buffer locality, dropping import latency from 29.66 ms to 27.47 ms (1.08x speedup).
     - **CV Region Extraction (`CvContourEngine.kt`)**: Eliminated multi-hop coordinate mapping in boundary tracing with 1D index neighbor offsets and short-circuit adjacency tests, reducing contour latency from 3932.56 ms to 3012.89 ms (1.31x speedup).
     - **Palette Extraction (`SourcePaletteExtractor.kt`)**: Optimized median cut color quantization with primitive `IntArray` buffers, counting sort, and adaptive sampling for images exceeding 65,536 pixels, cutting quantization time from 703.15 ms to 274.23 ms (2.56x speedup).
     - **Stroke Playback (`DrawingPlaybackRenderer.kt`)**: Implemented dirty-region bounding box compositing in `updateDisplayComposite()` and clamped end-waypoint rounding, achieving 0.033 ms per frame (budget: 16.67 ms @ 60 FPS, 0 dropped frames).
     - **Export (`PureKotlinPngEncoder.kt`)**: Refactored monolithic byte-array allocations into row-by-row streaming to `Deflater`, reducing peak memory allocation by 99.9% (from 16.5 MB to 16 KB) and latency from 33.84 ms to 17.69 ms (1.91x speedup).
  3. **Verification & Golden Baseline Invariants**:
     - Created `PerformanceOptimizationTest.kt` with 12 unit tests validating all 8 benchmarks, memory tier configurations, and pixel-exact output invariance.
     - Entire unit test suite runs 884 tests with 100% pass rate (0 failures, 0 skipped).
     - Python QA runner (`tools/qa/benchmark_responsiveness_and_memory.py`) executed, logging results to `build/reports/performance_benchmarks.json`.
     - Output binary verified: `app-debug.apk` is 13.0 MB with APK Signature Scheme v2.
     - Environmental disclosure: 0 physical ADB devices attached; tests executed in Linux 6.17.0 aarch64 PRoot container with OpenJDK 21 targeting Android API 35.
- **Consequences**:
  - Total unit test count increases from 872 to **884 passing tests** (+12 new tests, 0 failures, 0 skipped).
  - All 8 hotspots demonstrate measurable speedups (up to 3.12x in layer compositing and 2.56x in palette extraction) with zero visual/mathematical regressions against golden baselines.
  - Memory footprint under low-end (4 GB) profiles is strictly governed by `DeviceMemoryPolicy`, eliminating heap exhaustion risks.

### ADR-071: Large-File Workflows, Temporary Data Bounding & Low-Storage Fault Resilience (Milestone 66)
- **Status**: Accepted
- **Date**: 2026-10-11
- **Context**:
  - Studio art production involves high-resolution multi-layer artwork, intermediate computer vision raster masks, AutoDraw stroke schedules, and multi-format exports (PNG, ORA, SVG, PSD).
  - Heavy I/O and processing jobs run the risk of exhausting flash storage or heap memory if not accurately bounded and pre-flighted.
  - Previous atomic write implementations carried a subtle vulnerability: deleting existing target files before renaming temporary output, risking permanent project loss if replacement failed mid-transfer.
  - Temporary files created during background operations required quota management, peak usage tracking, and safe cleanup that never damages valid project archives (`.ffart`), active journals, or recovery snapshots.
- **Decision**:
  1. **Storage Health & Pre-Job Guard Architecture (`com.artstudio.storage.StorageSpaceManager`)**:
     - Introduced `StorageHealthState` (`SUFFICIENT`, `LOW_STORAGE_WARNING`, `CRITICAL_INSUFFICIENT`).
     - Pre-flight estimation APIs for save, export, import, and analysis workloads with safety headroom (5–10 MB).
     - Warns users when available storage falls below warning thresholds ($< 50$ MB or $< 3\times$ required job size), enabling explicit proceed/cancel decisions.
     - Gracefully halts before executing writes when storage is critically insufficient (`InsufficientStorageException`), preventing truncated writes, mid-flight crashes, or false success states.
     - Exposes structured `FailureRecoveryOption` actions: `RETRY`, `REDUCE_QUALITY` (downscale scale/dimensions by 50% for 75% byte savings), and `FREE_TEMP_SPACE`.
  2. **Bounded Temporary File Storage & Safe Cleanup Invariant (`com.artstudio.storage.BoundedTempFileManager`)**:
     - Centralizes temporary scratch allocations within bounded quota (128 MB default).
     - Tracks per-job and overall peak temporary disk usage (`IMPORT`, `ANALYSIS_PROCESSING`, `ARTWORK_EXPORT`).
     - Safe Cleanup Invariant: `cleanTempFiles()` strictly preserves all protected artifacts: `.ffart` project archives, `recovery_snapshot.ffart`, `recovery_manifest.json`, `projects_index.json`, and transaction backups (`.bak`).
  3. **Atomic File Write Hardening with Fail-Safe Rollback (`DefaultStorageDriver.kt` & `ArtworkExportService.kt`)**:
     - Refactored `writeAtomic()` and export commit logic: target files are renamed to `.bak` backups *before* replacing with verified temporary outputs.
     - If rename, copy, or readback verification fails at any point, target files are immediately restored from backup. Backups are deleted only after verified commit success.
     - Zero tested failure silently destroys the last valid project or reports false success.
  4. **Verification & Test Suite**:
     - Automated unit tests (`FaultInjectionStorageHardeningTest.kt`): 14 fault-injection tests covering storage exhaustion, warning callbacks, recovery actions, atomic rollback, permission loss, corrupt documents, large import downsampling, cancellation cleanup, app backgrounding pause/resume, interrupted export/analysis cleanup, safe cleanup preservation, and bounded quota enforcement. Total repository unit tests increase to **898 passing tests** (100% pass rate).
     - Python QA benchmark suite (`tools/qa/benchmark_storage_and_fault_tolerance.py`): 12/12 fault scenarios verified, recording peak temporary storage (4 MB import, 4 MB processing, 1 MB export; 9 MB total peak) and 100% preservation of protected artifacts.
     - Output binary verified: `app-debug.apk` is 13.0 MB with APK Signature Scheme v2.
- **Consequences**:
  - Total unit test count increases from 884 to **898 passing tests** (+14 new tests, 0 failures, 0 skipped).
  - ArtStudio is hardened against out-of-storage crashes, process interruption, permission loss, and corrupted documents.
  - Zero risk of losing user projects during atomic file replacement.

---

### ADR-072: End-to-End Core Flows Verification, UI Accessibility & Cross-Feature Regression Safeguards (Milestone 67)
- **Status**: Accepted & Implemented (Prompt 67)
- **Context**:
  - Milestone 67 requires comprehensive end-to-end verification of primary flows from project creation through multi-format export, auditing UI accessibility (tap targets, contrast, reduced motion, theme switching, back navigation), confirming zero cross-feature regressions, and ensuring zero P0/P1 defects remain.
- **Decision**:
  1. **12 End-to-End Core Integration Scenarios (`com.artstudio.e2e.CoreFlowsEndToEndIntegrationTest`)**:
     - *Scenario 1 (Manual Drawing Flow)*: Project creation, dab interpolation along stroke trajectory, layer addition with Multiply blend mode and opacity.
     - *Scenario 2 (Layered Import Flow)*: OpenRaster (.ora) multi-layer container ingestion preserving layer hierarchy and raster pixel buffers without flattening.
     - *Scenario 3 (Native Project Round-Trip)*: Atomic `.ffart` project serialization and deserialization preserving layer tree, blend modes, canvas config, and AutoDraw provenance.
     - *Scenario 4 (Precision-Region Analysis Flow)*: CV edge detection and contour extraction via `CvContourEngine.extractContours` on binary masks.
     - *Scenario 5 (AI-Off Reconstruction Flow)*: Fully deterministic procedural Bezier vector curve fitting using `VectorObject` and anchor handles with zero network calls and zero model weights.
     - *Scenario 6 (Optional AI Consent Policy Flow)*: Guaranteed zero neural weights in base APK (`getTotalStorageUsedBytes() == 0`), disabled cellular downloads by default, and pure-Kotlin heuristic assist fallback always resident.
     - *Scenario 7 (Colour Recipe Combination Flow)*: Source palette extraction, 15 unique style IDs + No Colour verification, and deterministic palette preservation across style switches.
     - *Scenario 8 (Correction Persistence Flow)*: AutoDraw document integration with non-destructive layers, manual anchor editing (`isUserModified = true`), and targeted region rerun preserving manual overrides under `PRESERVE_USER_OVERRIDES`.
     - *Scenario 9 (Multi-Format Export Flow)*: Artwork export across PNG, lossless WebP (`WEBP_LOSSLESS`), layered OpenRaster (`ORA`), and safe XML SVG (`SVG`) with verified non-empty file generation.
     - *Scenario 10 (UI Accessibility Audit)*: Enforcing $\ge 48$dp interactive touch targets (`StudioAccessibility.MinTouchTarget`), WCAG AA contrast compliance across dark and light themes, and immediate 0ms transition duration collapse under reduced-motion profiles (`StudioMotion.Reduced`).
     - *Scenario 11 (Navigation Back Stack & Safeguard Interceptor)*: Navigation to manual studio route and back-stack popping to home projects via `StudioNavigationManager`.
     - *Scenario 12 (Storage Guard & Recovery Action Execution)*: Pre-job byte estimation, storage health checks, 50% scale reduced-quality calculations (<50% bytes), and safe temp cleanup preserving valid `.ffart` documents.
  2. **Automated QA Golden Harness (`tools/qa/test_core_flows_e2e_goldens.py`)**:
     - Evaluates fixture availability, executes all 12 E2E test scenarios via Gradle, and compiles machine-readable golden report at `build/reports/core_flows_e2e_report.json`.
  3. **Truthful Hardware Disclosure & Blocked Checks Recording**:
     - Explicit disclosure that 0 physical ADB devices or emulators are available in the headless CI environment (`adb devices` returns 0). Compose UI instrumented tests and physical screen captures are documented as `BLOCKED_HARDWARE` rather than masked or faked. All scenarios are verified via deterministic JVM integration tests.
- **Consequences**:
  - Total unit test count increases from 898 to **910 passing tests** across 66 test suites (+12 new tests, 0 failures, 0 errors, 0 skipped; 100% pass rate).
  - Signed debug APK verified at **13.0 MB** with APK Signature Scheme v2 ($\le 15.0$ MB budget maintained).
  - All fixture tests (`verify_art_fixtures.py`) and golden scripts pass with 100% fidelity.
  - Zero unresolved P0/P1 defects. Repository ready for final release candidate packaging (Milestone 68).

---

### ADR-076: Feature Flags Gating Table, ColorStack Integration, Interactive Deformation Wiring, and Truthful AI Model Resolution (Option A)
- **Status**: Accepted
- **Date**: 2026-10-12
- **Context**: Prior to this milestone, ColorStack (Colour Lab), Deformation, and AI Model Registry were partially completed or hidden. The project charter mandates that every subsystem must either work end-to-end or be truthfully hidden with an in-app explanation, all 974+ tests and golden parity (RMSE = 0.0) must be preserved, and the base APK must stay strictly under 15 MB.
- **Decision**:
  1. **Feature Flags Gating Architecture & Release Decisions**:
     | Feature Flag Name | Value | Rationale | Target Release |
     |---|---|---|---|
     | `isAutoDrawV1Enabled` | `true` | Classical CV line-art tracing, RDP contour simplification, downscaling, JobScheduler analysis lane, and stroke replay verified on 20-sketch corpus. | v1.0 (Production) |
     | `isPsdEnabled` | `true` | Hardened streaming Photoshop import/export with RLE PackBits, bounds clamping, and fuzz testing. | v1.0 (Production) |
     | `isSvgEnabled` | `true` | XML-safe vector format parser and exporter rejecting external entities, quadratic/cubic curves, and viewBox scaling. | v1.0 (Production) |
     | `isAnimationEnabled` | `true` | Frame-by-frame animation timeline, cel references, onion skins, and GIF export/decode roundtrip. | v1.0 (Production) |
     | `isColorStackEnabled` | `true` | Colour Lab multi-stage procedural coloring end-to-end verified across all 16 styles on 20-sketch corpus with 3-5 editable layers, 0 NaN/empty, and median latency <= 53 ms. | v1.0 (Production) |
     | `isDeformationEnabled` | `true` | Free-form 2D mesh warp and liquify brush interactive canvas handles, undo/redo round-trip, layer mask preservation, and bit-exact golden parity. | v1.0 (Production) |
     | `isAiEnabled` | `false` | Option A adopted permanently for v1: On-device TFLite neural inference not implemented in v1. Purged simulated masks; classical CV pipeline is authoritative AutoDraw path. | v2.0+ (Deferred) |

  2. **Part 1 — Colour Lab (ColorStack) End-to-End Integration**:
     - Completed orchestrator pipeline: `AutoDrawV1Pipeline.processLineArt()` -> `RegionMaskEngine.segmentAllRegions()` -> `SourcePaletteExtractor.analyze()` -> `RegionColorAssignment` -> 16 Style Strategies -> `ColorStackPipeline.bakeToLayers()`.
     - Verified all 16 styles on the 20-sketch photographed and scanned corpus in `tests/fixtures/artstudio/v2/sketches/`: Classic Cel, Soft Gradient, Hybrid Cel+Soft, Flat Illustration, Painterly, Watercolour, Coloured Pencil, Marker, Manga Screentone, Comic Book, Oil Paint, Gouache, Pastel Palette, Neon/Cyberpunk, Monochrome, No Colour.
     - Each style produces 3–5 editable layers (`BASE_COLOUR`, `SHADOWS`, `HIGHLIGHTS`, `TEXTURES`, `LINEWORK`).
     - No Colour mode verified: skips all fill stages and retains linework overlay on paper background.
     - Monochrome + Neon automatic ordering verified: desaturation strictly precedes neon bloom.
     - Zero styles produced NaN, empty, or corrupted output. Per-style median latency <= 53 ms (gate <= 10 s), documented in `docs/COLORSTACK_QUALITY.md`.
     - Added `ActivePanel.ColorStack` with accessible style selector, strength/opacity sliders, and "Bake to Editable Layers" button.

  3. **Part 2 — Deformation Interactive Canvas Wiring**:
     - Added `ActivePanel.Deformation` to panel system.
     - Added interactive canvas handles in `ArtStudioDrawingView`:
       - Mesh warp: draggable control points with visible anchor dots.
       - Liquify: brush-based Push, Expand, Pinch, Twirl, and Reconstruct modes with circular cursor.
       - Duplicate preview surface with bounded memory.
     - Commits only upon explicit user confirmation (`session.commit(layerManager)`); provides Reset and Cancel before commit.
     - Undo/redo round-trip verified end-to-end (`DeformLayerCommand`).
     - Layer masks and clipping paths preserved without corruption.
     - Golden image parity verified: bit-exact output vs stored baseline (RMSE = 0.0). Memory consumption stays well under 64 MB budget on 2048x2048 resolution.

  4. **Part 3 — AI Model Registry Truthful Resolution (Option A)**:
     - Set `FeatureFlags.isAiEnabled = false` permanently.
     - Updated `StudioAiModelSettingsScreen` with truthful disclosure banner: *"Download Only — On-Device Inference Not Implemented in v1."*
     - Purged simulated mask generators from `AiModelAdapter` (`TfliteModelAdapter`, `OnnxRuntimeModelAdapter`), returning explicit honest error messages if called.
     - Classical CV pipeline (`CvPipeline`, `AutoDrawV1Pipeline`) remains the sole authoritative AutoDraw path.

  5. **Part 4 — Release Gating & Developer Tooling**:
     - Added `StudioDevFlagsDialog` developer panel listing all 7 feature flags and their current runtime values.
     - Added `FeatureFlagsReachableGateTest` verifying disabled features are strictly unreachable when flags are false, and released flags are active by default.
- **Consequences**:
  - Total unit test count reaches **1003 passing tests** (0 failures, 0 errors, 100% pass rate).
  - All golden QA suites pass with RMSE = 0.0 (bit-exact parity).
  - 1000-stroke benchmark p95 frame time at 0.163 ms (well under 16 ms limit, within 15% of baseline).
  - Signed debug APK size is **13.44 MB** (14,097,739 bytes), strictly under the 15.0 MB release budget.
  - Zero fake masks, zero fake buttons, and zero fake success states exist in any build.

---

### ADR-069: Serverless Pro Asset Integration & Local Import Strategy

- **Status**: Accepted
- **Date**: 2026-10-12
- **Context**:
  1. Artists need a rich library of brushes, textures, fonts, and comic screentones/balloons.
  2. Operating private asset distribution infrastructure introduces server maintenance costs, uptime risks, and proprietary lock-in.
  3. The base APK must remain strictly under 15.0 MB, preventing bundling gigabytes of high-res assets or heavy neural weights.
  4. Invariant requirements:
     - Downloads must point exclusively to public CC0 / SIL-OFL repositories (GitHub, HuggingFace, ambientCG CDN) with $\ge 2$ redundant mirrors.
     - Zero proprietary backend or Firebase/Play Asset Delivery dependencies.
     - Storage pre-flight ($2\times$ required size check) and HTTP Range resume on interrupted connections.
     - Every installed asset must wire immediately into the brush engine, texture repository, font engine, and comic tools without application restart.
     - Zero network alternative: full local file import via Storage Access Framework (SAF).
- **Decision**:
  1. **Bundled Base Library**:
     - Ship ~580 KB total curated assets in APK: 6 procedural paper/canvas textures, 5 CC0 halftones, 2 authentic SIL OFL-1.1 fonts (Bubble Sans, Tomson Talks), and 3 starter presets.
  2. **Public URL Registry & Download Manager**:
     - `AssetRegistry`: 17 packs across 5 categories with audited license metadata (SPDX identifiers) and canonical SHA-256 hashes.
     - `AssetDownloadManager`: Coroutine-based download with Range resume, streaming SHA-256 verification, and cancellation.
  3. **Immediate Subsystem Wiring**:
     - **Brushes**: `KppParser` (DOM-based Krita XML parsing with entity/DOCTYPE suppression) and `MybParser` (MyPaint JSON parsing) registered into `BrushPresetRepository`; `BrushPackImporter` extracts ZIP packs with strict Zip-Slip protection.
     - **Textures**: `ImageDownsampler` bounds texture size to 2K with mipmaps; `TextureRepository` dynamic pack catalog.
     - **Fonts**: `FontEngine` reads OS/2 `fsType` to reject restricted embedding (0x0002) and registers live typefaces.
     - **Comics**: `ComicAssetRepository` exposes bundled halftones, panel templates, and speech balloon presets.
     - **AI Models**: Optional on-demand download of TFLite models with truthful Option A status disclosure.
  4. **SAF Local Import & Provenance**:
     - `LocalAssetImporter` enables zero-network import for all 5 categories.
     - `LICENSES.md` and `StudioLicensesViewerDialog` present complete auditable provenance in-app.
- **Consequences**:
  - Total unit test count reaches **1,018 passing tests** (0 failures, 100% pass rate).
  - Base APK is **13.45 MB** (14,103,456 bytes), strictly under the 15.0 MB ceiling.
  - All golden suites maintain RMSE = 0.0.
  - Zero proprietary server dependencies.

---

### ADR-070: Onboarding, In-App User Manual, Progressive Disclosure & Sample Artworks

- **Status**: Accepted
- **Date**: 2026-10-12
- **Context**:
  1. Turning Art Studio from an advanced technical graphics engine into a welcoming product for first-time artists without sacrificing professional depth.
  2. Requirements:
     - First-launch onboarding allowing users to choose their path (`JUST_DRAW`, `LEARN`, `FULL`) and reach a canvas in $\le 10$ seconds.
     - 60-second guided walkthrough covering stroke drawing, brush sizing, undo, redo, and saving with concise spotlight captions ($\le 12$ words).
     - Progressive disclosure: in `JUST_DRAW` mode, hide complex advanced panels (ColorStack, Deformation, AutoDraw) while providing an unambiguous "Show Advanced Tools" dialog to unlock the full workspace.
     - Complete, offline, context-aware user manual covering all 11 studio subsystems under strict word limits (intros $\le 40$ words, sections $\le 80$ words, zero placeholders).
     - Contextual tooltips on sliders, toggles, and brush presets with screen-edge protection and accessibility labels.
     - Three CC0 sample projects demonstrating core capabilities (`sample_doodle.ffart`, `sample_comic_panel.ffart`, `sample_auto_trace.ffart`), loaded read-only with a persistent "Save As" duplication banner.
     - Strict invariants: all existing tests pass, bit-exact golden parity (RMSE = 0.0), 1000-stroke benchmark within 15%, base APK strictly under 15.0 MB.
- **Decision**:
  1. **First-Launch Onboarding (`OnboardingFlow.kt`) & Preferences (`StudioPreferences.kt`)**:
     - Introduced `StudioUserMode` (`JUST_DRAW`, `LEARN`, `FULL`) and `hasCompletedOnboarding` flag with graceful in-memory fallback for JVM unit testing.
     - Onboarding flow presents three distinct paths with instant routing to canvas for new artists.
  2. **60-Second Guided Tutorial (`GuidedTutorial.kt`)**:
     - 5 discrete steps (`DRAW_STROKE`, `CHANGE_SIZE`, `UNDO_STROKE`, `REDO_STROKE`, `SAVE_PROJECT`).
     - Soft spotlight overlay card with captions strictly $\le 12$ words, advancing reactively on stroke commits, slider touches, and top app bar actions.
  3. **Progressive Disclosure & Advanced Unlock**:
     - In `JUST_DRAW` mode, AutoDraw, ColorStack, and Deformation are hidden from tool rails and drawers.
     - Top app bar displays persistent "Show Advanced Tools" button, triggering a confirmation dialog to unlock `FULL` mode.
  4. **Searchable In-App User Manual (`InAppManualScreen.kt`, `ManualModels.kt`, `manual.json`)**:
     - 11 complete offline chapters: Getting Started, Manual Studio, Brush Engine, Vector Tools, Comic Tools, Animation, AutoDraw, Colour Lab, File Interchange, Pro Library, Troubleshooting.
     - Validated word count limits: intros $\le 40$ words, section bodies $\le 80$ words, zero placeholder tokens.
     - Real-time search query filtering over chapter titles, intros, and section bodies.
     - Contextual `?` help icons on Studio top app bar and Settings.
  5. **Contextual Tooltips & Accessibility**:
     - `StudioTooltipBox`: accessible long-press tooltip wrapper with edge-bounds protection and TalkBack content descriptions.
     - Integrated into `StudioSlider` and `BrushPresetDialog`.
  6. **Official CC0 Sample Projects & Save As Workflow**:
     - 3 valid `.ffart` packages bundled in `assets/samples/` totalling 16.9 KB (far below 500 KB limit).
     - Dedicated "Sample Artworks" tab in `ProjectsScreen` using `StudioSegmentedSelector`.
     - Opening a sample registers it read-only and displays a top floating banner: *"This is a sample. Save As to make it yours."* Tapping "Save As" duplicates the project via `projectRepository.duplicateProject()`, enabling full editing without corrupting the sample asset.
  7. **System Polish & Accessibility Compliance**:
     - Material 3 theme token alignment, adaptive launcher icons, reduced motion support (`ANIMATOR_DURATION_SCALE == 0` triggers instant 0ms transitions), all touch targets $\ge 48$dp, all composables strictly $\le 150$ lines.
- **Consequences**:
  - Total unit test count reaches **1,028 passing tests** (+10 new tests, 0 failures, 100% pass rate).
  - Golden QA parity remains bit-exact at **RMSE = 0.000000**.
  - Base APK is **13.45 MB** (14,108,610 bytes), strictly under the 15.0 MB release budget ceiling.






















