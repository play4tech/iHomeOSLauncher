import os
import subprocess
from PIL import Image, ImageDraw

svg_path = r"C:\Users\lavan\Downloads\launcher-icon-pill.svg"
chrome_exe = r"C:\Program Files\Google\Chrome\Application\chrome.exe"
project_root = r"D:\Code\OS_Launcher"
artifact_dir = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"
scratch_dir = os.path.join(artifact_dir, "scratch")

os.makedirs(scratch_dir, exist_ok=True)

with open(svg_path, 'r', encoding='utf-8') as f:
    full_svg = f.read()

defs_start = full_svg.find("<defs>")
defs_end = full_svg.find("</defs>") + 7
defs_block = full_svg[defs_start:defs_end]

fg_start = full_svg.find("<g transform=\"translate(67.668")
fg_end = full_svg.rfind("</svg>")
fg_block = full_svg[fg_start:fg_end]

bg_svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
{defs_block}
  <rect width="512" height="512" fill="url(#bg)"/>
</svg>"""

fg_svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">
{defs_block}
{fg_block}
</svg>"""

def render_html_to_png(svg_content, out_png, size=1024, transparent=False):
    html = f"""<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  html, body {{ width: {size}px; height: {size}px; overflow: hidden; background: {"transparent !important" if transparent else "white"}; }}
  svg {{ width: {size}px; height: {size}px; display: block; }}
</style>
</head>
<body>
{svg_content}
</body>
</html>"""
    temp_html = os.path.join(scratch_dir, f"temp_{os.path.basename(out_png)}.html")
    with open(temp_html, 'w', encoding='utf-8') as f:
        f.write(html)
    
    cmd = [
        chrome_exe,
        "--headless",
        "--disable-gpu",
        "--hide-scrollbars",
        f"--window-size={size},{size}",
        f"--screenshot={out_png}",
    ]
    if transparent:
        cmd.append("--default-background-color=00000000")
    cmd.append(f"file:///{temp_html.replace(os.sep, '/')}")
    subprocess.run(cmd, check=True, capture_output=True)

out_bg_1024 = os.path.join(scratch_dir, "master_bg_1024.png")
out_fg_1024 = os.path.join(scratch_dir, "master_fg_1024.png")
out_full_1024 = os.path.join(scratch_dir, "master_full_1024.png")

print("Rendering 1024x1024 master images via Chrome...")
render_html_to_png(bg_svg, out_bg_1024, size=1024, transparent=False)
render_html_to_png(fg_svg, out_fg_1024, size=1024, transparent=True)
render_html_to_png(full_svg, out_full_1024, size=1024, transparent=False)

img_bg = Image.open(out_bg_1024).convert("RGB")
img_fg = Image.open(out_fg_1024).convert("RGBA")
img_full = Image.open(out_full_1024).convert("RGBA")

# Build legacy safe-zone masked masters
safe_size = 1024.0 * (72.0 / 108.0) # 682.67 px
half_safe = safe_size / 2.0
center = 512.0
crop_box = (
    int(round(center - half_safe)),
    int(round(center - half_safe)),
    int(round(center + half_safe)),
    int(round(center + half_safe))
)
safe_img = img_full.crop(crop_box)
safe_w, safe_h = safe_img.size

# Circular mask
mask_circle = Image.new("L", (safe_w, safe_h), 0)
draw_c = ImageDraw.Draw(mask_circle)
draw_c.ellipse((0, 0, safe_w - 1, safe_h - 1), fill=255)
circle_master = Image.new("RGBA", (safe_w, safe_h), (0, 0, 0, 0))
circle_master.paste(safe_img, (0, 0), mask_circle)

# Squircle mask (corner radius ~22%)
mask_squircle = Image.new("L", (safe_w, safe_h), 0)
draw_s = ImageDraw.Draw(mask_squircle)
corner_r = int(safe_w * 0.22)
draw_s.rounded_rectangle((0, 0, safe_w - 1, safe_h - 1), radius=corner_r, fill=255)
squircle_master = Image.new("RGBA", (safe_w, safe_h), (0, 0, 0, 0))
squircle_master.paste(safe_img, (0, 0), mask_squircle)

def make_legacy_icon(masked_hi, canvas_size):
    inner_size = int(round(canvas_size * (42.0 / 48.0)))
    scaled = masked_hi.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    offset = (canvas_size - inner_size) // 2
    canvas.paste(scaled, (offset, offset), scaled)
    return canvas

# Density configurations
densities = {
    'mdpi': {'adaptive': 108, 'legacy': 48},
    'hdpi': {'adaptive': 162, 'legacy': 72},
    'xhdpi': {'adaptive': 216, 'legacy': 96},
    'xxhdpi': {'adaptive': 324, 'legacy': 144},
    'xxxhdpi': {'adaptive': 432, 'legacy': 192},
}

target_roots = [
    os.path.join(project_root, 'bliss', 'res_overrides'),
    os.path.join(project_root, 'res'),
]

for target_root in target_roots:
    print(f"\nProcessing target root: {target_root}")
    for density, sizes in densities.items():
        adaptive_sz = sizes['adaptive']
        legacy_sz = sizes['legacy']
        
        # 1. Drawable (adaptive layers)
        drawable_dir = os.path.join(target_root, f"drawable-{density}")
        os.makedirs(drawable_dir, exist_ok=True)
        
        bg_img = img_bg.resize((adaptive_sz, adaptive_sz), Image.Resampling.LANCZOS)
        bg_path = os.path.join(drawable_dir, "ic_launcher_background.png")
        bg_img.save(bg_path, "PNG")
        
        fg_img = img_fg.resize((adaptive_sz, adaptive_sz), Image.Resampling.LANCZOS)
        fg_path = os.path.join(drawable_dir, "ic_launcher_foreground.png")
        fg_img.save(fg_path, "PNG")
        
        # 2. Mipmap (legacy icons)
        mipmap_dir = os.path.join(target_root, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)
        
        icon_img = make_legacy_icon(squircle_master, legacy_sz)
        icon_path = os.path.join(mipmap_dir, "ic_launcher.png")
        icon_img.save(icon_path, "PNG")
        
        round_img = make_legacy_icon(circle_master, legacy_sz)
        round_path = os.path.join(mipmap_dir, "ic_launcher_round.png")
        round_img.save(round_path, "PNG")
        
        print(f"  [{density}] Adaptive: {adaptive_sz}x{adaptive_sz}, Legacy: {legacy_sz}x{legacy_sz}")

# Ensure XML adaptive icon declarations in bliss/res_overrides
adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

for xml_dir in ["mipmap-anydpi", "mipmap-anydpi-v26"]:
    dir_path = os.path.join(project_root, "bliss", "res_overrides", xml_dir)
    os.makedirs(dir_path, exist_ok=True)
    with open(os.path.join(dir_path, "ic_launcher.xml"), 'w', encoding='utf-8') as f:
        f.write(adaptive_xml)
    with open(os.path.join(dir_path, "ic_launcher_round.xml"), 'w', encoding='utf-8') as f:
        f.write(adaptive_xml)

# Export 512x512 store/web icon
web_icon = img_full.resize((512, 512), Image.Resampling.LANCZOS)
web_icon.save(os.path.join(project_root, "bliss", "ic_launcher-web.png"), "PNG")
web_icon.save(os.path.join(artifact_dir, "new_app_icon_512.png"), "PNG")

print("\nApp icons successfully generated and installed in project resources!")
