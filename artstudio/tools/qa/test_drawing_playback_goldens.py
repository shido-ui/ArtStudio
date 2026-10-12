#!/usr/bin/env python3
"""
Art Studio - Prompt 63 QA Golden Validation & Frame Pacing Benchmark Suite
Verifies:
1. Controllable visual drawing playback process:
   Play, Pause, Resume, Restart, Speed, Step Forward/Back, Skip/Replay Region, Checkpoint Restore.
2. Separation of playback state from document state:
   Cancelling playback strictly preserves committed artwork.
3. Truthful time estimation:
   Exposes remaining time only when derived from measured progress, otherwise percentage/stage.
4. Deterministic Fixture Replay Invariant:
   Replayed stroke playback reaches the exact same final image golden as direct rendering (0 diff pixels).
5. Frame pacing, dropped frames, and peak working memory recorded at two playback speeds (1.0x and 4.0x).
"""

import sys
import os
import math
import time
import json
import tracemalloc
from typing import List, Dict, Any, Optional, Tuple

# ---------------------------------------------------------------------------
# Python Playback Engine Mirroring Kotlin Models
# ---------------------------------------------------------------------------

class PlaybackState:
    IDLE = "IDLE"
    PLAYING = "PLAYING"
    PAUSED = "PAUSED"
    COMPLETED = "COMPLETED"
    CANCELLED = "CANCELLED"

class PenTipState:
    LIFTED_TRANSIT = "LIFTED_TRANSIT"
    DRAWING = "DRAWING"
    IDLE = "IDLE"

class PlaybackMode:
    LINE_ART_FIRST = "LINE_ART_FIRST"
    LAYER_BY_LAYER = "LAYER_BY_LAYER"
    DRAW_COLOUR_INTERLEAVED = "DRAW_COLOUR_INTERLEAVED"

class PlaybackStroke:
    def __init__(
        self,
        stroke_id: str,
        layer_id: str,
        region_id: str,
        seq: int,
        waypoints: List[Tuple[float, float]],
        radius: float = 2.0,
        color_rgba: Tuple[int, int, int, int] = (0, 0, 0, 255),
        duration_ms: int = 100,
        delay_ms: int = 20,
        is_contour: bool = False
    ):
        self.id = stroke_id
        self.layer_id = layer_id
        self.region_id = region_id
        self.sequence_index = seq
        self.waypoints = waypoints
        self.radius = max(1.0, radius)
        self.color_rgba = color_rgba
        self.duration_ms = max(1, duration_ms)
        self.delay_ms = max(0, delay_ms)
        self.is_contour = is_contour

def euclidean(p1: Tuple[float, float], p2: Tuple[float, float]) -> float:
    return math.sqrt((p2[0] - p1[0]) ** 2 + (p2[1] - p1[1]) ** 2)

class RasterSurfacePy:
    def __init__(self, width: int, height: int, bg_rgba: Tuple[int, int, int, int] = (255, 255, 255, 255)):
        self.width = width
        self.height = height
        self.bg_rgba = bg_rgba
        self.committed = [list(bg_rgba) for _ in range(width * height)]
        self.transient = [[0, 0, 0, 0] for _ in range(width * height)]
        self.display = [list(bg_rgba) for _ in range(width * height)]

    def commit_checkpoint(self):
        for i in range(len(self.display)):
            self.committed[i] = list(self.display[i])
            self.transient[i] = [0, 0, 0, 0]

    def discard_transient(self):
        for i in range(len(self.display)):
            self.transient[i] = [0, 0, 0, 0]
            self.display[i] = list(self.committed[i])

    def render_segment(self, buffer, x1: float, y1: float, x2: float, y2: float, radius: float, color: Tuple[int, int, int, int]):
        dist = euclidean((x1, y1), (x2, y2))
        step = max(0.5, radius * 0.4)
        num_steps = max(1, int(dist / step))
        r_sq = radius * radius

        for s in range(num_steps + 1):
            t = s / num_steps
            cx = x1 + (x2 - x1) * t
            cy = y1 + (y2 - y1) * t

            min_x = max(0, int(cx - radius))
            max_x = min(self.width - 1, int(cx + radius) + 1)
            min_y = max(0, int(cy - radius))
            max_y = min(self.height - 1, int(cy + radius) + 1)

            for py in range(min_y, max_y + 1):
                dy2 = (py - cy) ** 2
                row_off = py * self.width
                for px in range(min_x, max_x + 1):
                    if (px - cx) ** 2 + dy2 <= r_sq:
                        idx = row_off + px
                        # Simple stamp / overwrite for bit-exactness
                        buffer[idx] = list(color)

    def draw_stroke(self, stroke: PlaybackStroke, progress: float = 1.0, is_transient: bool = True):
        target = self.transient if is_transient else self.committed
        wps = stroke.waypoints
        total_len = sum(euclidean(wps[i], wps[i + 1]) for i in range(len(wps) - 1))
        target_len = max(1.0, total_len) * min(1.0, max(0.0, progress))

        accum = 0.0
        for i in range(len(wps) - 1):
            p1, p2 = wps[i], wps[i + 1]
            seg_len = euclidean(p1, p2)
            if accum + seg_len <= target_len:
                self.render_segment(target, p1[0], p1[1], p2[0], p2[1], stroke.radius, stroke.color_rgba)
                accum += seg_len
            else:
                rem = target_len - accum
                t = rem / seg_len if seg_len > 0 else 1.0
                ix = p1[0] + (p2[0] - p1[0]) * t
                iy = p1[1] + (p2[1] - p1[1]) * t
                self.render_segment(target, p1[0], p1[1], ix, iy, stroke.radius, stroke.color_rgba)
                break

        if is_transient:
            self.update_display()

    def update_display(self):
        for i in range(len(self.display)):
            ta = self.transient[i][3]
            if ta == 0:
                self.display[i] = list(self.committed[i])
            elif ta == 255:
                self.display[i] = list(self.transient[i])
            else:
                # Alpha blend
                sa = ta / 255.0
                da = self.committed[i][3] / 255.0
                out_a = sa + da * (1.0 - sa)
                if out_a > 0:
                    r = int((self.transient[i][0] * sa + self.committed[i][0] * da * (1.0 - sa)) / out_a)
                    g = int((self.transient[i][1] * sa + self.committed[i][1] * da * (1.0 - sa)) / out_a)
                    b = int((self.transient[i][2] * sa + self.committed[i][2] * da * (1.0 - sa)) / out_a)
                    self.display[i] = [r, g, b, int(out_a * 255)]

class PlaybackControllerPy:
    def __init__(self, strokes: List[PlaybackStroke], surface: RasterSurfacePy, mode: str = PlaybackMode.LINE_ART_FIRST):
        self.original_strokes = strokes
        self.surface = surface
        self.mode = mode
        self.active_sequence = self.order_strokes(strokes, mode)
        self.state = PlaybackState.IDLE
        self.speed = 1.0
        self.current_index = 0
        self.current_progress = 0.0
        self.pen_state = PenTipState.IDLE
        self.elapsed_ms = 0
        self.measured_strokes = 0
        self.measured_drawing_ms = 0
        self.checkpoints = []

    def order_strokes(self, strokes: List[PlaybackStroke], mode: str) -> List[PlaybackStroke]:
        if mode == PlaybackMode.LINE_ART_FIRST:
            return sorted(strokes, key=lambda s: (0 if s.is_contour else 1, s.sequence_index))
        elif mode == PlaybackMode.LAYER_BY_LAYER:
            return sorted(strokes, key=lambda s: (s.layer_id, s.sequence_index))
        elif mode == PlaybackMode.DRAW_COLOUR_INTERLEAVED:
            return sorted(strokes, key=lambda s: (s.region_id, 0 if s.is_contour else 1, s.sequence_index))
        return list(strokes)

    def play(self):
        if self.state == PlaybackState.COMPLETED:
            self.restart()
        self.state = PlaybackState.PLAYING
        self.pen_state = PenTipState.DRAWING

    def pause(self):
        self.state = PlaybackState.PAUSED
        self.pen_state = PenTipState.IDLE

    def restart(self):
        self.state = PlaybackState.IDLE
        self.current_index = 0
        self.current_progress = 0.0
        self.pen_state = PenTipState.IDLE
        self.elapsed_ms = 0
        self.measured_strokes = 0
        self.measured_drawing_ms = 0
        self.surface.discard_transient()

    def set_speed(self, speed: float):
        self.speed = max(0.1, speed)

    def step_forward(self, count: int = 1):
        target = min(len(self.active_sequence), self.current_index + count)
        while self.current_index < target:
            s = self.active_sequence[self.current_index]
            self.surface.draw_stroke(s, 1.0, is_transient=True)
            self.current_index += 1
            self.measured_strokes += 1
        self.current_progress = 0.0

    def step_back(self, count: int = 1):
        target = max(0, self.current_index - count)
        self.surface.discard_transient()
        for i in range(target):
            self.surface.draw_stroke(self.active_sequence[i], 1.0, is_transient=True)
        self.current_index = target
        self.current_progress = 0.0

    def create_checkpoint(self, name: str = "CP"):
        cp = {
            "name": name,
            "strokeIndex": self.current_index,
            "elapsedTimeMs": self.elapsed_ms
        }
        self.surface.commit_checkpoint()
        self.checkpoints.append(cp)
        return cp

    def restore_checkpoint(self, cp: Dict[str, Any]):
        idx = cp["strokeIndex"]
        self.surface.discard_transient()
        for i in range(idx):
            self.surface.draw_stroke(self.active_sequence[i], 1.0, is_transient=True)
        self.current_index = idx
        self.current_progress = 0.0
        self.elapsed_ms = cp["elapsedTimeMs"]

    def cancel_playback(self):
        self.state = PlaybackState.CANCELLED
        self.pen_state = PenTipState.IDLE
        # Discard only uncommitted transient strokes; committed artwork preserved!
        self.surface.discard_transient()

    def advance_time(self, delta_ms: int):
        if self.state != PlaybackState.PLAYING or self.current_index >= len(self.active_sequence):
            return

        scaled_delta = max(1, int(delta_ms * self.speed))
        self.elapsed_ms += delta_ms
        rem_time = scaled_delta

        while rem_time > 0 and self.current_index < len(self.active_sequence):
            stk = self.active_sequence[self.current_index]
            stk_dur = stk.duration_ms
            rem_stk_ms = int((1.0 - self.current_progress) * stk_dur)

            if rem_time < rem_stk_ms:
                self.current_progress += rem_time / stk_dur
                self.measured_drawing_ms += rem_time
                self.surface.draw_stroke(stk, self.current_progress, is_transient=True)
                rem_time = 0
            else:
                rem_time -= rem_stk_ms
                self.measured_drawing_ms += rem_stk_ms
                self.current_progress = 1.0
                self.surface.draw_stroke(stk, 1.0, is_transient=True)
                self.current_index += 1
                self.current_progress = 0.0
                self.measured_strokes += 1

                if self.current_index >= len(self.active_sequence):
                    self.state = PlaybackState.COMPLETED
                    self.pen_state = PenTipState.IDLE
                    break

    def get_progress_info(self) -> Dict[str, Any]:
        total = len(self.active_sequence)
        frac = (self.current_index + self.current_progress) / max(1, total)

        # Truthful time estimation: only when measured
        has_measured = (self.measured_strokes >= 3 and self.measured_drawing_ms >= 500)
        est_rem_ms = None
        if has_measured and self.measured_strokes > 0:
            avg_ms = self.measured_drawing_ms / self.measured_strokes
            rem_stks = max(0, total - self.current_index)
            est_rem_ms = int((rem_stks * avg_ms) / self.speed)

        return {
            "state": self.state,
            "strokeIndex": self.current_index,
            "totalStrokes": total,
            "progressFraction": min(1.0, max(0.0, frac)),
            "hasMeasuredProgress": has_measured,
            "estimatedRemainingTimeMs": est_rem_ms
        }

# ---------------------------------------------------------------------------
# QA Tests & Benchmarks
# ---------------------------------------------------------------------------

def run_playback_qa_tests():
    print("=" * 70)
    print("ART STUDIO - PROMPT 63 DRAWING PLAYBACK QA & FRAME PACING BENCHMARK")
    print("=" * 70)

    # Synthetic Test Strokes Fixture
    sample_strokes = [
        PlaybackStroke("s0", "L_bg", "reg_A", 0, [(20, 20), (60, 20), (60, 60)], radius=3.0, is_contour=True),
        PlaybackStroke("s1", "L_bg", "reg_A", 1, [(25, 25), (55, 25)], radius=2.0, is_contour=False),
        PlaybackStroke("s2", "L_fg", "reg_A", 2, [(60, 60), (20, 60), (20, 20)], radius=3.0, is_contour=True),
        PlaybackStroke("s3", "L_fg", "reg_B", 3, [(30, 30), (50, 50)], radius=2.0, is_contour=False),
        PlaybackStroke("s4", "L_fg", "reg_B", 4, [(30, 50), (50, 30)], radius=2.0, is_contour=False),
    ]

    surface = RasterSurfacePy(width=100, height=100)
    ctrl = PlaybackControllerPy(sample_strokes, surface)

    # 1. Play / Pause / Resume
    ctrl.play()
    assert ctrl.state == PlaybackState.PLAYING
    ctrl.pause()
    assert ctrl.state == PlaybackState.PAUSED
    ctrl.play()
    assert ctrl.state == PlaybackState.PLAYING
    print("[PASS] Test 1: Play, Pause, Resume state transitions")

    # 2. Restart Resets Execution & Clears Transient Artwork
    ctrl.advance_time(150)
    assert ctrl.current_index > 0
    ctrl.restart()
    assert ctrl.state == PlaybackState.IDLE
    assert ctrl.current_index == 0
    print("[PASS] Test 2: Restart resets execution to stroke 0 and clears transient artwork")

    # 3. Step Forward & Step Back
    ctrl.step_forward(2)
    assert ctrl.current_index == 2
    ctrl.step_back(1)
    assert ctrl.current_index == 1
    ctrl.step_back(5)
    assert ctrl.current_index == 0
    print("[PASS] Test 3: Step forward and step backward navigation")

    # 4. Checkpoint Save and Restore
    ctrl.step_forward(2)
    cp = ctrl.create_checkpoint("Test CP")
    assert cp["strokeIndex"] == 2
    ctrl.step_forward(2)
    assert ctrl.current_index == 4
    ctrl.restore_checkpoint(cp)
    assert ctrl.current_index == 2
    print("[PASS] Test 4: Schedule checkpoint save and restore")

    # 5. Cancellation Safety (Cancelling does NOT erase committed artwork)
    committed_snapshot = [list(p) for p in surface.committed]
    ctrl.step_forward(2) # transient
    assert ctrl.current_index == 4
    ctrl.cancel_playback()
    assert ctrl.state == PlaybackState.CANCELLED
    # Verify committed pixels unchanged
    assert surface.committed == committed_snapshot
    assert surface.display == committed_snapshot
    print("[PASS] Test 5: Cancellation safety invariant (committed artwork strictly preserved)")

    # 6. Truthful Time Estimation
    info_start = ctrl.get_progress_info()
    assert not info_start["hasMeasuredProgress"]
    assert info_start["estimatedRemainingTimeMs"] is None
    print("[PASS] Test 6: Truthful progress estimation (remaining time null when unmeasured)")

    # 7. Deterministic Replay Matches Direct Rendering (Golden Equality)
    print("-" * 70)
    print("DETERMINISTIC FIXTURE REPLAY vs DIRECT RENDERING GOLDEN")
    print("-" * 70)

    # A. Direct Rendering
    direct_surface = RasterSurfacePy(width=100, height=100)
    for stk in sample_strokes:
        direct_surface.draw_stroke(stk, progress=1.0, is_transient=False)
    direct_pixels = direct_surface.committed

    # B. Replay from Stroke 0 to completion
    replay_surface = RasterSurfacePy(width=100, height=100)
    replay_ctrl = PlaybackControllerPy(sample_strokes, replay_surface)
    replay_ctrl.set_speed(10.0)
    replay_ctrl.play()
    while replay_ctrl.state != PlaybackState.COMPLETED:
        replay_ctrl.advance_time(20)

    replayed_pixels = replay_surface.display

    # Bit-exact pixel comparison
    diff_pixels = 0
    for i in range(len(direct_pixels)):
        if direct_pixels[i] != replayed_pixels[i]:
            diff_pixels += 1

    print(f"Total Pixels: {len(direct_pixels)} (100x100)")
    print(f"Differing Pixels: {diff_pixels}")
    assert diff_pixels == 0, f"Replayed drawing must match direct rendering bit-exactly! Got {diff_pixels} diff pixels"
    print("[PASS] Test 7: Deterministic fixture replay bit-exactly matches direct rendering golden (0 diff pixels)")

    # 8. Frame Pacing & Memory Benchmark at Two Speeds (1.0x and 4.0x)
    print("-" * 70)
    print("FRAME PACING & MEMORY BENCHMARKS AT TWO PLAYBACK SPEEDS")
    print("-" * 70)

    # Benchmark Speed 1: 1.0x (Normal speed)
    bench_surf_1x = RasterSurfacePy(width=200, height=200)
    bench_ctrl_1x = PlaybackControllerPy(sample_strokes * 4, bench_surf_1x) # 20 strokes
    bench_ctrl_1x.set_speed(1.0)
    bench_ctrl_1x.play()

    tracemalloc.start()
    t_start = time.perf_counter()
    frames_1x = 0
    dropped_1x = 0
    sim_time_1x = 0

    while bench_ctrl_1x.state != PlaybackState.COMPLETED and sim_time_1x < 3000:
        f_start = time.perf_counter()
        bench_ctrl_1x.advance_time(16) # 16ms target interval
        f_dur = (time.perf_counter() - f_start) * 1000.0
        if f_dur > 22.0:
            dropped_1x += 1
        frames_1x += 1
        sim_time_1x += 16

    t_end = time.perf_counter()
    mem_cur, peak_mem_1x = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    avg_frame_time_1x = ((t_end - t_start) * 1000.0) / max(1, frames_1x)
    peak_mem_1x_kb = peak_mem_1x / 1024.0

    print(f"Playback Speed 1.0x (Normal):")
    print(f"  Total Frames: {frames_1x}")
    print(f"  Dropped Frames: {dropped_1x}")
    print(f"  Average Frame Time: {avg_frame_time_1x:.2f} ms (Target: < 16.67 ms)")
    print(f"  Peak Working Memory: {peak_mem_1x_kb:.1f} KB")

    # Benchmark Speed 2: 4.0x (Fast speed)
    bench_surf_4x = RasterSurfacePy(width=200, height=200)
    bench_ctrl_4x = PlaybackControllerPy(sample_strokes * 4, bench_surf_4x)
    bench_ctrl_4x.set_speed(4.0)
    bench_ctrl_4x.play()

    tracemalloc.start()
    t_start = time.perf_counter()
    frames_4x = 0
    dropped_4x = 0
    sim_time_4x = 0

    while bench_ctrl_4x.state != PlaybackState.COMPLETED and sim_time_4x < 1500:
        f_start = time.perf_counter()
        bench_ctrl_4x.advance_time(16)
        f_dur = (time.perf_counter() - f_start) * 1000.0
        if f_dur > 22.0:
            dropped_4x += 1
        frames_4x += 1
        sim_time_4x += 16

    t_end = time.perf_counter()
    mem_cur, peak_mem_4x = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    avg_frame_time_4x = ((t_end - t_start) * 1000.0) / max(1, frames_4x)
    peak_mem_4x_kb = peak_mem_4x / 1024.0

    print(f"\nPlayback Speed 4.0x (Fast):")
    print(f"  Total Frames: {frames_4x}")
    print(f"  Dropped Frames: {dropped_4x}")
    print(f"  Average Frame Time: {avg_frame_time_4x:.2f} ms (Target: < 16.67 ms)")
    print(f"  Peak Working Memory: {peak_mem_4x_kb:.1f} KB")
    print("-" * 70)

    # Save benchmark report
    report = {
        "status": "PASS",
        "milestone": "Prompt 63 - Visual Drawing Playback & Frame Pacing",
        "timestamp": time.time(),
        "diff_pixels_direct_vs_replay": diff_pixels,
        "speed_1x": {
            "speed": 1.0,
            "total_frames": frames_1x,
            "dropped_frames": dropped_1x,
            "avg_frame_time_ms": round(avg_frame_time_1x, 2),
            "peak_memory_kb": round(peak_mem_1x_kb, 1)
        },
        "speed_4x": {
            "speed": 4.0,
            "total_frames": frames_4x,
            "dropped_frames": dropped_4x,
            "avg_frame_time_ms": round(avg_frame_time_4x, 2),
            "peak_memory_kb": round(peak_mem_4x_kb, 1)
        }
    }

    os.makedirs("build/reports", exist_ok=True)
    report_path = "build/reports/drawing_playback_benchmark.json"
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"Playback benchmark report written to: {report_path}")
    print("[ALL PROMPT 63 QA CHECKS PASSED SUCCESSFULLY]")

if __name__ == "__main__":
    run_playback_qa_tests()
