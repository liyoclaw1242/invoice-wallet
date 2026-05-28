# temp-asset/

Source PNGs from Gemini's image generator. **Big** (≈7 MB × 11) — `.gitignore`'d.
The processed WebPs in `android/core/design-system/src/main/res/drawable-nodpi/`
are what actually ships — they live in the design-system module so any feature
can `import tw.invoicewallet.core.designsystem.R`.

## Re-running

```bash
python3 temp-asset/process.py
```

Auto-trims paper margins → pads to square → resizes (1024 / 512 / 256) → WebP
(q=82 or q=88 for icons). Splits the 3×3 category grid into 8 individual cells.

## Outputs

| Source PNG | → WebP drawable |
|---|---|
| `Gemini_…_r7ughk…` | `illust_onboarding_local.webp` |
| `Gemini_…_fsbrtk…` | `illust_onboarding_scan.webp` |
| `Gemini_…_l4oeq3…` | `illust_onboarding_csv.webp` |
| `Gemini_…_worxz3…` | `illust_onboarding_ai.webp` |
| `Gemini_…_6zj7ax…` | `illust_empty_list.webp` |
| `Gemini_…_xctwvx…` | `illust_lottery_pending.webp` |
| `Gemini_…_181116…` | `illust_lottery_won.webp` |
| `Gemini_…_8x2nf5…` | `illust_lottery_noprize.webp` |
| `Gemini_…_ibmcl7…` | `ic_streak_flame.webp` |
| `Gemini_…_mvmajj…` (3×3 grid) | `ic_category_food / drink / convstore / tech / medical / transit / clothing / home.webp` |
| `Gemini_…_8mxa5v…` (pairing) | **SKIPPED — needs regeneration** (see below) |

## Pairing — needs regeneration

Gemini ignored the "no text" instruction and added italic English annotation
labels (`tin-can communication`, `intentional connection`) with arrows pointing
into the subjects. Subjects and labels overlap; trimming or painting over
either damages the other.

Suggested re-prompt (extra anti-text guards):

```
Two old metal tin cans connected by a single taut twine string, drawn in soft
watercolor with pencil outlines on warm cream paper. One can has a small
QR-code label glued to its side. The cans sit on a softly painted wooden
plank. Composition is minimal and quiet. **NO text, NO words, NO letters,
NO annotation labels, NO arrows, NO captions, NO writing of any kind on or
around the subjects.** Centered subject with generous negative space on all
sides. Palette restricted to warm cream paper background, dusty sage-teal
accents, and warm coral accents — three colors only. NOT 3D, NOT cartoon,
NOT glossy. Square 2048×2048.
```

Drop the resulting PNG into this folder and re-run `process.py`.
