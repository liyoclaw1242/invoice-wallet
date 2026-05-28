#!/usr/bin/env python3
"""
One-off: turn the raw 2048² Gemini PNGs into trimmed, named WebP drawables.

Outputs go to android/app/src/main/res/drawable-nodpi/ — no density bucket means
Android uses the asset at its raw pixel size regardless of screen density; we
control display size in Compose via Modifier.size().

Originals stay in temp-asset/ (gitignored). Re-run any time prompts change.
"""

import os
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = "/Users/liyoclaw/Projects/invoice-app"
SRC = f"{ROOT}/temp-asset"
DST = f"{ROOT}/android/core/design-system/src/main/res/drawable-nodpi"
os.makedirs(DST, exist_ok=True)

# Mean paper-cream colour of the canvas — close to #F4EEE0 but the watercolour
# texture varies, so we use a generous tolerance below.
BG = (244, 238, 224)


def auto_trim(img, tolerance=28, pad=24):
    """Crop near-background margins. Pads back a little so subjects don't kiss the edge."""
    rgb = img.convert("RGB")
    bg = Image.new("RGB", rgb.size, BG)
    diff = ImageChops.difference(rgb, bg).convert("L")
    mask = diff.point(lambda v: 255 if v > tolerance else 0)
    bbox = mask.getbbox()
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


def to_square(img, bg=BG):
    """Pad to a square canvas — keeps composable size logic simple."""
    w, h = img.size
    s = max(w, h)
    out = Image.new("RGB", (s, s), bg)
    out.paste(img, ((s - w) // 2, (s - h) // 2))
    return out


def save_webp(img, name, max_dim, quality):
    if max(img.size) > max_dim:
        img.thumbnail((max_dim, max_dim), Image.LANCZOS)
    path = f"{DST}/{name}.webp"
    img.save(path, "WEBP", quality=quality, method=6)
    print(f"  {name:32s} {img.size[0]}x{img.size[1]}  {os.path.getsize(path) // 1024} KB")


# --- 1. Standard full illustrations (auto-trim + square + 1024 WebP q=82) ---

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
    img = Image.open(f"{SRC}/{src}")
    save_webp(to_square(auto_trim(img)), name, max_dim=1024, quality=82)

# --- 2. Streak flame icon — tighter, smaller, higher q for small UI use ---

print("\nflame icon:")
img = Image.open(f"{SRC}/Gemini_Generated_Image_ibmcl7ibmcl7ibmc.png")
save_webp(to_square(auto_trim(img, tolerance=22, pad=12)), "ic_streak_flame", max_dim=512, quality=88)

# --- 3. Pairing: Gemini sprinkled English annotation labels ("tin-can
# communication" / "intentional connection") across the empty space *and* with
# arrows pointing into the cans. Subjects and labels are too interwoven to clean
# up programmatically. Regenerate with a stronger anti-text prompt — see the
# README in this folder for the suggested wording.

print("\npairing: SKIPPED (Gemini added English labels intertwined with subjects — regenerate).")

# --- 4. Category icons: split the 3×3 grid into 8 cells (centre is decorative). ---

print("\ncategory icons (split from 3×3 grid):")
grid = Image.open(f"{SRC}/Gemini_Generated_Image_mvmajjmvmajjmvma.png")
gw, gh = grid.size
# Grid spans roughly x ∈ [4%, 96%], y ∈ [8%, 96%].
gx0, gy0 = int(0.04 * gw), int(0.08 * gh)
gx1, gy1 = int(0.96 * gw), int(0.96 * gh)
cell_w = (gx1 - gx0) // 3
cell_h = (gy1 - gy0) // 3

# (col, row) addressing inside the grid; (1, 1) is the decorative centre and skipped.
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
for slug, cx, cy in CATEGORIES:
    x0 = gx0 + cx * cell_w
    y0 = gy0 + cy * cell_h
    cell = grid.crop((x0, y0, x0 + cell_w, y0 + cell_h))
    # Drop the bottom 25 % where the Chinese label sits — we'll show labels in Compose.
    cw, ch = cell.size
    illust = cell.crop((0, 0, cw, int(ch * 0.75)))
    illust = auto_trim(illust, tolerance=28, pad=10)
    save_webp(to_square(illust), f"ic_category_{slug}", max_dim=256, quality=88)

print("\ndone")
