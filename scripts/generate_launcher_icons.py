"""
Regenerate Android launcher PNGs from icon.png.

Produces:
  - mipmap-*dpi/ic_launcher.png         square legacy icon
  - mipmap-*dpi/ic_launcher_round.png   round legacy icon (circular mask)
  - mipmap-*dpi/ic_launcher_foreground.png  adaptive icon foreground
    (108dp canvas with inner 66dp safe zone — unicorn padded to fit)

Adaptive icon XMLs already exist in mipmap-anydpi-v26/.
"""
import pathlib
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "icon.png"
RES = ROOT / "android" / "app" / "src" / "main" / "res"

# (folder, legacy_size, adaptive_size)
# Adaptive icon must be 108dp; mdpi=1dp/px → 108, hdpi=1.5 → 162, xhdpi=2 → 216,
# xxhdpi=3 → 324, xxxhdpi=4 → 432.
DENSITIES = [
    ("mipmap-mdpi",    48,  108),
    ("mipmap-hdpi",    72,  162),
    ("mipmap-xhdpi",   96,  216),
    ("mipmap-xxhdpi", 144,  324),
    ("mipmap-xxxhdpi",192,  432),
]

BG_HEX = "#1A1A2E"  # must match @color/launcher_background


def hex_to_rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4))


def composite_on_bg(img: Image.Image, size: int) -> Image.Image:
    """Square legacy icon: paint unicorn onto solid background."""
    out = Image.new("RGBA", (size, size), hex_to_rgb(BG_HEX) + (255,))
    inset = int(size * 0.10)
    fit = size - 2 * inset
    scaled = img.copy()
    scaled.thumbnail((fit, fit), Image.LANCZOS)
    x = (size - scaled.width) // 2
    y = (size - scaled.height) // 2
    out.paste(scaled, (x, y), scaled if scaled.mode == "RGBA" else None)
    return out


def round_icon(img: Image.Image, size: int) -> Image.Image:
    base = composite_on_bg(img, size)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size, size), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(base, (0, 0), mask)
    return out


def adaptive_foreground(img: Image.Image, size: int) -> Image.Image:
    """
    Transparent canvas of adaptive size with unicorn scaled into the
    66dp safe zone (~61% of canvas). System provides background + masks.
    """
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    safe = int(size * 66 / 108)
    scaled = img.copy()
    scaled.thumbnail((safe, safe), Image.LANCZOS)
    x = (size - scaled.width) // 2
    y = (size - scaled.height) // 2
    out.paste(scaled, (x, y), scaled if scaled.mode == "RGBA" else None)
    return out


def main() -> None:
    src = Image.open(SRC).convert("RGBA")
    print(f"source: {SRC} {src.size}")
    for folder, legacy, adaptive in DENSITIES:
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        composite_on_bg(src, legacy).save(out_dir / "ic_launcher.png", "PNG")
        round_icon(src, legacy).save(out_dir / "ic_launcher_round.png", "PNG")
        adaptive_foreground(src, adaptive).save(out_dir / "ic_launcher_foreground.png", "PNG")
        print(f"  {folder}: legacy {legacy}px, adaptive {adaptive}px")


if __name__ == "__main__":
    main()
