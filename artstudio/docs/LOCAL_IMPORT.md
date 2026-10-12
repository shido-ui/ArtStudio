# Art Studio — Local Asset Import Guide (Zero-Network Workflow)

This guide documents how to import Pro brushes, textures, fonts, comic assets, and AI models into Art Studio without requiring an active internet connection.

---

## 1. Overview & Privacy Guarantee

Art Studio uses a strictly **serverless, offline-first** architecture. You do not need an account, Play Store connectivity, or proprietary cloud services to load assets. All features are fully functional in airplane mode.

If you have assets on your personal computer (or offline storage), you can transfer them via USB cable, SD card, local Wi-Fi transfer, or Android Nearby Share, and import them directly into Art Studio using Android's **Storage Access Framework (SAF)**.

---

## 2. Supported File Formats by Category

| Category | Supported File Extensions | Target Pipeline | Notes |
| :--- | :--- | :--- | :--- |
| **Brushes** | `.brushpack`, `.kpp`, `.myb`, `.json`, `.zip` | Brush Engine / Preset Dialog | Automatically parses Krita (.kpp) XML and MyPaint (.myb) JSON brushes into native dynamics. |
| **Textures** | `.png`, `.jpg`, `.webp`, `.zip` | Texture Repository / Mipmap Chain | Textures > 2K are automatically downsampled to 2K mipmaps (2048, 1024, 512) for 4 GB RAM safety. |
| **Typography** | `.ttf`, `.otf` | Font Engine / Text Panel | Verified against OS/2 `fsType` table to ensure embedding compliance. |
| **Comic Assets**| `.svg`, `.png`, `.zip` | Comic Panel / Halftone Engine | Screentones, speedlines, panel borders, and speech balloon templates. |
| **AI Models** | `.tflite`, `.onnx` | Optional AI Model Adapter | Optional weights only. Classical CV contour tracing remains fully authoritative. |

---

## 3. How to Transfer Assets from PC to Phone

1. **Download CC0 or Licensed Assets on Your PC**:
   - Download Krita brush bundles from David Revoy (`.bundle` / `.zip`) or MyPaint brush sets (`.myb`).
   - Download seamless textures from ambientCG or Poly Haven (`.png` / `.jpg`).
   - Download open-source fonts from Google Fonts or Fontsource (`.ttf` / `.otf`).
2. **Connect Phone via USB**:
   - Select **File Transfer (MTP)** mode on your Android device.
   - Copy the downloaded files into your device's `Download/` or `Documents/` folder.
3. **Open Art Studio**:
   - Navigate to **Settings → Pro Library** (or open the respective panel in the Studio canvas).
   - Tap **Import from Files**.
   - Select the transferred file using the Android system file picker.
   - The asset is verified, parsed, and registered immediately without restarting the app.

---

## 4. Integrity and Security Checks

Every local import performs:
1. **Sandboxed Staging**: Streams incoming URI to private cache scratch file before committing.
2. **Magic Byte Verification**: Verifies file magic headers (ZIP `PK`, PNG `\x89PNG`, TrueType `0x00010000`, OpenType `OTTO`).
3. **Zip Slip Protection**: Rejects archives attempting path traversal outside of app-private storage.
4. **Instant Invalidation on Failure**: Automatically purges invalid or corrupt temp files and displays actionable error messages.
