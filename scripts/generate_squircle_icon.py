import math, os, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_squircle_icon():
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    canvas_size = 1024
    scale = 2
    S = canvas_size * scale
    margin = S * 0.08
    icon_box = [margin, margin, S - margin, S - margin]
    corner_radius = (S - 2 * margin) * 0.225
    center = (S / 2.0, S / 2.0)
    icon_half_width = (S - 2 * margin) / 2.0
    
    # Squircle parameters
    fol_scale = 0.65
    fol_half_w = icon_half_width * fol_scale
    fol_corner_r = corner_radius * fol_scale
    red_box = [
        center[0] - fol_half_w,
        center[1] - fol_half_w,
        center[0] + fol_half_w,
        center[1] + fol_half_w
    ]

    # Icon base
    icon_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # Shadow
    shadow_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_img)
    s_draw.rounded_rectangle(
        [margin + 8, margin + 25, S - margin - 8, S - margin + 25],
        radius=corner_radius,
        fill=(0, 0, 0, 110)
    )
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(radius=30 * scale))
    icon_img.paste(shadow_img, (0, 0), shadow_img)

    # Content
    mask_icon = Image.new("L", (S, S), 0)
    m_draw = ImageDraw.Draw(mask_icon)
    m_draw.rounded_rectangle(icon_box, radius=corner_radius, fill=255)

    content_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    c_draw = ImageDraw.Draw(content_layer)

    # Blue gradient background (Move Aside)
    for y in range(int(icon_box[1]), int(icon_box[3])):
        prog = (y - icon_box[1]) / (icon_box[3] - icon_box[1])
        r = int(0 * (1 - prog) + 0 * prog)
        g = int(140 * (1 - prog) + 110 * prog)
        b = int(255 * (1 - prog) + 235 * prog)
        c_draw.line([(icon_box[0], y), (icon_box[2], y)], fill=(r, g, b, 255))

    # Red squircle center (Folder Creation)
    red_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    r_draw = ImageDraw.Draw(red_layer)
    r_draw.rounded_rectangle(red_box, radius=fol_corner_r, fill=(255, 59, 48, 255))
    r_draw.rounded_rectangle(red_box, radius=fol_corner_r, outline=(255, 255, 255, 220), width=int(5 * scale))
    content_layer.paste(red_layer, (0, 0), red_layer)

    # Mask to squircle
    masked_icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    masked_icon.paste(content_layer, (0, 0), mask_icon)
    
    # Border
    p_draw = ImageDraw.Draw(masked_icon)
    p_draw.rounded_rectangle(icon_box, radius=corner_radius, outline=(255, 255, 255, 80), width=int(4 * scale))

    # Typography
    f_badge_large = ImageFont.truetype(font_bold_path, 40 * scale)
    f_badge_small = ImageFont.truetype(font_bold_path, 25 * scale)
    f_sub = ImageFont.truetype(font_reg_path, 22 * scale)

    # Folder symbol in center
    fol_w, fol_h = 100 * scale, 70 * scale
    folder_icon_y = center[1] - 80 * scale
    fol_left = center[0] - fol_w / 2
    fol_top = folder_icon_y - fol_h / 2
    p_draw.rounded_rectangle([fol_left, fol_top, fol_left + fol_w * 0.45, fol_top + fol_h * 0.3], radius=6*scale, fill=(255, 255, 255, 245))
    p_draw.rounded_rectangle([fol_left, fol_top + fol_h * 0.15, fol_left + fol_w, fol_top + fol_h], radius=10*scale, fill=(255, 255, 255, 245))
    p_cx, p_cy = center[0], fol_top + fol_h * 0.58
    p_s = 14 * scale
    p_draw.line([(p_cx - p_s, p_cy), (p_cx + p_s, p_cy)], fill=(255, 59, 48, 255), width=int(4*scale))
    p_draw.line([(p_cx, p_cy - p_s), (p_cx, p_cy + p_s)], fill=(255, 59, 48, 255), width=int(4*scale))

    text_red_1 = "FOLDER CREATION"
    text_red_2 = "DROP HERE (SQUIRCLE)"
    b1 = p_draw.textbbox((0, 0), text_red_1, font=f_badge_large)
    b2 = p_draw.textbbox((0, 0), text_red_2, font=f_sub)
    w1 = b1[2] - b1[0]
    w2 = b2[2] - b2[0]
    p_draw.text((center[0] - w1 / 2, center[1] + 15 * scale), text_red_1, font=f_badge_large, fill=(255, 255, 255, 255))
    p_draw.text((center[0] - w2 / 2, center[1] + 65 * scale), text_red_2, font=f_sub, fill=(255, 255, 255, 220))

    # Top & Bottom "MOVE ASIDE (REORDER)"
    text_blue = "MOVE ASIDE (REORDER)"
    bb = p_draw.textbbox((0, 0), text_blue, font=f_badge_small)
    wb = bb[2] - bb[0]
    p_draw.text((center[0] - wb / 2, icon_box[1] + 35 * scale), text_blue, font=f_badge_small, fill=(255, 255, 255, 240))
    p_draw.text((center[0] - wb / 2, icon_box[3] - 65 * scale), text_blue, font=f_badge_small, fill=(255, 255, 255, 240))

    # Corner 45-degree ticks
    def draw_corner_tick(cx_a, cy_a, angle_deg):
        rad = math.radians(angle_deg)
        dist = 22 * scale
        dx, dy = math.cos(rad) * dist, math.sin(rad) * dist
        p_draw.line([(cx_a - dx, cy_a - dy), (cx_a + dx, cy_a + dy)], fill=(255, 255, 255, 220), width=int(4 * scale))

    pad = 70 * scale
    draw_corner_tick(icon_box[0] + pad, icon_box[1] + pad, 225)
    draw_corner_tick(icon_box[2] - pad, icon_box[1] + pad, 315)
    draw_corner_tick(icon_box[0] + pad, icon_box[3] - pad, 135)
    draw_corner_tick(icon_box[2] - pad, icon_box[3] - pad, 45)

    icon_img.paste(masked_icon, (0, 0), masked_icon)
    final_icon = icon_img.resize((canvas_size, canvas_size), Image.Resampling.LANCZOS)
    
    out_path = r"d:\Code\OS_Launcher\app_icon_squircle_zones.png"
    final_icon.save(out_path, "PNG")
    print(f"Saved {out_path}")

    artifact_dir = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"
    shutil.copy(out_path, os.path.join(artifact_dir, "app_icon_squircle_zones.png"))
    print("Copied to artifacts")

if __name__ == "__main__":
    create_squircle_icon()
