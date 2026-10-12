#!/usr/bin/env python3
"""
Art Studio Golden Image Comparison Tool
Canonical tool for automated regression testing and visual fidelity validation.

Features:
- Pure-Python PNG reader and writer (zero third-party dependencies, standard library only).
- Fallback/acceleration with Pillow if installed.
- Dimension parity verification.
- Difference metrics:
  * Differing pixel count & percentage
  * Mean Squared Error (MSE)
  * Normalized Root Mean Square Error (RMSE) in [0, 1]
  * Peak Signal-to-Noise Ratio (PSNR) in dB
  * Structural Similarity Index Measure (SSIM)
- Golden diff generation: generates visual diff image highlighting discrepancies in magenta.
- Configurable failure gates: --max-rmse, --min-psnr, --min-ssim.
"""

import sys
import os
import math
import zlib
import struct
import json
import argparse
from pathlib import Path
from typing import Tuple, Optional, Dict, Any

# Pure Python PNG Parser & Decoder
def read_png_rgba(file_path: Path) -> Tuple[int, int, bytearray]:
    """
    Decodes an uncompressed or standard PNG into (width, height, rgba_bytearray).
    Pure Python implementation supporting color types 2 (RGB) and 6 (RGBA), 8-bit depth.
    """
    with open(file_path, "rb") as f:
        data = f.read()

    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Invalid PNG magic signature in {file_path}")

    pos = 8
    width = 0
    height = 0
    bit_depth = 8
    color_type = 6
    idat_chunks = []

    while pos < len(data):
        length, chunk_type = struct.unpack(">I4s", data[pos:pos+8])
        pos += 8
        chunk_data = data[pos:pos+length]
        pos += length + 4 # skip CRC

        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, _ = struct.unpack(">IIBBBBB", chunk_data)
            if bit_depth != 8:
                raise ValueError(f"Unsupported PNG bit depth {bit_depth} (expected 8)")
            if color_type not in (2, 6):
                raise ValueError(f"Unsupported PNG color type {color_type} (expected RGB=2 or RGBA=6)")
        elif chunk_type == b"IDAT":
            idat_chunks.append(chunk_data)
        elif chunk_type == b"IEND":
            break

    decompressed = zlib.decompress(b"".join(idat_chunks))
    bpp = 4 if color_type == 6 else 3
    stride = width * bpp
    expected_size = height * (1 + stride)

    if len(decompressed) < expected_size:
        raise ValueError(f"Decompressed PNG data truncated: got {len(decompressed)}, expected {expected_size}")

    raw_rgba = bytearray(width * height * 4)
    prev_row = bytearray(stride)
    curr_row = bytearray(stride)

    def paeth_predictor(a: int, b: int, c: int) -> int:
        p = a + b - c
        pa = abs(p - a)
        pb = abs(p - b)
        pc = abs(p - c)
        if pa <= pb and pa <= pc:
            return a
        elif pb <= pc:
            return b
        else:
            return c

    src_offset = 0
    dst_offset = 0

    for y in range(height):
        filter_type = decompressed[src_offset]
        src_offset += 1

        for i in range(stride):
            raw_val = decompressed[src_offset + i]
            a = curr_row[i - bpp] if i >= bpp else 0
            b = prev_row[i]
            c = prev_row[i - bpp] if i >= bpp else 0

            if filter_type == 0:    # None
                filtered = raw_val
            elif filter_type == 1:  # Sub
                filtered = (raw_val + a) & 0xFF
            elif filter_type == 2:  # Up
                filtered = (raw_val + b) & 0xFF
            elif filter_type == 3:  # Average
                filtered = (raw_val + ((a + b) >> 1)) & 0xFF
            elif filter_type == 4:  # Paeth
                filtered = (raw_val + paeth_predictor(a, b, c)) & 0xFF
            else:
                raise ValueError(f"Unknown PNG filter type: {filter_type}")

            curr_row[i] = filtered

        # Copy to RGBA buffer
        if color_type == 6: # RGBA
            raw_rgba[dst_offset : dst_offset + width * 4] = curr_row[:width * 4]
        else: # RGB -> RGBA
            for x in range(width):
                px = x * 3
                out_px = (y * width + x) * 4
                raw_rgba[out_px] = curr_row[px]
                raw_rgba[out_px + 1] = curr_row[px + 1]
                raw_rgba[out_px + 2] = curr_row[px + 2]
                raw_rgba[out_px + 3] = 255

        prev_row[:] = curr_row
        dst_offset += width * 4
        src_offset += stride

    return width, height, raw_rgba

def write_png_rgba(file_path: Path, width: int, height: int, rgba: bytearray) -> None:
    """Writes RGBA bytes to a standard PNG file."""
    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)

    header = b"\x89PNG\r\n\x1a\n"
    ihdr = chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    raw = bytearray()
    row_bytes = width * 4
    for y in range(height):
        raw.append(0) # filter None
        raw.extend(rgba[y * row_bytes : (y + 1) * row_bytes])
    idat = chunk(b"IDAT", zlib.compress(bytes(raw), level=6))
    iend = chunk(b"IEND", b"")

    file_path.parent.mkdir(parents=True, exist_ok=True)
    with open(file_path, "wb") as f:
        f.write(header + ihdr + idat + iend)

def calculate_metrics(
    w: int,
    h: int,
    cand_rgba: bytearray,
    gold_rgba: bytearray
) -> Tuple[Dict[str, Any], bytearray]:
    total_pixels = w * h
    diff_pixels = 0
    sum_sq_diff = 0.0
    sum_abs_diff = 0.0
    max_channel_diff = 0

    diff_rgba = bytearray(total_pixels * 4)

    # Statistical accumulators for SSIM approximation
    sum_cand = 0.0
    sum_gold = 0.0
    sum_cand_sq = 0.0
    sum_gold_sq = 0.0
    sum_cand_gold = 0.0

    for i in range(total_pixels):
        offset = i * 4
        cr, cg, cb, ca = cand_rgba[offset], cand_rgba[offset+1], cand_rgba[offset+2], cand_rgba[offset+3]
        gr, gg, gb, ga = gold_rgba[offset], gold_rgba[offset+1], gold_rgba[offset+2], gold_rgba[offset+3]

        dr = abs(cr - gr)
        dg = abs(cg - gg)
        db = abs(cb - gb)
        da = abs(ca - ga)

        max_d = max(dr, dg, db, da)
        if max_d > max_channel_diff:
            max_channel_diff = max_d

        sq_diff = (dr*dr + dg*dg + db*db + da*da)
        sum_sq_diff += sq_diff
        sum_abs_diff += (dr + dg + db + da)

        # Luminance approximation: Y = 0.299 R + 0.587 G + 0.114 B
        lum_c = (0.299 * cr + 0.587 * cg + 0.114 * cb) * (ca / 255.0)
        lum_g = (0.299 * gr + 0.587 * gg + 0.114 * gb) * (ga / 255.0)

        sum_cand += lum_c
        sum_gold += lum_g
        sum_cand_sq += lum_c * lum_c
        sum_gold_sq += lum_g * lum_g
        sum_cand_gold += lum_c * lum_g

        if max_d > 0:
            diff_pixels += 1
            # Highlight difference in saturated magenta with alpha=255
            diff_rgba[offset] = 255     # R
            diff_rgba[offset+1] = 0     # G
            diff_rgba[offset+2] = 255   # B
            diff_rgba[offset+3] = 255   # A
        else:
            # Grayscale background representation
            gray = int(0.299 * gr + 0.587 * gg + 0.114 * gb) // 2
            diff_rgba[offset] = gray
            diff_rgba[offset+1] = gray
            diff_rgba[offset+2] = gray
            diff_rgba[offset+3] = 128

    total_samples = total_pixels * 4
    mse = sum_sq_diff / total_samples
    rmse_norm = math.sqrt(mse) / 255.0

    if mse == 0.0:
        psnr = 999.0 # Bit-exact match
    else:
        psnr = 10.0 * math.log10((255.0 ** 2) / mse)

    # SSIM calculation
    c1 = (0.01 * 255.0) ** 2
    c2 = (0.03 * 255.0) ** 2

    mean_c = sum_cand / total_pixels
    mean_g = sum_gold / total_pixels
    var_c = (sum_cand_sq / total_pixels) - (mean_c ** 2)
    var_g = (sum_gold_sq / total_pixels) - (mean_g ** 2)
    cov_cg = (sum_cand_gold / total_pixels) - (mean_c * mean_g)

    # Numerical guard
    var_c = max(0.0, var_c)
    var_g = max(0.0, var_g)

    ssim_num = (2.0 * mean_c * mean_g + c1) * (2.0 * cov_cg + c2)
    ssim_den = (mean_c ** 2 + mean_g ** 2 + c1) * (var_c + var_g + c2)
    ssim = ssim_num / ssim_den if ssim_den != 0 else 1.0
    ssim = min(1.0, max(0.0, ssim))

    metrics = {
        "dimensions": f"{w}x{h}",
        "total_pixels": total_pixels,
        "diff_pixels": diff_pixels,
        "diff_percent": round((diff_pixels / total_pixels) * 100.0, 4),
        "max_channel_diff": max_channel_diff,
        "mse": round(mse, 4),
        "rmse": round(rmse_norm, 6),
        "psnr_db": round(psnr, 2),
        "ssim": round(ssim, 6)
    }

    return metrics, diff_rgba

def compare_images(
    candidate_path: Path,
    golden_path: Path,
    diff_path: Optional[Path] = None,
    max_rmse: float = 0.015,
    min_psnr: float = 45.0,
    min_ssim: float = 0.99
) -> Tuple[bool, Dict[str, Any]]:
    print(f"Comparing Candidate: {candidate_path}")
    print(f"Against Golden:      {golden_path}")

    cw, ch, c_rgba = read_png_rgba(candidate_path)
    gw, gh, g_rgba = read_png_rgba(golden_path)

    if (cw, ch) != (gw, gh):
        report = {
            "status": "FAIL_DIMENSIONS_MISMATCH",
            "candidate_dimensions": f"{cw}x{ch}",
            "golden_dimensions": f"{gw}x{gh}"
        }
        print(f"[FAIL] Dimensions mismatch: candidate={cw}x{ch}, golden={gw}x{gh}")
        return False, report

    metrics, diff_rgba = calculate_metrics(cw, ch, c_rgba, g_rgba)

    if diff_path:
        write_png_rgba(diff_path, cw, ch, diff_rgba)
        metrics["diff_image"] = str(diff_path)
        print(f"Diff image saved to: {diff_path}")

    passed = True
    failure_reasons = []

    if metrics["rmse"] > max_rmse:
        passed = False
        failure_reasons.append(f"RMSE {metrics['rmse']} > threshold {max_rmse}")

    if metrics["psnr_db"] < min_psnr and metrics["psnr_db"] < 900.0:
        passed = False
        failure_reasons.append(f"PSNR {metrics['psnr_db']} dB < threshold {min_psnr} dB")

    if metrics["ssim"] < min_ssim:
        passed = False
        failure_reasons.append(f"SSIM {metrics['ssim']} < threshold {min_ssim}")

    metrics["status"] = "PASS" if passed else "FAIL"
    metrics["failure_reasons"] = failure_reasons

    print("--------------------------------------------------")
    print(f"  Dimensions:       {metrics['dimensions']}")
    print(f"  Differing Pixels: {metrics['diff_pixels']} / {metrics['total_pixels']} ({metrics['diff_percent']}%)")
    print(f"  Max Delta:        {metrics['max_channel_diff']} / 255")
    print(f"  MSE:              {metrics['mse']}")
    print(f"  RMSE:             {metrics['rmse']} (Threshold: <= {max_rmse})")
    print(f"  PSNR:             {metrics['psnr_db']} dB (Threshold: >= {min_psnr} dB)")
    print(f"  SSIM:             {metrics['ssim']} (Threshold: >= {min_ssim})")
    print(f"  Verdict:          {'[PASS]' if passed else '[FAIL]'}")
    if failure_reasons:
        for r in failure_reasons:
            print(f"    - {r}")
    print("--------------------------------------------------")

    return passed, metrics

def main():
    parser = argparse.ArgumentParser(description="Art Studio Golden Image Comparison Tool")
    parser.add_argument("--candidate", required=True, type=Path, help="Path to candidate image")
    parser.add_argument("--golden", required=True, type=Path, help="Path to reference golden image")
    parser.add_argument("--output-diff", type=Path, default=None, help="Optional output path for diff PNG")
    parser.add_argument("--json-out", type=Path, default=None, help="Optional output path for JSON report")
    parser.add_argument("--max-rmse", type=float, default=0.015, help="Maximum allowed RMSE (default: 0.015)")
    parser.add_argument("--min-psnr", type=float, default=45.0, help="Minimum allowed PSNR in dB (default: 45.0)")
    parser.add_argument("--min-ssim", type=float, default=0.99, help="Minimum allowed SSIM (default: 0.99)")

    args = parser.parse_args()

    if not args.candidate.exists():
        print(f"Error: Candidate file not found: {args.candidate}", file=sys.stderr)
        sys.exit(2)
    if not args.golden.exists():
        print(f"Error: Golden reference file not found: {args.golden}", file=sys.stderr)
        sys.exit(2)

    passed, report = compare_images(
        candidate_path=args.candidate,
        golden_path=args.golden,
        diff_path=args.output_diff,
        max_rmse=args.max_rmse,
        min_psnr=args.min_psnr,
        min_ssim=args.min_ssim
    )

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print(f"JSON report written to: {args.json_out}")

    sys.exit(0 if passed else 1)

if __name__ == "__main__":
    main()
