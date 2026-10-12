# ArtStudio Authoritative Document Model & Package Specification (.ffart)

**Status:** Authoritative  
**Current Schema Version:** `2.0.0`  
**Legacy Schema Version:** `1.0.0`  
**MIME Type:** `application/vnd.artstudio.project`  
**Package Extension:** `.ffart` (FocusForge Art / ArtStudio Project)  

---

## 1. Architectural Philosophy & Invariants

The `ArtDocument` model is the authoritative, editable project state shared between manual drawing, AutoDraw AI reconstruction, Colour Lab shading, and animation.

1. **Separation of Persistent Model and Live Views:**
   The document model contains pure immutable data classes describing structure, vector trajectories, metadata, and asset references. It never serializes live `View`, Compose state holders, or volatile GPU/Bitmap caches.
2. **Explicit Layer Ordering:**
   Within any group (including the root group), layer order follows standard 2D compositing convention:
   - **Index 0:** Bottom-most layer (rendered first).
   - **Index `size - 1`:** Top-most layer (rendered last).
3. **Stable Identifiers:**
   All layers, groups, vector paths, swatches, and reference images possess immutable UUID strings preserved across serialization, duplication, and migration.
4. **Normalized Coordinate & Alpha Semantics:**
   - Coordinates are stored in physical canvas pixel units (`Float` or `Int`).
   - Opacity is normalized strictly between `0.0f` (transparent) and `1.0f` (opaque).
   - Colors are represented as 64-bit unsigned/signed integers (`0xAARRGGBB`).
   - Stylus pressure is normalized between `0.0f` and `1.0f`.
   - Stylus tilt angles are stored in radians or normalized `-1.0f..1.0f` vector components.

---

## 2. Package Architecture (`.ffart`)

The `.ffart` container format is a standard ZIP archive following OpenRaster, EPUB, and ODF encapsulation standards.

### 2.1 File Layout

```text
my_artwork.ffart (ZIP container)
├── mimetype                        [STORED uncompressed at offset 30, value: application/vnd.artstudio.project]
├── project.json                    [DEFLATED UTF-8 JSON describing the complete ArtDocument schema]
├── thumbnail.png                   [DEFLATED optional rendered thumbnail preview]
└── assets/                         [DEFLATED directory of binary resources]
    ├── layer_paper_texture.png
    ├── layer_flats.png
    └── ref_pose_reference.png
```

### 2.2 Security & Integrity Guards
- **Zip Slip Traversal Prevention:** The loader rejects any archive entry containing `..` or starting with `/` with a `CorruptDocumentException`.
- **MIME Validation:** The first entry must be named `mimetype` with contents exactly matching `application/vnd.artstudio.project`.
- **Atomic Persistence:** Documents are written to a temporary sibling file and renamed atomically into place to prevent corruption during power loss or system interruption.

---

## 3. Schema Structure (`project.json` v2.0.0)

### 3.1 Top-Level Document

| Field | Type | Description |
| :--- | :--- | :--- |
| `schemaVersion` | `String` | `"2.0.0"` (authoritative version identifier) |
| `appVersion` | `String` | Application build version (e.g. `"1.0.0"`) |
| `id` | `String` | Stable document UUID |
| `title` | `String` | Human-readable document name |
| `createdAt` | `Long` | Epoch timestamp in milliseconds |
| `updatedAt` | `Long` | Epoch timestamp in milliseconds |
| `canvas` | `Object` | Canvas configuration (dimensions, DPI, background, color space) |
| `rootGroup` | `Object` | Top-level root `LayerGroup` node |
| `palettes` | `Array<Object>` | User color palettes and swatches |
| `referenceImages` | `Array<Object>` | Pinned floating reference images |
| `animation` | `Object?` | Optional animation timeline configuration |
| `autoDrawProvenance` | `Object?` | Optional AutoDraw AI provenance and parameters |

### 3.2 Canvas Configuration

```json
{
  "width": 2048,
  "height": 2048,
  "dpi": 300,
  "backgroundColor": -1,
  "colorSpace": "sRGB"
}
```
- `width`, `height`: Positive integers representing canvas dimensions in pixels.
- `dpi`: Screen resolution (standard 72, 150, 300, 600).
- `backgroundColor`: 32-bit ARGB color value (`-1` = `0xFFFFFFFF` opaque white, `0` = fully transparent).
- `colorSpace`: `"sRGB"` or `"DisplayP3"`.

### 3.3 Layer Tree Hierarchy (`LayerNode`)

Polymorphic sealed hierarchy:
- `LayerNode.LayerGroup`: Can contain nested groups, raster layers, and vector path layers.
- `LayerNode.RasterLayer`: Holds raster pixel content referenced via `assetPath`.
- `LayerNode.VectorPathLayer`: Holds vector path trajectories and pressure curve points.

#### Common Layer Attributes

| Field | Type | Description |
| :--- | :--- | :--- |
| `type` | `String` | `"group"`, `"raster"`, or `"vector"` |
| `id` | `String` | Stable layer UUID |
| `name` | `String` | Human-readable layer name |
| `visible` | `Boolean` | Visibility flag |
| `opacity` | `Float` | `0.0f` to `1.0f` |
| `blendMode` | `String` | One of 9 supported blend modes (see below) |
| `transform` | `Object` | `{"tx": Float, "ty": Float, "sx": Float, "sy": Float, "rot": Float}` |
| `isLocked` | `Boolean` | User edit lock |
| `isClippingMask` | `Boolean` | Clips layer compositing to the layer directly beneath it |

#### Supported Blend Modes

1. `normal`
2. `multiply`
3. `screen`
4. `overlay`
5. `soft_light`
6. `darken`
7. `lighten`
8. `color_dodge`
9. `color_burn`

#### Vector Path Layer Schema

```json
{
  "type": "vector",
  "id": "layer_ink_01",
  "name": "Ink Pen Strokes",
  "visible": true,
  "opacity": 1.0,
  "blendMode": "normal",
  "transform": {"tx": 0.0, "ty": 0.0, "sx": 1.0, "sy": 1.0, "rot": 0.0},
  "isLocked": false,
  "isClippingMask": false,
  "paths": [
    {
      "id": "path_001",
      "brushId": "inking_pen",
      "strokeColor": -16777216,
      "strokeWidth": 4.5,
      "points": [
        {"x": 100.5, "y": 150.2, "p": 0.45, "tx": 0.1, "ty": 0.0, "t": 1700000000100}
      ]
    }
  ]
}
```

### 3.4 Animation Timeline Configuration

```json
{
  "fps": 12,
  "totalFrames": 24,
  "onionSkinEnabled": true,
  "onionSkinFramesBefore": 2,
  "onionSkinFramesAfter": 1,
  "frameTracks": [
    {
      "layerId": "layer_f1",
      "activeFrames": [0, 1]
    },
    {
      "layerId": "layer_f2",
      "activeFrames": [2, 3]
    }
  ]
}
```

### 3.5 AutoDraw Provenance Configuration

```json
{
  "sourceImageHash": "e57bb565dbdc2d8977bbba949b2cbe67576b5c39121a88bb8f060fc18ae676b7",
  "algorithm": "offline_morphological_v1",
  "edgeThresholdLow": 40.0,
  "edgeThresholdHigh": 120.0,
  "generatedLayerIds": ["layer_base_color", "layer_shadows", "layer_lineart"],
  "timestampMs": 1700000100000
}
```

---

## 4. Schema Migration Rules (v1.0.0 $\to$ v2.0.0)

When a document with `schemaVersion == "1.0.0"` is encountered:
1. **Root Group Synthesis:** Flat array under `"layers"` is migrated into a synthesized `LayerGroup` with `id = "root_group"`, `name = "Root"`, and `children` retaining exact original array ordering.
2. **Opacity Normalization:** If legacy layer `opacity > 1.0f` (e.g. `85` or `100`), it is normalized via `opacity / 100f` into `0.0f..1.0f`.
3. **Default Transforms:** Attached `LayerTransform(tx=0, ty=0, sx=1, sy=1, rot=0)` to each migrated layer.
4. **Color Space Fallback:** Injects default `"sRGB"` if missing from canvas.
5. **Clipping Mask & Lock Defaults:** Injects `isClippingMask = false` and `isLocked = false` if omitted.
6. **Version Bump:** Output document has `schemaVersion = "2.0.0"`.

---

## 5. Verification & Test Suite

The model and packaging engine are verified by 15 tests in `ArtDocumentRoundTripAndMigrationTest`:
- `testJsonParserAndSerializerRoundTrip_simpleAndNested`
- `testSingleLayerDocument_roundTripMatchesExactly`
- `testMultilayerHierarchy_preservesLayerOrderAndGroupStructure`
- `testClippingMasksAndBlendModes_preservedCorrectly`
- `testVectorPathData_preservesCoordinatesPressureTiltAndTimestamps`
- `testAnimationTimeline_roundTripPreservesFrameTracksAndOnionSkin`
- `testAutoDrawProvenance_preserved`
- `testPalettesAndReferences_preserved`
- `testLegacyV1ToV2Migration_convertsFlatLayersAndNormalizesOpacity`
- `testPackageSaveAndLoad_ffartArchiveIntegrity`
- `testPackageCorruptDetection_missingMimetypeThrows`
- `testPackageCorruptDetection_missingProjectJsonThrows`
- `testPackageCorruptDetection_malformedJsonThrows`
- `testPackageSecurity_zipSlipPathTraversalRejected`
- `testSampleDocuments_fromFixturesDirectoryLoadSuccessfully`
