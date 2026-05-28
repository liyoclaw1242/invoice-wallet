#!/usr/bin/env python3
"""
One-off: turn the raw 2048² Gemini PNGs into shippable WebPs.

Pipeline per illustration:
  load → knock paper out to transparent (soft-edge alpha) → auto-trim alpha
  → pad to square (transparent) → resize → WebP with alpha

The cream-paper background Gemini bakes into each illustration was visible as
a "card" rectangle against the app's surface colour (#EFEAE0). Transparent
alpha lets the illustration sit on any surface seamlessly.

Outputs land in android/core/design-system/src/main/res/drawable-nodpi/ so
any feature module can `import tw.invoicewallet.core.designsystem.R`.

Originals stay in temp-asset/ (gitignored). Re-run any time prompts change.
"""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = "/Users/liyoclaw/Projects/invoice-app"
SRC = f"{ROOT}/temp-asset"
DST = f"{ROOT}/android/core/design-system/src/main/res/drawable-nodpi"
os.makedirs(DST, exist_ok=True)

# Mean paper-cream colour of Gemini's canvas — close to #F4EEE0 but the
# watercolour texture varies, so we threshold within a band rather than
# matching exactly. (The same constant doubles as the "paint over" colour
# for the pairing-image label cleanup below.)
BG = (244, 238, 224)


def knock_out_paper(img, threshold=38, soft_band=22):
    """
    Returns an RGBA copy with paper-coloured pixels turned transparent.

    Pixels whose RGB distance from [BG] is below [threshold] become fully
    transparent. Pixels in the soft band [threshold, threshold + soft_band]
    get a linearly-interpolated alpha, giving a feathered edge rather than
    a jagged cut. Painted pixels stay fully opaque.
    """
    rgba = np.array(img.convert("RGBA"), dtype=np.int32)
    rgb = rgba[..., :3]
    bg = np.array(BG, dtype=np.int32)
    dist = np.sqrt(((rgb - bg) ** 2).sum(axis=-1))

    alpha = np.where(
        dist < threshold,
        0.0,
        np.where(
            dist < threshold + soft_band,
            (dist - threshold) / soft_band * 255.0,
            255.0,
        ),
    )
    rgba[..., 3] = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")


def auto_trim(img, pad=24):
    """Trim transparent margins (alpha == 0) with a few px of breathing room."""
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    bbox = img.split()[-1].getbbox()  # alpha channel bbox
    if bbox is None:
        return img
    w, h = img.size
    left, top, right, bottom = bbox
    return img.crop(
        (
            max(0, left - pad),
            max(0, top - pad),
            min(w, right + pad),
            min(h, bottom + pad),
        )
    )


def to_square(img):
    """Pad to a square canvas with transparent pixels."""
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    w, h = img.size
    s = max(w, h)
    out = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    out.paste(img, ((s - w) // 2, (s - h) // 2), img)
    return out


def save_webp(img, name, max_dim, quality):
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    if max(img.size) > max_dim:
        img.thumbnail((max_dim, max_dim), Image.LANCZOS)
    path = f"{DST}/{name}.webp"
    img.save(path, "WEBP", quality=quality, method=6, exact=True)
    print(f"  {name:32s} {img.size[0]}x{img.size[1]}  {os.path.getsize(path) // 1024} KB")


def process(src, name, max_dim=1024, quality=82):
    img = Image.open(f"{SRC}/{src}")
    out = to_square(auto_trim(knock_out_paper(img)))
    save_webp(out, name, max_dim=max_dim, quality=quality)


# --- 1. Standard full illustrations ---

STANDARD = {
    "Gemini_Generated_Image_r7ughkr7ughkr7ug.png": "illust_onboarding_local",
    "Gemini_Generated_Image_fsbrtkfsbrtkfsbr.png": "illust_onboarding_scan",
    "Gemini_Generated_Image_l4oeq3l4oeq3l4oe.png": "illust_onboarding_csv",
    "Gemini_Generated_Image_worxz3worxz3worx.png": "illust_onboarding_ai",
    "Gemini_Generated_Image_6zj7ax6zj7ax6zj7.png": "illust_empty_list",
    "Gemini_Generated_Image_xctwvxctwvxctwvx.png": "illust_lottery_pending",
    "Gemini_Generated_Image_1811161811161811.png": "illust_lottery_won",
    "Gemini_Generated_Image_8x2nf58x2nf58x2n.png": "illust_lottery_noprize",
}

print("standard illustrations:")
for src, name in STANDARD.items():
    process(src, name)

# --- 2. Streak flame icon — small, tighter quality ---

print("\nflame icon:")
process("Gemini_Generated_Image_ibmcl7ibmcl7ibmc.png", "ic_streak_flame", max_dim=512, quality=88)

# --- 3. Pairing — SKIPPED: Gemini ignored "no text" and added English
# annotation labels intertwined with the subjects. Regenerate with the
# stronger anti-text prompt in this folder's README.

print("\npairing: SKIPPED (Gemini added English labels intertwined with subjects — regenerate).")

# --- 4. Category icons: split the 3×3 grid into 8 cells (centre is decorative).

print("\ncategory icons (split from 3×3 grid):")
grid = Image.open(f"{SRC}/Gemini_Generated_Image_mvmajjmvmajjmvma.png")
gw, gh = grid.size
gx0, gy0 = int(0.04 * gw), int(0.08 * gh)
gx1, gy1 = int(0.96 * gw), int(0.96 * gh)
cell_w = (gx1 - gx0) // 3
cell_h = (gy1 - gy0) // 3

# (col, row) — (1, 1) is the decorative centre, skipped.
CATEGORIES = [
    ("food",      0, 0),
    ("drink",     1, 0),
    ("convstore", 2, 0),
    ("tech",      0, 1),
    ("medical",   2, 1),
    ("transit",   0, 2),
    ("clothing",  1, 2),
    ("home",      2, 2),
]
# Each cell has its own cream tone — the watercolour paper subtly drifts from the
# canvas-wide BG, so we sample THIS cell's corner-pixel cream and knock that out
# instead of relying on the global constant. 9 % inset trims the pencil rectangle
# drawn around each tile; bottom 32 % drops the Chinese caption.
def _knock_out_with_sampled_bg(img, sample_box, threshold=22, soft_band=18):
    """Like knock_out_paper but uses the median colour of [sample_box] as BG.
    sample_box = (left, top, right, bottom) in img coords."""
    arr = np.array(img.convert("RGBA"), dtype=np.int32)
    sample = arr[sample_box[1]:sample_box[3], sample_box[0]:sample_box[2], :3]
    bg = np.median(sample.reshape(-1, 3), axis=0)
    dist = np.sqrt(((arr[..., :3] - bg) ** 2).sum(axis=-1))
    alpha = np.where(
        dist < threshold,
        0.0,
        np.where(dist < threshold + soft_band, (dist - threshold) / soft_band * 255.0, 255.0),
    )
    arr[..., 3] = np.clip(alpha, 0, 255).astype(np.uint8)
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


for slug, cx, cy in CATEGORIES:
    x0 = gx0 + cx * cell_w
    y0 = gy0 + cy * cell_h
    cell = grid.crop((x0, y0, x0 + cell_w, y0 + cell_h))
    cw, ch = cell.size
    inset_x = int(cw * 0.09)
    inset_y = int(ch * 0.09)
    illust = cell.crop((inset_x, inset_y, cw - inset_x, int(ch * 0.68)))
    iw, ih = illust.size
    # Sample a corner patch — corners reliably contain only the cell's cream paper.
    sample_box = (0, 0, max(10, iw // 12), max(10, ih // 12))
    transparent = _knock_out_with_sampled_bg(illust, sample_box, threshold=22, soft_band=18)
    save_webp(to_square(auto_trim(transparent)), f"ic_category_{slug}", max_dim=256, quality=88)

# Silence the unused-import warnings — these are kept available for ad-hoc
# label cleanup runs (see README for the pairing-image scenario).
_ = (ImageDraw, ImageFilter)

print("\ndone")
