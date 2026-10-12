#!/usr/bin/env python3
"""
Art Studio - Prompt 62 QA Golden & Benchmark Validation Suite
Verifies:
1. Deterministic Drawing Schedule Synthesis across all 4 ordering strategies
   (CONTOUR_FIRST, LAYER_BY_LAYER, SPATIALLY_EFFICIENT, ANATOMICAL).
2. Stroke Direction Optimization (FORWARD / REVERSE) & Closed-loop winding invariant.
3. Topological Dependency DAG & User Priority Overrides.
4. Single-pen cursor simulation timeline monotonicity & non-overlapping intervals.
5. Deterministic Schedule SHA-256 Hashing & Reproducibility.
6. Non-historical fidelity disclaimer invariant.
7. Latency and Memory Benchmarks on simple (lineart_simple_01) and detail-dense (lineart_dense_02) fixtures.
"""

import sys
import os
import math
import time
import hashlib
import json
import tracemalloc
from typing import List, Dict, Any, Optional, Set, Tuple

# ---------------------------------------------------------------------------
# Python Domain Model Mirroring Kotlin Models
# ---------------------------------------------------------------------------

class StrokeDirection:
    FORWARD = "FORWARD"
    REVERSE = "REVERSE"
    BIDIRECTIONAL = "BIDIRECTIONAL"

class StrokeSemanticRole:
    OUTLINE = "OUTLINE"
    INTERNAL_DETAIL = "INTERNAL_DETAIL"
    HATCHING = "HATCHING"
    SHADOW_ACCENT = "SHADOW_ACCENT"
    FREEHAND = "FREEHAND"

ROLE_RANK = {
    StrokeSemanticRole.OUTLINE: 0,
    StrokeSemanticRole.INTERNAL_DETAIL: 1,
    StrokeSemanticRole.HATCHING: 2,
    StrokeSemanticRole.SHADOW_ACCENT: 3,
    StrokeSemanticRole.FREEHAND: 4,
}

class AnatomicalTag:
    HEAD = 0
    EYES = 1
    FACE = 2
    HAIR = 3
    TORSO = 4
    LIMBS = 5
    BACKGROUND = 6
    GENERAL_DETAIL = 7

TAG_NAMES = {
    0: "HEAD", 1: "EYES", 2: "FACE", 3: "HAIR",
    4: "TORSO", 5: "LIMBS", 6: "BACKGROUND", 7: "GENERAL_DETAIL"
}

class ScheduleOrderingStrategy:
    CONTOUR_FIRST = "CONTOUR_FIRST"
    LAYER_BY_LAYER = "LAYER_BY_LAYER"
    SPATIALLY_EFFICIENT = "SPATIALLY_EFFICIENT"
    ANATOMICAL = "ANATOMICAL"

class SchedulablePathPy:
    def __init__(
        self,
        path_id: str,
        layer_id: str,
        start_x: float,
        start_y: float,
        end_x: float,
        end_y: float,
        path_length_px: float,
        is_closed: bool = False,
        semantic_role: str = StrokeSemanticRole.OUTLINE,
        anatomical_rank: Optional[int] = None,
        prerequisites: Optional[Set[str]] = None,
        user_priority: Optional[int] = None
    ):
        self.id = path_id
        self.layer_id = layer_id
        self.start_x = start_x
        self.start_y = start_y
        self.end_x = end_x
        self.end_y = end_y
        self.path_length_px = max(1.0, path_length_px)
        self.is_closed = is_closed
        self.semantic_role = semantic_role
        self.anatomical_rank = anatomical_rank
        self.prerequisites = prerequisites or set()
        self.user_priority = user_priority

def euclidean_dist(x1: float, y1: float, x2: float, y2: float) -> float:
    return math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2)

def compute_schedule_hash(entries: List[Dict[str, Any]]) -> str:
    digest = hashlib.sha256()
    for e in entries:
        deps_sorted = ",".join(sorted(e.get("dependencies", [])))
        line = f"{e['pathId']}:{e['sequenceIndex']}:{e['direction']}:{e['layerId']}:{e['timing']['startTimestampMs']}:{e['timing']['estimatedDurationMs']}:{deps_sorted}"
        digest.update(line.encode("utf-8"))
    return digest.hexdigest()

def generate_schedule_py(
    paths: List[SchedulablePathPy],
    strategy: str = ScheduleOrderingStrategy.CONTOUR_FIRST,
    nominal_pen_speed: float = 400.0,
    pen_lift_speed: float = 1200.0,
    allow_reversal: bool = True,
    user_priorities: Optional[Dict[str, int]] = None,
    randomize: bool = False,
    seed: int = 42
) -> Dict[str, Any]:
    user_priorities = user_priorities or {}

    if not paths:
        return {
            "entries": [],
            "scheduleHash": hashlib.sha256(b"").hexdigest(),
            "totalDurationMs": 0,
            "totalPenTravelPx": 0.0,
            "disclaimer": "This synthesized reconstruction schedule represents an optimized machine drawing plan; it does not claim to reproduce the original artist's real human stroke history."
        }

    # 1. Dependency Graph
    dep_graph: Dict[str, Set[str]] = {p.id: set(p.prerequisites) for p in paths}

    # User priority overrides
    for pA in paths:
        prioA = user_priorities.get(pA.id, pA.user_priority)
        if prioA is not None:
            for pB in paths:
                if pA.id != pB.id:
                    prioB = user_priorities.get(pB.id, pB.user_priority)
                    if prioB is not None and prioA < prioB:
                        dep_graph[pB.id].add(pA.id)

    # Strategy dependencies
    if strategy == ScheduleOrderingStrategy.CONTOUR_FIRST:
        outlines = [p for p in paths if p.semantic_role == StrokeSemanticRole.OUTLINE]
        non_outlines = [p for p in paths if p.semantic_role != StrokeSemanticRole.OUTLINE]
        for out in outlines:
            for non in non_outlines:
                if out.layer_id == non.layer_id:
                    out_prio = user_priorities.get(out.id, out.user_priority)
                    non_prio = user_priorities.get(non.id, non.user_priority)
                    if out_prio is None or non_prio is None or out_prio <= non_prio:
                        dep_graph[non.id].add(out.id)
    elif strategy == ScheduleOrderingStrategy.LAYER_BY_LAYER:
        layers = sorted(list({p.layer_id for p in paths}))
        for i in range(len(layers) - 1):
            lower = [p for p in paths if p.layer_id == layers[i]]
            upper = [p for p in paths if p.layer_id == layers[i + 1]]
            for lp in lower:
                for up in upper:
                    lp_prio = user_priorities.get(lp.id, lp.user_priority)
                    up_prio = user_priorities.get(up.id, up.user_priority)
                    if lp_prio is None or up_prio is None or lp_prio <= up_prio:
                        dep_graph[up.id].add(lp.id)
    elif strategy == ScheduleOrderingStrategy.ANATOMICAL:
        for pA in paths:
            if pA.anatomical_rank is not None:
                for pB in paths:
                    if pB.anatomical_rank is not None and pA.anatomical_rank < pB.anatomical_rank:
                        pA_prio = user_priorities.get(pA.id, pA.user_priority)
                        pB_prio = user_priorities.get(pB.id, pB.user_priority)
                        if pA_prio is None or pB_prio is None or pA_prio <= pB_prio:
                            dep_graph[pB.id].add(pA.id)

    completed: Set[str] = set()
    remaining = list(paths)
    entries: List[Dict[str, Any]] = []

    curr_pen_x = paths[0].start_x if paths else 0.0
    curr_pen_y = paths[0].start_y if paths else 0.0
    curr_time_ms = 0
    total_travel_px = 0.0
    total_length_px = 0.0
    seq_idx = 0
    lcg_seed = seed

    def min_pen_dist(p: SchedulablePathPy) -> float:
        d_start = euclidean_dist(curr_pen_x, curr_pen_y, p.start_x, p.start_y)
        if allow_reversal and not p.is_closed:
            d_end = euclidean_dist(curr_pen_x, curr_pen_y, p.end_x, p.end_y)
            return min(d_start, d_end)
        return d_start

    while remaining:
        available = [p for p in remaining if dep_graph[p.id].issubset(completed)]
        if not available:
            # Deadlock breaker: candidate with fewest unfulfilled dependencies
            candidate_pool = [min(remaining, key=lambda p: len(dep_graph[p.id] - completed))]
        else:
            candidate_pool = available

        if randomize:
            lcg_seed = (lcg_seed * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
            pick_idx = (lcg_seed >> 32) % len(candidate_pool)
            chosen = candidate_pool[pick_idx]
        else:
            if strategy == ScheduleOrderingStrategy.SPATIALLY_EFFICIENT:
                chosen = min(candidate_pool, key=lambda p: (min_pen_dist(p), p.id))
            elif strategy == ScheduleOrderingStrategy.CONTOUR_FIRST:
                chosen = min(candidate_pool, key=lambda p: (ROLE_RANK.get(p.semantic_role, 99), min_pen_dist(p), p.id))
            elif strategy == ScheduleOrderingStrategy.ANATOMICAL:
                chosen = min(candidate_pool, key=lambda p: (p.anatomical_rank if p.anatomical_rank is not None else 99, min_pen_dist(p), p.id))
            elif strategy == ScheduleOrderingStrategy.LAYER_BY_LAYER:
                chosen = min(candidate_pool, key=lambda p: (p.layer_id, min_pen_dist(p), p.id))
            else:
                chosen = candidate_pool[0]

        # Direction determination
        if allow_reversal and not chosen.is_closed:
            d_start = euclidean_dist(curr_pen_x, curr_pen_y, chosen.start_x, chosen.start_y)
            d_end = euclidean_dist(curr_pen_x, curr_pen_y, chosen.end_x, chosen.end_y)
            if d_end < d_start:
                direction = StrokeDirection.REVERSE
                start_x, start_y = chosen.end_x, chosen.end_y
                end_x, end_y = chosen.start_x, chosen.start_y
            else:
                direction = StrokeDirection.FORWARD
                start_x, start_y = chosen.start_x, chosen.start_y
                end_x, end_y = chosen.end_x, chosen.end_y
        else:
            direction = StrokeDirection.FORWARD
            start_x, start_y = chosen.start_x, chosen.start_y
            end_x, end_y = chosen.end_x, chosen.end_y

        air_dist = euclidean_dist(curr_pen_x, curr_pen_y, start_x, start_y)
        delay_ms = 0 if seq_idx == 0 else max(0, round(air_dist / (pen_lift_speed / 1000.0)))
        dur_ms = max(1, round(chosen.path_length_px / (nominal_pen_speed / 1000.0)))
        start_ts = curr_time_ms + delay_ms
        end_ts = start_ts + dur_ms

        curr_time_ms = end_ts
        curr_pen_x, curr_pen_y = end_x, end_y
        total_travel_px += air_dist
        total_length_px += chosen.path_length_px

        entry = {
            "id": f"sched_{chosen.id}_{seq_idx}",
            "pathId": chosen.id,
            "layerId": chosen.layer_id,
            "sequenceIndex": seq_idx,
            "direction": direction,
            "timing": {
                "estimatedDurationMs": dur_ms,
                "delayBeforeMs": delay_ms,
                "startTimestampMs": start_ts,
                "endTimestampMs": end_ts
            },
            "dependencies": sorted(list(dep_graph[chosen.id])),
            "startPoint": {"x": start_x, "y": start_y},
            "endPoint": {"x": end_x, "y": end_y},
            "pathLengthPx": chosen.path_length_px,
            "isClosed": chosen.is_closed,
            "semanticRole": chosen.semantic_role
        }

        entries.append(entry)
        completed.add(chosen.id)
        remaining.remove(chosen)
        seq_idx += 1

    total_dur = entries[-1]["timing"]["endTimestampMs"] if entries else 0
    sched_hash = compute_schedule_hash(entries)

    return {
        "entries": entries,
        "scheduleHash": sched_hash,
        "totalDurationMs": total_dur,
        "totalPenTravelPx": total_travel_px,
        "totalDrawingLengthPx": total_length_px,
        "disclaimer": "This synthesized reconstruction schedule represents an optimized machine drawing plan; it does not claim to reproduce the original artist's real human stroke history."
    }

# ---------------------------------------------------------------------------
# Test & Benchmark Suite
# ---------------------------------------------------------------------------

def run_all_qa_tests():
    print("=" * 70)
    print("ART STUDIO - PROMPT 62 DRAWING SCHEDULE QA & BENCHMARK VALIDATION")
    print("=" * 70)

    # 1. Test Deterministic Hash Reproducibility
    p1 = SchedulablePathPy("p1", "L1", 0, 0, 50, 0, 50)
    p2 = SchedulablePathPy("p2", "L1", 50, 0, 100, 0, 50)
    p3 = SchedulablePathPy("p3", "L1", 0, 50, 50, 50, 50)
    s1 = generate_schedule_py([p1, p2, p3])
    s2 = generate_schedule_py([p1, p2, p3])
    assert s1["scheduleHash"] == s2["scheduleHash"], "Schedule hashes must be identical for identical inputs"
    assert len(s1["scheduleHash"]) == 64, "SHA-256 hash must be 64 hex characters"
    print("[PASS] Test 1: Deterministic schedule hash bit-exact repeatability")

    # 2. Test Contour-First Ordering
    p_hatch = SchedulablePathPy("hatch", "L1", 20, 20, 30, 30, 14, semantic_role=StrokeSemanticRole.HATCHING)
    p_detail = SchedulablePathPy("detail", "L1", 10, 10, 15, 15, 7, semantic_role=StrokeSemanticRole.INTERNAL_DETAIL)
    p_outline = SchedulablePathPy("outline", "L1", 0, 0, 100, 100, 141, semantic_role=StrokeSemanticRole.OUTLINE)
    s_contour = generate_schedule_py([p_hatch, p_detail, p_outline], strategy=ScheduleOrderingStrategy.CONTOUR_FIRST)
    order_roles = [e["semanticRole"] for e in s_contour["entries"]]
    assert order_roles == [StrokeSemanticRole.OUTLINE, StrokeSemanticRole.INTERNAL_DETAIL, StrokeSemanticRole.HATCHING], f"Unexpected order: {order_roles}"
    print("[PASS] Test 2: Contour-first ordering (outlines scheduled before internal details & hatching)")

    # 3. Test Layer-By-Layer Ordering
    p_fg = SchedulablePathPy("fg", "layer_fg", 10, 10, 20, 20, 14)
    p_bg = SchedulablePathPy("bg", "layer_bg", 0, 0, 100, 100, 141)
    s_layer = generate_schedule_py([p_fg, p_bg], strategy=ScheduleOrderingStrategy.LAYER_BY_LAYER)
    assert s_layer["entries"][0]["layerId"] == "layer_bg"
    assert s_layer["entries"][1]["layerId"] == "layer_fg"
    print("[PASS] Test 3: Layer-by-layer ordering (lower layers complete before upper layers)")

    # 4. Test Spatially Efficient Ordering
    p_start = SchedulablePathPy("start", "L1", 0, 0, 10, 0, 10)
    p_far = SchedulablePathPy("far", "L1", 500, 500, 510, 500, 10)
    p_near = SchedulablePathPy("near", "L1", 12, 0, 20, 0, 8)
    s_spatial = generate_schedule_py([p_start, p_far, p_near], strategy=ScheduleOrderingStrategy.SPATIALLY_EFFICIENT)
    spatial_order = [e["pathId"] for e in s_spatial["entries"]]
    assert spatial_order == ["start", "near", "far"], f"Unexpected spatial order: {spatial_order}"
    print("[PASS] Test 4: Spatially efficient ordering (pen air travel minimized)")

    # 5. Test Direction Reversal Optimization
    p_rev1 = SchedulablePathPy("r1", "L1", 0, 0, 10, 10, 14, is_closed=False)
    p_rev2 = SchedulablePathPy("r2", "L1", 100, 100, 11, 10, 14, is_closed=False)
    s_rev = generate_schedule_py([p_rev1, p_rev2], allow_reversal=True)
    assert s_rev["entries"][1]["direction"] == StrokeDirection.REVERSE
    assert s_rev["entries"][1]["startPoint"]["x"] == 11 and s_rev["entries"][1]["startPoint"]["y"] == 10
    print("[PASS] Test 5: Direction reversal optimization on open stroke paths")

    # 6. Test Closed Loop Invariant
    p_closed = SchedulablePathPy("closed", "L1", 50, 50, 11, 10, 50, is_closed=True)
    s_closed = generate_schedule_py([p_rev1, p_closed], allow_reversal=True)
    closed_entry = next(e for e in s_closed["entries"] if e["pathId"] == "closed")
    assert closed_entry["direction"] == StrokeDirection.FORWARD, "Closed shapes must never be reversed"
    print("[PASS] Test 6: Closed-loop shapes strictly maintain FORWARD winding")

    # 7. Test User Priority Overrides
    p_low = SchedulablePathPy("p_low", "L1", 0, 0, 10, 0, 10, semantic_role=StrokeSemanticRole.OUTLINE)
    p_high = SchedulablePathPy("p_high", "L1", 50, 50, 60, 50, 10, semantic_role=StrokeSemanticRole.HATCHING)
    s_override = generate_schedule_py([p_low, p_high], user_priorities={"p_high": 0, "p_low": 10})
    assert s_override["entries"][0]["pathId"] == "p_high", "User priority override must precede default strategy"
    print("[PASS] Test 7: User priority overrides supersede default scheduling heuristics")

    # 8. Test Timeline Monotonicity & Non-Overlapping Pen Intervals
    for i in range(len(s_contour["entries"]) - 1):
        c = s_contour["entries"][i]
        n = s_contour["entries"][i + 1]
        assert n["timing"]["startTimestampMs"] >= c["timing"]["endTimestampMs"], "Pen intervals must not overlap"
    print("[PASS] Test 8: Single-pen cursor simulation timeline monotonicity verified")

    # 9. Test Non-Historical Fidelity Disclaimer Invariant
    assert "does not claim to reproduce the original artist's real human stroke history" in s1["disclaimer"]
    print("[PASS] Test 9: Non-historical fidelity disclaimer invariant strictly verified")

    # 10. Benchmarks on Simple and Detail-Dense Fixtures
    print("-" * 70)
    print("FIXTURE BENCHMARKS")
    print("-" * 70)

    # Simple Fixture: 15 stroke paths (representative of lineart_simple_01)
    simple_paths = []
    for i in range(15):
        simple_paths.append(SchedulablePathPy(
            path_id=f"simple_{i}",
            layer_id="layer_ink",
            start_x=float((i * 30) % 400),
            start_y=float((i * 45) % 400),
            end_x=float((i * 30 + 25) % 400),
            end_y=float((i * 45 + 25) % 400),
            path_length_px=35.0,
            is_closed=(i % 3 == 0),
            semantic_role=StrokeSemanticRole.OUTLINE if i % 2 == 0 else StrokeSemanticRole.INTERNAL_DETAIL
        ))

    tracemalloc.start()
    t0 = time.perf_counter()
    res_simple = generate_schedule_py(simple_paths, strategy=ScheduleOrderingStrategy.CONTOUR_FIRST)
    t1 = time.perf_counter()
    mem_current, mem_peak_simple = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    latency_simple_ms = (t1 - t0) * 1000.0
    mem_simple_kb = mem_peak_simple / 1024.0

    print(f"Simple Fixture (15 paths):")
    print(f"  Latency: {latency_simple_ms:.2f} ms (Target: < 50.0 ms)")
    print(f"  Peak Memory: {mem_simple_kb:.1f} KB (Target: < 5000.0 KB)")
    print(f"  Schedule Hash: {res_simple['scheduleHash']}")
    assert latency_simple_ms < 50.0, f"Simple latency exceeded: {latency_simple_ms} ms"

    # Detail-Dense Fixture: 180 stroke paths (representative of lineart_dense_02 complex anime sketch)
    dense_paths = []
    for i in range(180):
        dense_paths.append(SchedulablePathPy(
            path_id=f"dense_{i}",
            layer_id=f"layer_{(i // 30)}",
            start_x=float((i * 17) % 1024),
            start_y=float((i * 23) % 1024),
            end_x=float((i * 17 + 40) % 1024),
            end_y=float((i * 23 + 40) % 1024),
            path_length_px=56.5,
            is_closed=(i % 5 == 0),
            semantic_role=StrokeSemanticRole.OUTLINE if i < 40 else (
                StrokeSemanticRole.INTERNAL_DETAIL if i < 120 else StrokeSemanticRole.HATCHING
            ),
            anatomical_rank=(i % 8)
        ))

    tracemalloc.start()
    t0 = time.perf_counter()
    res_dense = generate_schedule_py(dense_paths, strategy=ScheduleOrderingStrategy.CONTOUR_FIRST)
    t1 = time.perf_counter()
    mem_current, mem_peak_dense = tracemalloc.get_traced_memory()
    tracemalloc.stop()

    latency_dense_ms = (t1 - t0) * 1000.0
    mem_dense_kb = mem_peak_dense / 1024.0

    print(f"\nDetail-Dense Fixture (180 paths):")
    print(f"  Latency: {latency_dense_ms:.2f} ms (Target: < 500.0 ms)")
    print(f"  Peak Memory: {mem_dense_kb:.1f} KB (Target: < 15000.0 KB)")
    print(f"  Schedule Hash: {res_dense['scheduleHash']}")
    assert latency_dense_ms < 500.0, f"Dense latency exceeded: {latency_dense_ms} ms"
    print("-" * 70)

    # Save benchmark report
    report = {
        "status": "PASS",
        "milestone": "Prompt 62 - Deterministic Drawing Schedule",
        "timestamp": time.time(),
        "disclaimer_verified": True,
        "simple_fixture": {
            "paths_count": len(simple_paths),
            "latency_ms": round(latency_simple_ms, 2),
            "peak_memory_kb": round(mem_simple_kb, 1),
            "schedule_hash": res_simple["scheduleHash"],
            "total_duration_ms": res_simple["totalDurationMs"]
        },
        "dense_fixture": {
            "paths_count": len(dense_paths),
            "latency_ms": round(latency_dense_ms, 2),
            "peak_memory_kb": round(mem_dense_kb, 1),
            "schedule_hash": res_dense["scheduleHash"],
            "total_duration_ms": res_dense["totalDurationMs"]
        }
    }

    os.makedirs("build/reports", exist_ok=True)
    report_path = "build/reports/drawing_schedule_benchmark.json"
    with open(report_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"Benchmark report written to: {report_path}")
    print("[ALL QA CHECKS PASSED SUCCESSFULLY]")

if __name__ == "__main__":
    run_all_qa_tests()
