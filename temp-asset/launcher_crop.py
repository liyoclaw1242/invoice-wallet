#!/usr/bin/env python3
"""
One-off: take Gemini's 2048² launcher candidates and produce two centred 1024² PNGs
with the subject sized to leave a 22 % margin (Android adaptive icons clip the outer
33 % to circle / squircle / etc.; 22 % keeps the subject comfortably inside the safe
zone after that clip).

Inputs are stable across both candidates: ~cream paper background, a single subject,
some decorative noise (ornaments at top, English / Chinese captions). We:
  1. Detect subject pixels (everything far enough from cream).
  2. Drop the top 12 % and bottom 12 % strips before bbox-ing — that removes the
     "leafy ornament" header on both candidates and the Chinese caption under the
     book. The seal text inside the seal stays (we can't separate it from the seal
     itself; at launcher size 48–108 dp it's illegible anyway).
  3. Auto-trim to the remaining subject bbox.
  4. Pad to square with cream paper to keep the icon background continuous.
  5. Centre on a 1024² canvas with 22 % padding on each side.
"""

import os
import sys

import numpy as np
from PIL import Image

ROOT = "/Users/liyoclaw/Projects/invoice-app"
SRC = "/Users/liyoclaw/Downloads"
OUT = f"{ROOT}/temp-asset/launcher-candidates"
os.makedirs(OUT, exist_ok=True)

BG = (244, 238, 224)  # the cream paper Gemini paints
BG_TOLERANCE = 28


def subject_bbox(img, ignore_top_frac=0.12, ignore_bottom_frac=0.12):
    """Find the bounding box of non-background pixels, ignoring top/bottom decorative bands."""
    arr = np.array(img.convert("RGB"), dtype=np.int32)
    h, w, _ = arr.shape
    bg = np.array(BG)
    dist = np.sqrt(((arr - bg) ** 2).sum(axis=-1))
    mask = dist > BG_TOLERANCE

    top_skip = int(h * ignore_top_frac)
    bot_skip = int(h * ignore_bottom_frac)
    mask[:top_skip, :] = False
    mask[h - bot_skip:, :] = False

    ys, xs = np.where(mask)
    if len(xs) == 0:
        return None
    return int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())


def centre_in_canvas(img, target_size=1024, margin_frac=0.22):
    """Place [img]'s subject centred on a target² square canvas of cream paper."""
    bbox = subject_bbox(img)
    if bbox is None:
        return img.resize((target_size, target_size), Image.LANCZOS).convert("RGB")
    left, top, right, bottom = bbox
    cropped = img.crop((left, top, right + 1, bottom + 1)).convert("RGB")

    cw, ch = cropped.size
    side = max(cw, ch)
    # Square-pad the crop so the subject's aspect doesn't distort when we scale.
    squared = Image.new("RGB", (side, side), BG)
    squared.paste(cropped, ((side - cw) // 2, (side - ch) // 2))

    # Final canvas with the subject occupying (1 - 2*margin) of the side.
    inner = int(target_size * (1 - 2 * margin_frac))
    scaled = squared.resize((inner, inner), Image.LANCZOS)
    out = Image.new("RGB", (target_size, target_size), BG)
    out.paste(scaled, ((target_size - inner) // 2, (target_size - inner) // 2))
    return out


def process(src_name, out_name, *, hard_crop=None):
    """[hard_crop] is an optional (left, top, right, bottom) override before auto-bbox.
    Use it when the auto-bbox pulls the centroid off — e.g. when a long trailing
    ribbon would bias the crop away from the primary disc/object."""
    path = f"{SRC}/{src_name}"
    if not os.path.exists(path):
        print(f"missing: {path}", file=sys.stderr)
        return
    img = Image.open(path)
    if hard_crop is not None:
        img = img.crop(hard_crop)
    out = centre_in_canvas(img)
    out_path = f"{OUT}/{out_name}"
    out.save(out_path, "PNG", optimize=True)
    print(f"  {out_name}  {out.size[0]}x{out.size[1]}  {os.path.getsize(out_path) // 1024} KB")


# Book: auto-bbox works well (subject centred, ornament + caption already stripped
# by the top/bottom ignore bands).
process("Gemini_Generated_Image_q7wm4q7wm4q7wm4q.png", "launcher_book_1024.png")

# Seal: the ribbon trails diagonally to the bottom-right and pulls the auto-bbox off
# the disc itself. Hard-crop to the seal-dominant region first; the trimmed ribbon
# tip still extends from the disc which keeps the character without dragging composition.
process(
    "Gemini_Generated_Image_e4ya3ve4ya3ve4ya.png",
    "launcher_seal_1024.png",
    hard_crop=(640, 620, 1900, 1880),
)

# --- Adaptive-icon foreground (cream paper knocked out to transparent alpha) ---
# Android adaptive icons take a 108-dp foreground; we ship 432² (≈ xxxhdpi). The
# launcher then composites it over the @color/ic_launcher_background tile. Keeping
# the foreground transparent lets the cream background shine through without a
# baked-in cream box, and lets supported launchers parallax the foreground.
FG_OUT = f"{ROOT}/android/app/src/main/res/drawable-nodpi"
os.makedirs(FG_OUT, exist_ok=True)


def knock_out(img, threshold=32, soft_band=18):
    rgba = np.array(img.convert("RGBA"), dtype=np.int32)
    rgb = rgba[..., :3]
    bg = np.array(BG)
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=-1))
    alpha = np.where(
        dist < threshold,
        0.0,
        np.where(dist < threshold + soft_band, (dist - threshold) / soft_band * 255.0, 255.0),
    )
    rgba[..., 3] = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


seal = Image.open(f"{OUT}/launcher_seal_1024.png")
seal_fg = knock_out(seal).resize((432, 432), Image.LANCZOS)
fg_path = f"{FG_OUT}/ic_launcher_foreground.png"
seal_fg.save(fg_path, "PNG", optimize=True)
print(f"\n  ic_launcher_foreground.png  432x432  {os.path.getsize(fg_path) // 1024} KB")
