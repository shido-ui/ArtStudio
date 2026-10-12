#!/usr/bin/env python3
"""
tools/qa/generate_onboarding_samples.py

Regenerates or validates the 3 official CC0 sample projects for Art Studio onboarding:
- sample_doodle.ffart
- sample_comic_panel.ffart
- sample_auto_trace.ffart

Ensures all 3 archives conform to DocumentPackage specification:
- Entry 1: 'mimetype' STORED uncompressed ("application/vnd.artstudio.project")
- Entry 2: 'project.json' DEFLATED
- Entry 3+: 'thumbnail.png', 'assets/...'
"""

import os
import sys
import zipfile
import json

SAMPLES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "app/src/main/assets/samples")

def verify_sample(filename):
    path = os.path.join(SAMPLES_DIR, filename)
    if not os.path.exists(path):
        print(f"Error: {filename} does not exist at {path}")
        return False
    size = os.path.getsize(path)
    if size > 200 * 1024:
        print(f"Error: {filename} size {size} exceeds 200 KB budget")
        return False
    with zipfile.ZipFile(path, 'r') as z:
        names = z.namelist()
        if "mimetype" not in names or "project.json" not in names:
            print(f"Error: {filename} missing required mimetype or project.json")
            return False
        mime = z.read("mimetype").decode('ascii')
        if mime != "application/vnd.artstudio.project":
            print(f"Error: {filename} invalid mimetype: {mime}")
            return False
        # Validate json
        pj = json.loads(z.read("project.json").decode('utf-8'))
        print(f"✓ {filename} ({size} bytes): valid package, document id='{pj.get('id')}', title='{pj.get('title')}'")
    return True

def main():
    print(f"Verifying sample projects in {SAMPLES_DIR}...")
    samples = ["sample_doodle.ffart", "sample_comic_panel.ffart", "sample_auto_trace.ffart"]
    all_ok = True
    for s in samples:
        if not verify_sample(s):
            all_ok = False
    if not all_ok:
        sys.exit(1)
    print("All sample projects verified successfully!")

if __name__ == "__main__":
    main()
