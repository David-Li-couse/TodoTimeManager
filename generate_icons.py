"""Generate launcher icons for all densities: clock + checkmark design."""
import math
import os
from PIL import Image, ImageDraw

# Output directory and sizes
BASE_DIR = r"D:\学习\移动平台开发实践\TodoTimeManager\app\src\main\res"
DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Colors
BG_TOP = (0x1E, 0x88, 0xE5)       # lighter blue
BG_BOTTOM = (0x15, 0x65, 0xC0)    # darker blue
FG_COLOR = (255, 255, 255)         # white


def draw_icon(size, round_corners=False):
    """Draw a clock + checkmark icon at given size."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # --- Background: rounded rect with gradient ---
    # Simulate gradient by drawing two halves
    radius = size * 0.22  # corner radius (~1/4.5 of size)
    half = size // 2

    # Draw full rounded rect with darker blue
    draw.rounded_rectangle(
        [0, 0, size - 1, size - 1],
        radius=int(radius),
        fill=BG_BOTTOM,
    )
    # Draw top half rectangle with lighter blue (overlaid)
    draw.rectangle([0, 0, size - 1, half], fill=BG_TOP)
    # Re-apply rounded corners on top by drawing a rounded rect on the top portion
    # to keep corners smooth
    mask_img = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask_img)
    mask_draw.rounded_rectangle(
        [0, 0, size - 1, size - 1],
        radius=int(radius),
        fill=255,
    )
    # Create the gradient background
    bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)
    bg_draw.rounded_rectangle(
        [0, 0, size - 1, size - 1],
        radius=int(radius),
        fill=BG_BOTTOM,
    )
    bg_draw.rectangle([0, 0, size - 1, half], fill=BG_TOP)
    # Clip to rounded shape
    bg.putalpha(mask_img)

    # --- Foreground drawing ---
    fg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(fg)

    # Scale factors (based on 108dp viewport)
    s = size / 108.0
    cx, cy = 54 * s, 52 * s     # clock center
    r = 30 * s                   # clock radius
    sw_outer = max(1, int(3 * s))      # outer circle stroke
    sw_tick = max(1, int(3 * s))       # tick stroke
    sw_hour = max(1, int(3.5 * s))     # hour hand stroke
    sw_min = max(1, int(2.5 * s))      # minute hand stroke
    sw_check = max(1, int(3.5 * s))    # checkmark stroke

    # Clock pure circle (no ticks)
    bbox = [cx - r, cy - r, cx + r, cy + r]
    fg_draw.ellipse(bbox, outline=FG_COLOR, width=sw_outer)

    # Hour hand (pointing to ~10 o'clock)
    hour_angle = math.radians(-60)  # 10 o'clock = -60° from 12
    hour_len = r * 0.55
    hx = cx + hour_len * math.sin(hour_angle)
    hy = cy - hour_len * math.cos(hour_angle)
    fg_draw.line([cx, cy, hx, hy], fill=FG_COLOR, width=sw_hour)

    # Minute hand (pointing to ~2 o'clock)
    min_angle = math.radians(60)    # 2 o'clock = 60° from 12
    min_len = r * 0.75
    mx = cx + min_len * math.sin(min_angle)
    my = cy - min_len * math.cos(min_angle)
    fg_draw.line([cx, cy, mx, my], fill=FG_COLOR, width=sw_min)

    # Center dot
    dot_r = max(1.5, 3 * s)
    fg_draw.ellipse(
        [cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r],
        fill=FG_COLOR,
    )

    # Composite foreground onto background
    result = Image.alpha_composite(bg, fg)
    return result


for folder, size in DENSITIES.items():
    os.makedirs(os.path.join(BASE_DIR, folder), exist_ok=True)

    # Regular icon
    icon = draw_icon(size)
    out_path = os.path.join(BASE_DIR, folder, "ic_launcher.png")
    icon.save(out_path, "PNG")
    print(f"  {out_path} ({size}x{size})")

    # Round icon (same design, just different filename)
    round_path = os.path.join(BASE_DIR, folder, "ic_launcher_round.png")
    icon.save(round_path, "PNG")
    print(f"  {round_path} ({size}x{size})")

print("\nAll icons generated!")
