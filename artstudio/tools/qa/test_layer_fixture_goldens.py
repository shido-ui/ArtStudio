#!/usr/bin/env python3
"""
Art Studio 10-Layer Fixture & Golden Verification Suite
Canonical QA test verifying:
1. The 10-layer fixture renders against the stored golden with zero pixel diff.
2. Reporting metrics: Differing Pixels, MSE, RMSE, PSNR, SSIM.
3. Baseline enforcement gates.
4. Visibility culling validation (unhiding hidden layer triggers diff).
5. Transparency validation (mutating opacity triggers diff).
6. Layer ordering validation (reordering layers triggers diff).
7. Save/reopen persistence from .ffart archive preserving stable IDs and ordering.
"""

import os
import sys
import json
import zipfile
from pathlib import Path

# Add tools/qa to sys.path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from compare_golden_images import compare_images, read_png_rgba, write_png_rgba
from generate_layer_goldens import build_ten_layers, composite_layers, LayerDef


def test_ten_layer_golden_baseline():
    base_dir = Path(__file__).resolve().parent.parent.parent
    golden_path = base_dir / "tests" / "fixtures" / "artstudio" / "v1" / "goldens" / "layers" / "ten_layer_fixture_golden.png"
    sample_dir = base_dir / "tests" / "fixtures" / "artstudio" / "v1" / "sample_projects"
    ffart_path = sample_dir / "ten_layer_fixture.ffart"

    assert golden_path.exists(), f"Stored golden file missing: {golden_path}"
    assert ffart_path.exists(), f"Stored .ffart file missing: {ffart_path}"

    print(f"=== 1. Validating 10-Layer Fixture Against Stored Golden ===")
    print(f"Golden File: {golden_path}")

    # Re-render canonical 10-layer fixture
    layers = build_ten_layers()
    rendered = composite_layers(layers, 512, 512)

    # Save to temporary test render
    test_render_path = golden_path.parent / "test_ten_layer_render.png"
    write_png_rgba(test_render_path, 512, 512, rendered.buffer)

    # Compare test render against stored golden
    passed, metrics = compare_images(
        test_render_path,
        golden_path,
        max_rmse=0.0001,
        min_psnr=80.0,
        min_ssim=0.9999
    )

    print("\n--- Pixel Diff & Fidelity Report ---")
    print(f"Dimensions: {metrics['dimensions']}")
    print(f"Total Pixels: {metrics['total_pixels']}")
    print(f"Differing Pixels: {metrics['diff_pixels']} ({metrics['diff_percent']:.4f}%)")
    print(f"MSE: {metrics['mse']:.6f}")
    print(f"RMSE: {metrics['rmse']:.6f}")
    print(f"PSNR: {metrics['psnr_db']:.2f} dB")
    print(f"SSIM: {metrics['ssim']:.6f}")

    # Enforce strict baseline gates
    assert passed, "Golden comparison baseline failed"
    assert metrics["diff_pixels"] == 0, f"Expected 0 differing pixels, got {metrics['diff_pixels']}"
    assert metrics["rmse"] == 0.0, f"Expected RMSE 0.0, got {metrics['rmse']}"
    assert metrics["ssim"] == 1.0, f"Expected SSIM 1.0, got {metrics['ssim']}"
    print(">>> BASELINE ENFORCED: Bit-exact match (RMSE = 0.0, SSIM = 1.0)")

    # Clean up temp render
    if test_render_path.exists():
        test_render_path.unlink()

    print("\n=== 2. Validating Visibility Culling ===")
    # Unhide layer 3 (Hidden Draft Grid)
    layers_unhidden = build_ten_layers()
    layers_unhidden[3].visible = True
    rendered_unhidden = composite_layers(layers_unhidden, 512, 512)
    unhidden_path = golden_path.parent / "test_unhidden.png"
    write_png_rgba(unhidden_path, 512, 512, rendered_unhidden.buffer)
    _, metrics_unhidden = compare_images(unhidden_path, golden_path)
    if unhidden_path.exists():
        unhidden_path.unlink()

    print(f"When layer 3 is unhidden: differing pixels = {metrics_unhidden['diff_pixels']}, RMSE = {metrics_unhidden['rmse']:.4f}")
    assert metrics_unhidden["diff_pixels"] > 0, "Unhiding layer must change visual output"
    print(">>> VISIBILITY CULLING VERIFIED: Hidden layers are strictly excluded from output")

    print("\n=== 3. Validating Opacity & Transparency Compositing ===")
    # Mutate opacity of layer 4 from 1.0 to 0.4
    layers_opacity = build_ten_layers()
    layers_opacity[4].opacity = 0.4
    rendered_opacity = composite_layers(layers_opacity, 512, 512)
    opacity_path = golden_path.parent / "test_opacity.png"
    write_png_rgba(opacity_path, 512, 512, rendered_opacity.buffer)
    _, metrics_opacity = compare_images(opacity_path, golden_path)
    if opacity_path.exists():
        opacity_path.unlink()

    print(f"When layer 4 opacity changed 1.0 -> 0.4: differing pixels = {metrics_opacity['diff_pixels']}, RMSE = {metrics_opacity['rmse']:.4f}")
    assert metrics_opacity["diff_pixels"] > 0, "Opacity modification must change visual output"
    print(">>> TRANSPARENCY COMPOSITING VERIFIED: Per-layer alpha correctly affects composite")

    print("\n=== 4. Validating Document Ordering (Bottom-to-Top) ===")
    # Swap layer 4 (Teal) and layer 5 (Orange)
    layers_reordered = build_ten_layers()
    l4 = layers_reordered[4]
    l5 = layers_reordered[5]
    layers_reordered[4] = l5
    layers_reordered[5] = l4
    rendered_reordered = composite_layers(layers_reordered, 512, 512)
    reorder_path = golden_path.parent / "test_reorder.png"
    write_png_rgba(reorder_path, 512, 512, rendered_reordered.buffer)
    _, metrics_reorder = compare_images(reorder_path, golden_path)
    if reorder_path.exists():
        reorder_path.unlink()

    print(f"When layers 4 and 5 swapped: differing pixels = {metrics_reorder['diff_pixels']}, RMSE = {metrics_reorder['rmse']:.4f}")
    assert metrics_reorder["diff_pixels"] > 0, "Swapping layer order must change visual output"
    print(">>> DOCUMENT ORDERING VERIFIED: Bottom-to-top rendering order enforced")

    print("\n=== 5. Validating Save/Reopen Persistence from .ffart ===")
    with zipfile.ZipFile(ffart_path, "r") as zf:
        namelist = zf.namelist()
        assert "mimetype" in namelist, "Missing mimetype entry"
        assert "project.json" in namelist, "Missing project.json entry"
        assert "thumbnail.png" in namelist, "Missing thumbnail.png entry"

        project_raw = zf.read("project.json").decode("utf-8")
        project_data = json.loads(project_raw)

    children = project_data["rootGroup"]["children"]
    assert len(children) == 10, f"Expected 10 layers in persisted project, got {len(children)}"

    expected_ids = [l.id for l in layers]
    actual_ids = [c["id"] for c in children]
    assert actual_ids == expected_ids, f"Layer IDs or ordering changed: {actual_ids} vs {expected_ids}"

    # Verify properties
    for idx, (exp_layer, act_layer) in enumerate(zip(layers, children)):
        assert act_layer["name"] == exp_layer.name, f"Layer {idx} name mismatch"
        assert act_layer["visible"] == exp_layer.visible, f"Layer {idx} visible mismatch"
        assert abs(act_layer["opacity"] - exp_layer.opacity) < 0.001, f"Layer {idx} opacity mismatch"
        assert act_layer["isLocked"] == exp_layer.is_locked, f"Layer {idx} isLocked mismatch"

    # Verify selectedLayerId persistence
    assert project_data.get("selectedLayerId") == "layer-fixture-07", "selectedLayerId not preserved"
    print(f">>> PERSISTENCE VERIFIED: All 10 layers survive save/reopen with stable IDs and ordering")

    print("\nALL 10-LAYER FIXTURE VERIFICATION CHECKS PASSED.")


if __name__ == "__main__":
    test_ten_layer_golden_baseline()
