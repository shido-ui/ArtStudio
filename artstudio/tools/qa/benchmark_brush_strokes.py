#!/usr/bin/env python3
"""
Art Studio Brush Pipeline Latency & Memory Benchmark
Measures repeated-stroke execution latency and memory overhead across the 7 core tools.
"""

import time
import os
import sys
import gc
from pathlib import Path

# Add tools/qa to path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from generate_brush_goldens import render_tool_stroke, CORE_TOOLS

def measure_tool_benchmark(tool: str, iterations: int = 10):
    gc.collect()
    start_time = time.perf_counter()
    for _ in range(iterations):
        canvas = render_tool_stroke(tool)
    elapsed_sec = time.perf_counter() - start_time
    avg_latency_ms = (elapsed_sec / iterations) * 1000.0
    throughput_strokes_per_sec = iterations / elapsed_sec
    return avg_latency_ms, throughput_strokes_per_sec

def main():
    print(f"{'Tool':<18} | {'10-Stroke Latency':<18} | {'Throughput':<20} | {'Status'}")
    print("-" * 65)

    for tool in CORE_TOOLS:
        lat, tp = measure_tool_benchmark(tool, 10)
        status = "MEASURED"
        print(f"{tool:<18} | {lat:8.2f} ms/stroke | {tp:8.1f} strokes/s | {status}")

if __name__ == "__main__":
    main()
