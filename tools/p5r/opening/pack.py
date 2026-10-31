"""Packs the opening frames rendered by render.py into the app (app/src/main/assets/p5_opening):
per frame a white and a black coverage mask at half resolution, each cropped to what it covers, and
index.json with where every mask goes in the 1920x1080 frame. The app keeps only the masks' alpha
(ALPHA_8 bitmaps) and tints them, so the whole opening costs a few MB of memory.

    python3 tools/p5r/opening/pack.py FRAMES_DIR

Needs Pillow and NumPy.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

FIRST = 125  # params.json's frame number of the menu's first frame (the app's opening frame 0)
COUNT = 18  # silhouette frames; the 2D art cuts in on the next one (Opening.ART in the app)
SCALE = 2  # masks are stored at 1/SCALE of the frame size and stretched back when drawn

OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../../app/src/main/assets/p5_opening"))


def save_mask(coverage: np.ndarray, name: str):
    """Crops coverage (0..1, frame size) to even bounds, halves it, saves it: (index entry, pixels) or None."""
    ys, xs = np.nonzero(coverage > 0.5 / 255)
    if len(xs) == 0:
        return None
    x0, y0 = xs.min() // SCALE * SCALE, ys.min() // SCALE * SCALE
    x1, y1 = -(-(xs.max() + 1) // SCALE) * SCALE, -(-(ys.max() + 1) // SCALE) * SCALE
    alpha = Image.fromarray(np.round(coverage[y0:y1, x0:x1] * 255).astype(np.uint8)).reduce(SCALE)
    Image.merge("LA", (Image.new("L", alpha.size, 255), alpha)).save(f"{OUT}/{name}", optimize=True)
    return {"file": name, "x": int(x0), "y": int(y0)}, alpha.width * alpha.height


def main():
    frames = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for stale in os.listdir(OUT):
        os.remove(os.path.join(OUT, stale))
    index, pixels = {"scale": SCALE, "frames": []}, 0
    for i in range(COUNT):
        rgba = np.asarray(Image.open(f"{frames}/f{FIRST + i:03d}.png").convert("RGBA"), dtype=np.float32) / 255
        cover = np.clip(rgba[..., 3] * 255 / 254, 0, 1)  # full coverage arrives from Blender as 254
        white = rgba[..., :3].mean(-1)  # straight colour: 1 on the white side, 0 on the black side
        entry = {}
        for key, part in (("white", cover * white), ("black", cover * (1 - white))):
            saved = save_mask(part, f"{key[0]}{i:02d}.png")
            entry[key] = saved[0] if saved else None
            pixels += saved[1] if saved else 0
        index["frames"].append(entry)
    with open(f"{OUT}/index.json", "w") as f:
        json.dump(index, f, indent=1)
    print(f"{COUNT} frames -> {OUT}, {pixels / 1e6:.1f} MB as ALPHA_8")


if __name__ == "__main__":
    main()
