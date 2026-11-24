"""Draw the launcher icon (a pointing target with ripple rings) at every density."""
import os
import sys
from PIL import Image, ImageDraw

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
out_root = sys.argv[1]

for dens, size in SIZES.items():
    s = size * 4  # supersample for smooth edges
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = s * 0.04
    d.ellipse([pad, pad, s - pad, s - pad], fill=(42, 37, 128, 255))           # indigo disc
    c = s / 2
    for r, w in ((0.33, 0.035), (0.22, 0.035)):                                  # ripple rings
        rr = s * r
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=(45, 212, 191, 200), width=int(s * w))  # teal
    rr = s * 0.10
    d.ellipse([c - rr, c - rr, c + rr, c + rr], fill=(255, 255, 255, 255))      # touch point
    img = img.resize((size, size), Image.LANCZOS)
    folder = os.path.join(out_root, "mipmap-" + dens)
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, "ic_launcher.png"))
print("icons written")
