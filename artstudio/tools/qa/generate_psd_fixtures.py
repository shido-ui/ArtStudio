#!/usr/bin/env python3
"""
Art Studio Synthetic PSD Fixtures Generator
Generates CC0-1.0 licensed test assets for Photoshop (.psd) interchange testing:
1. sample_layered_simple_01.psd (2 layers: Background and Subject, 64x64, 8-bit RGB, PackBits RLE)
2. sample_layered_clipping_02.psd (3 layers: Base Layer, Clipping Detail [clipping=1], Hidden Sketch [visible=false], 64x64)
3. corrupted_header_01.psd (Corrupted magic bytes 'XXXX')
4. truncated_channels_02.psd (Truncated RLE channel data)
"""

import os
import sys
import struct
import hashlib
import json
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
FIXTURES_DIR = REPO_ROOT / "tests" / "fixtures" / "artstudio" / "v1"

def packbits_encode(data: bytes) -> bytes:
    out = bytearray()
    i = 0
    n = len(data)
    while i < n:
        # Check run of identical bytes
        run = 1
        while i + run < n and run < 128 and data[i + run] == data[i]:
            run += 1
        if run >= 3:
            # Repeat run
            out.append(256 - (run - 1))
            out.append(data[i])
            i += run
        else:
            # Literal run
            lit_start = i
            while i < n and (i - lit_start < 128):
                if i + 2 < n and data[i] == data[i+1] == data[i+2]:
                    break
                i += 1
            lit_len = i - lit_start
            out.append(lit_len - 1)
            out.extend(data[lit_start:i])
    return bytes(out)

def encode_channel_rle(pixels: bytes, width: int, height: int) -> bytes:
    compressed_rows = []
    for y in range(height):
        row = pixels[y * width : (y + 1) * width]
        compressed_rows.append(packbits_encode(row))
    
    # 2 bytes short for compression = 1
    out = bytearray(struct.pack(">H", 1))
    for r in compressed_rows:
        out.extend(struct.pack(">H", len(r)))
    for r in compressed_rows:
        out.extend(r)
    return bytes(out)

def build_resolution_info() -> bytes:
    block = bytearray()
    block.extend(b"8BIM")
    block.extend(struct.pack(">H", 0x03ED)) # ResolutionInfo resource ID
    block.extend(struct.pack(">H", 0))     # Pascal name empty
    block.extend(struct.pack(">I", 16))    # data size
    block.extend(struct.pack(">I", 300 << 16)) # 300 DPI
    block.extend(struct.pack(">H", 1))     # pixels per inch
    block.extend(struct.pack(">H", 1))     # inches
    block.extend(struct.pack(">I", 300 << 16))
    block.extend(struct.pack(">H", 1))
    block.extend(struct.pack(">H", 1))
    return bytes(block)

def create_psd_file(width: int, height: int, layers_data: list, composite_rgb: list) -> bytes:
    """
    layers_data is a list of dicts:
    {
        'name': str,
        'blend_key': str,
        'opacity': int (0..255),
        'clipping': int (0 or 1),
        'flags': int (1 for alpha lock, 2 for invisible),
        'top': int, 'left': int, 'bottom': int, 'right': int,
        'channels': { -1: bytes (alpha), 0: bytes (R), 1: bytes (G), 2: bytes (B) }
    }
    composite_rgb: [R_bytes, G_bytes, B_bytes] of size width * height
    """
    out = bytearray()
    
    # 1. Header (26 bytes)
    out.extend(b"8BPS")
    out.extend(struct.pack(">H", 1))      # version 1
    out.extend(b"\x00" * 6)               # reserved
    out.extend(struct.pack(">H", 3))      # channels
    out.extend(struct.pack(">I", height))
    out.extend(struct.pack(">I", width))
    out.extend(struct.pack(">H", 8))      # 8 bits per channel
    out.extend(struct.pack(">H", 3))      # RGB mode
    
    # 2. Color Mode Data (length 0)
    out.extend(struct.pack(">I", 0))
    
    # 3. Image Resources Section
    res_block = build_resolution_info()
    out.extend(struct.pack(">I", len(res_block)))
    out.extend(res_block)
    
    # 4. Layer and Mask Information Section
    layer_info_ba = bytearray()
    num_layers = len(layers_data)
    # negative count indicates merged alpha in composite
    layer_info_ba.extend(struct.pack(">h", -num_layers))
    
    # Pre-encode channels for each layer
    encoded_layer_channels = []
    for lyr in layers_data:
        lw = lyr['right'] - lyr['left']
        lh = lyr['bottom'] - lyr['top']
        ch_list = []
        for ch_id in [-1, 0, 1, 2]:
            ch_bytes = lyr['channels'][ch_id]
            enc = encode_channel_rle(ch_bytes, lw, lh)
            ch_list.append((ch_id, enc))
        encoded_layer_channels.append(ch_list)
        
    # Layer Records
    for i, lyr in enumerate(layers_data):
        layer_info_ba.extend(struct.pack(">iiii", lyr['top'], lyr['left'], lyr['bottom'], lyr['right']))
        layer_info_ba.extend(struct.pack(">H", 4)) # 4 channels (-1, 0, 1, 2)
        
        ch_list = encoded_layer_channels[i]
        for ch_id, enc in ch_list:
            layer_info_ba.extend(struct.pack(">hI", ch_id, len(enc)))
            
        layer_info_ba.extend(b"8BIM")
        blend_key = lyr.get('blend_key', 'norm').ljust(4)[:4].encode('ascii')
        layer_info_ba.extend(blend_key)
        layer_info_ba.append(lyr.get('opacity', 255))
        layer_info_ba.append(lyr.get('clipping', 0))
        layer_info_ba.append(lyr.get('flags', 0))
        layer_info_ba.append(0) # filler
        
        # Extra data
        extra_ba = bytearray()
        extra_ba.extend(struct.pack(">I", 0)) # mask data len
        extra_ba.extend(struct.pack(">I", 0)) # blending ranges len
        
        name_bytes = lyr['name'].encode('utf-8')[:255]
        extra_ba.append(len(name_bytes))
        extra_ba.extend(name_bytes)
        # Pad pascal string to multiple of 4 bytes
        total_name_len = 1 + len(name_bytes)
        padded_len = ((total_name_len + 3) // 4) * 4
        pad_needed = padded_len - total_name_len
        extra_ba.extend(b"\x00" * pad_needed)
        
        layer_info_ba.extend(struct.pack(">I", len(extra_ba)))
        layer_info_ba.extend(extra_ba)
        
    # Channel image data
    for ch_list in encoded_layer_channels:
        for ch_id, enc in ch_list:
            layer_info_ba.extend(enc)
            
    # Pad layer info to even byte count
    if len(layer_info_ba) % 2 != 0:
        layer_info_ba.append(0)
        
    layer_section_ba = bytearray()
    layer_section_ba.extend(struct.pack(">I", len(layer_info_ba)))
    layer_section_ba.extend(layer_info_ba)
    
    if len(layer_section_ba) % 2 != 0:
        layer_section_ba.append(0)
        
    out.extend(struct.pack(">I", len(layer_section_ba)))
    out.extend(layer_section_ba)
    
    # 5. Global Composite Image Data
    # Encode R, G, B channels
    comp_rle_rows = []
    for ch in range(3):
        ch_bytes = composite_rgb[ch]
        for y in range(height):
            row = ch_bytes[y * width : (y + 1) * width]
            comp_rle_rows.append(packbits_encode(row))
            
    comp_ba = bytearray()
    comp_ba.extend(struct.pack(">H", 1)) # RLE
    for r in comp_rle_rows:
        comp_ba.extend(struct.pack(">H", len(r)))
    for r in comp_rle_rows:
        comp_ba.extend(r)
        
    out.extend(comp_ba)
    return bytes(out)

def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hasher.update(chunk)
    return hasher.hexdigest()

def main():
    layered_dir = FIXTURES_DIR / "layered_psd"
    corrupt_dir = FIXTURES_DIR / "corrupt_truncated"
    layered_dir.mkdir(parents=True, exist_ok=True)
    corrupt_dir.mkdir(parents=True, exist_ok=True)
    
    W, H = 64, 64
    
    # 1. sample_layered_simple_01.psd (2 layers: Background white, Circle blue)
    bg_alpha = b"\xFF" * (W * H)
    bg_r = b"\xFF" * (W * H)
    bg_g = b"\xFF" * (W * H)
    bg_b = b"\xFF" * (W * H)
    
    # Subject layer: Blue square in center (16..48)
    fg_alpha = bytearray(W * H)
    fg_r = bytearray(W * H)
    fg_g = bytearray(W * H)
    fg_b = bytearray(W * H)
    for y in range(H):
        for x in range(W):
            idx = y * W + x
            if 16 <= x < 48 and 16 <= y < 48:
                fg_alpha[idx] = 255
                fg_r[idx] = 30
                fg_g[idx] = 144
                fg_b[idx] = 255
            else:
                fg_alpha[idx] = 0
                fg_r[idx] = 0
                fg_g[idx] = 0
                fg_b[idx] = 0
                
    comp_r = bytearray(bg_r)
    comp_g = bytearray(bg_g)
    comp_b = bytearray(bg_b)
    for i in range(W * H):
        if fg_alpha[i] > 0:
            comp_r[i] = fg_r[i]
            comp_g[i] = fg_g[i]
            comp_b[i] = fg_b[i]
            
    layers_simple = [
        {
            'name': 'Background',
            'blend_key': 'norm',
            'opacity': 255,
            'clipping': 0,
            'flags': 0,
            'top': 0, 'left': 0, 'bottom': H, 'right': W,
            'channels': { -1: bg_alpha, 0: bg_r, 1: bg_g, 2: bg_b }
        },
        {
            'name': 'Subject',
            'blend_key': 'norm',
            'opacity': 255,
            'clipping': 0,
            'flags': 0,
            'top': 0, 'left': 0, 'bottom': H, 'right': W,
            'channels': { -1: bytes(fg_alpha), 0: bytes(fg_r), 1: bytes(fg_g), 2: bytes(fg_b) }
        }
    ]
    
    psd_simple = create_psd_file(W, H, layers_simple, [bytes(comp_r), bytes(comp_g), bytes(comp_b)])
    p_simple = layered_dir / "sample_layered_simple_01.psd"
    with open(p_simple, "wb") as f:
        f.write(psd_simple)
    print(f"Generated {p_simple} ({len(psd_simple)} bytes)")
    
    # 2. sample_layered_clipping_02.psd (3 layers: Base, Clipping Detail [clipping=1], Hidden Sketch [flags=2])
    clip_alpha = bytearray(W * H)
    clip_r = bytearray(W * H)
    clip_g = bytearray(W * H)
    clip_b = bytearray(W * H)
    for y in range(H):
        for x in range(W):
            idx = y * W + x
            if 24 <= x < 40 and 24 <= y < 40:
                clip_alpha[idx] = 255
                clip_r[idx] = 255
                clip_g[idx] = 215
                clip_b[idx] = 0 # Gold
            else:
                clip_alpha[idx] = 0
                
    sketch_alpha = bytearray(W * H)
    sketch_r = bytearray(W * H)
    sketch_g = bytearray(W * H)
    sketch_b = bytearray(W * H)
    for y in range(H):
        for x in range(W):
            idx = y * W + x
            if x == y or x == (H - 1 - y):
                sketch_alpha[idx] = 200
                sketch_r[idx] = 128
                sketch_g[idx] = 128
                sketch_b[idx] = 128
                
    layers_clipping = [
        {
            'name': 'Base Layer',
            'blend_key': 'norm',
            'opacity': 255,
            'clipping': 0,
            'flags': 0,
            'top': 0, 'left': 0, 'bottom': H, 'right': W,
            'channels': { -1: bytes(fg_alpha), 0: bytes(fg_r), 1: bytes(fg_g), 2: bytes(fg_b) }
        },
        {
            'name': 'Clipping Detail',
            'blend_key': 'mul ',
            'opacity': 230,
            'clipping': 1, # Clipping mask!
            'flags': 0,
            'top': 0, 'left': 0, 'bottom': H, 'right': W,
            'channels': { -1: bytes(clip_alpha), 0: bytes(clip_r), 1: bytes(clip_g), 2: bytes(clip_b) }
        },
        {
            'name': 'Hidden Sketch',
            'blend_key': 'norm',
            'opacity': 180,
            'clipping': 0,
            'flags': 2, # Bit 1 = 1 means hidden/invisible!
            'top': 0, 'left': 0, 'bottom': H, 'right': W,
            'channels': { -1: bytes(sketch_alpha), 0: bytes(sketch_r), 1: bytes(sketch_g), 2: bytes(sketch_b) }
        }
    ]
    
    psd_clipping = create_psd_file(W, H, layers_clipping, [bytes(comp_r), bytes(comp_g), bytes(comp_b)])
    p_clipping = layered_dir / "sample_layered_clipping_02.psd"
    with open(p_clipping, "wb") as f:
        f.write(psd_clipping)
    print(f"Generated {p_clipping} ({len(psd_clipping)} bytes)")
    
    # 3. corrupted_header_01.psd (Corrupted magic 'XXXX')
    corrupted_magic = bytearray(psd_simple)
    corrupted_magic[0:4] = b"XXXX"
    p_bad_header = corrupt_dir / "corrupted_header_01.psd"
    with open(p_bad_header, "wb") as f:
        f.write(corrupted_magic)
    print(f"Generated {p_bad_header} ({len(corrupted_magic)} bytes)")
    
    # 4. truncated_channels_02.psd (Truncated stream halfway through layer data)
    truncated_data = psd_simple[: len(psd_simple) // 2]
    p_truncated = corrupt_dir / "truncated_channels_02.psd"
    with open(p_truncated, "wb") as f:
        f.write(truncated_data)
    print(f"Generated {p_truncated} ({len(truncated_data)} bytes)")

if __name__ == "__main__":
    main()
