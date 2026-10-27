import math, os, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_images():
    # Fonts
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    font_huge = ImageFont.truetype(font_bold_path, 46)
    font_large_bold = ImageFont.truetype(font_bold_path, 32)
    font_large_reg = ImageFont.truetype(font_reg_path, 30)
    font_med_bold = ImageFont.truetype(font_bold_path, 26)
    font_med_reg = ImageFont.truetype(font_reg_path, 24)
    font_small = ImageFont.truetype(font_reg_path, 20)
    font_badge = ImageFont.truetype(font_bold_path, 18)
    font_mono = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf" if os.path.exists(r"C:\Windows\Fonts\consola.ttf") else font_reg_path, 20)

    canvas_size = 1024
    scale = 2
    S = canvas_size * scale
    margin = S * 0.08
    icon_box = [margin, margin, S - margin, S - margin]
    corner_radius = (S - 2 * margin) * 0.225
    center = (S / 2.0, S / 2.0)
    icon_half_width = (S - 2 * margin) / 2.0
    folder_radius = icon_half_width * 0.65

    # =========================================================================
    # A. GENERATE app_icon_zones_pure.png (Completely Clean Icon Asset)
    # =========================================================================
    pure_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # Shadow
    shadow_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_img)
    s_draw.rounded_rectangle(
        [margin + 8, margin + 25, S - margin - 8, S - margin + 25],
        radius=corner_radius,
        fill=(0, 0, 0, 110)
    )
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(radius=30 * scale))
    pure_img.paste(shadow_img, (0, 0), shadow_img)

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

    # Red central circle (Folder Creation)
    red_box = [
        center[0] - folder_radius,
        center[1] - folder_radius,
        center[0] + folder_radius,
        center[1] + folder_radius
    ]
    red_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    r_draw = ImageDraw.Draw(red_layer)
    r_draw.ellipse(red_box, fill=(255, 59, 48, 255))
    r_draw.ellipse(red_box, outline=(255, 255, 255, 220), width=int(5 * scale))
    content_layer.paste(red_layer, (0, 0), red_layer)

    # Mask to squircle
    masked_pure = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    masked_pure.paste(content_layer, (0, 0), mask_icon)
    
    # Border
    p_draw = ImageDraw.Draw(masked_pure)
    p_draw.rounded_rectangle(icon_box, radius=corner_radius, outline=(255, 255, 255, 80), width=int(4 * scale))
    pure_img.paste(masked_pure, (0, 0), masked_pure)

    final_pure = pure_img.resize((canvas_size, canvas_size), Image.Resampling.LANCZOS)
    out_pure_path = r"d:\Code\OS_Launcher\app_icon_zones_pure.png"
    final_pure.save(out_pure_path, "PNG")
    print(f"Saved {out_pure_path}")

    # =========================================================================
    # B. GENERATE app_icon_zones_only.png (Labeled Icon Asset)
    # =========================================================================
    icon_labeled = pure_img.copy()
    l_draw = ImageDraw.Draw(icon_labeled)

    f_badge_large = ImageFont.truetype(font_bold_path, 40 * scale)
    f_badge_small = ImageFont.truetype(font_bold_path, 25 * scale)
    f_sub = ImageFont.truetype(font_reg_path, 22 * scale)

    # Folder symbol in center
    fol_w, fol_h = 100 * scale, 70 * scale
    folder_icon_y = center[1] - 80 * scale
    fol_left = center[0] - fol_w / 2
    fol_top = folder_icon_y - fol_h / 2
    l_draw.rounded_rectangle([fol_left, fol_top, fol_left + fol_w * 0.45, fol_top + fol_h * 0.3], radius=6*scale, fill=(255, 255, 255, 245))
    l_draw.rounded_rectangle([fol_left, fol_top + fol_h * 0.15, fol_left + fol_w, fol_top + fol_h], radius=10*scale, fill=(255, 255, 255, 245))
    p_cx, p_cy = center[0], fol_top + fol_h * 0.58
    p_s = 14 * scale
    l_draw.line([(p_cx - p_s, p_cy), (p_cx + p_s, p_cy)], fill=(255, 59, 48, 255), width=int(4*scale))
    l_draw.line([(p_cx, p_cy - p_s), (p_cx, p_cy + p_s)], fill=(255, 59, 48, 255), width=int(4*scale))

    text_red_1 = "FOLDER CREATION"
    text_red_2 = "DROP HERE"
    b1 = l_draw.textbbox((0, 0), text_red_1, font=f_badge_large)
    b2 = l_draw.textbbox((0, 0), text_red_2, font=f_sub)
    w1 = b1[2] - b1[0]
    w2 = b2[2] - b2[0]
    l_draw.text((center[0] - w1 / 2, center[1] + 15 * scale), text_red_1, font=f_badge_large, fill=(255, 255, 255, 255))
    l_draw.text((center[0] - w2 / 2, center[1] + 65 * scale), text_red_2, font=f_sub, fill=(255, 255, 255, 220))

    # Top & Bottom "MOVE ASIDE (REORDER)"
    text_blue = "MOVE ASIDE (REORDER)"
    bb = l_draw.textbbox((0, 0), text_blue, font=f_badge_small)
    wb = bb[2] - bb[0]
    l_draw.text((center[0] - wb / 2, icon_box[1] + 35 * scale), text_blue, font=f_badge_small, fill=(255, 255, 255, 240))
    l_draw.text((center[0] - wb / 2, icon_box[3] - 65 * scale), text_blue, font=f_badge_small, fill=(255, 255, 255, 240))

    # Left & Right push arrows and indicator
    side_text = "MOVE ASIDE"
    f_side = ImageFont.truetype(font_bold_path, 20 * scale)
    sb = l_draw.textbbox((0, 0), side_text, font=f_side)
    sw = sb[2] - sb[0]
    
    # Left arrow
    l_draw.line([(icon_box[0] + 35 * scale, center[1]), (icon_box[0] + 65 * scale, center[1])], fill=(255, 255, 255, 230), width=int(4*scale))
    l_draw.line([(icon_box[0] + 35 * scale, center[1]), (icon_box[0] + 48 * scale, center[1] - 13*scale)], fill=(255, 255, 255, 230), width=int(4*scale))
    l_draw.line([(icon_box[0] + 35 * scale, center[1]), (icon_box[0] + 48 * scale, center[1] + 13*scale)], fill=(255, 255, 255, 230), width=int(4*scale))

    # Right arrow
    l_draw.line([(icon_box[2] - 65 * scale, center[1]), (icon_box[2] - 35 * scale, center[1])], fill=(255, 255, 255, 230), width=int(4*scale))
    l_draw.line([(icon_box[2] - 35 * scale, center[1]), (icon_box[2] - 48 * scale, center[1] - 13*scale)], fill=(255, 255, 255, 230), width=int(4*scale))
    l_draw.line([(icon_box[2] - 35 * scale, center[1]), (icon_box[2] - 48 * scale, center[1] + 13*scale)], fill=(255, 255, 255, 230), width=int(4*scale))

    # Corner 45-degree ticks
    def draw_corner_tick(cx_a, cy_a, angle_deg):
        rad = math.radians(angle_deg)
        dist = 22 * scale
        dx, dy = math.cos(rad) * dist, math.sin(rad) * dist
        l_draw.line([(cx_a - dx, cy_a - dy), (cx_a + dx, cy_a + dy)], fill=(255, 255, 255, 220), width=int(4 * scale))

    pad = 70 * scale
    draw_corner_tick(icon_box[0] + pad, icon_box[1] + pad, 225)
    draw_corner_tick(icon_box[2] - pad, icon_box[1] + pad, 315)
    draw_corner_tick(icon_box[0] + pad, icon_box[3] - pad, 135)
    draw_corner_tick(icon_box[2] - pad, icon_box[3] - pad, 45)

    final_labeled = icon_labeled.resize((canvas_size, canvas_size), Image.Resampling.LANCZOS)
    out_labeled_path = r"d:\Code\OS_Launcher\app_icon_zones_only.png"
    final_labeled.save(out_labeled_path, "PNG")
    print(f"Saved {out_labeled_path}")

    # =========================================================================
    # C. GENERATE app_icon_trigger_zones.png (Full Infographic Presentation)
    # =========================================================================
    img_w, img_h = 1600, 1100
    info_img = Image.new("RGBA", (img_w, img_h), (20, 21, 24, 255))
    guide_draw = ImageDraw.Draw(info_img)

    # Top Header Banner
    guide_draw.text((80, 50), "App Icon Drag & Drop Trigger Zones", font=font_huge, fill=(255, 255, 255, 255))
    guide_draw.text((80, 110), "Launcher3 Architecture: Folder Creation (Red) vs. Move Aside Reorder (Blue)", font=font_med_reg, fill=(160, 165, 175, 255))
    guide_draw.line([(80, 155), (img_w - 80, 155)], fill=(45, 48, 56, 255), width=2)

    # Place the labeled icon on the left half
    icon_display_size = 580
    scaled_icon = final_labeled.resize((icon_display_size, icon_display_size), Image.Resampling.LANCZOS)
    icon_pos_x = 80
    icon_pos_y = 230
    info_img.paste(scaled_icon, (icon_pos_x, icon_pos_y), scaled_icon)

    ic_center_x = icon_pos_x + icon_display_size // 2
    ic_center_y = icon_pos_y + icon_display_size // 2

    # Dimension & Target Guide Lines on the icon
    fol_disp_r = int((icon_display_size * (1 - 2 * 0.08) / 2.0) * 0.65)
    # Radius dimension line inside red circle
    guide_draw.line([(ic_center_x, ic_center_y), (ic_center_x + fol_disp_r, ic_center_y)], fill=(255, 255, 255, 240), width=2)
    guide_draw.ellipse([ic_center_x - 4, ic_center_y - 4, ic_center_x + 4, ic_center_y + 4], fill=(255, 255, 255, 255))
    guide_draw.ellipse([ic_center_x + fol_disp_r - 4, ic_center_y - 4, ic_center_x + fol_disp_r + 4, ic_center_y + 4], fill=(255, 255, 255, 255))
    guide_draw.text((ic_center_x + fol_disp_r // 4, ic_center_y - 28), "r_folder", font=font_badge, fill=(255, 255, 255, 240))

    card_x = 730
    card_w = 790

    # -------------------------------------------------------------
    # CARD 1: RED ZONE - FOLDER CREATION
    # -------------------------------------------------------------
    c1_y = 190
    c1_h = 370
    guide_draw.rounded_rectangle([card_x, c1_y, card_x + card_w, c1_y + c1_h], radius=20, fill=(30, 31, 36, 255), outline=(255, 59, 48, 140), width=2)

    # Red Badge Pill
    guide_draw.rounded_rectangle([card_x + 30, c1_y + 26, card_x + 230, c1_y + 66], radius=14, fill=(255, 59, 48, 255))
    guide_draw.text((card_x + 46, c1_y + 34), "RED ZONE", font=font_badge, fill=(255, 255, 255, 255))
    guide_draw.text((card_x + 250, c1_y + 28), "Folder Creation Trigger", font=font_large_bold, fill=(255, 255, 255, 255))

    guide_draw.text((card_x + 30, c1_y + 84), "State: DRAG_MODE_CREATE_FOLDER  |  Condition: distance <= folderCreationRadius", font=font_small, fill=(255, 120, 110, 255))

    p1 = [
        "Trigger Region: Central circular target zone around the icon's visual center.",
        "Radius Calculation: folderRadius = (calculateReorderRadius() + iconVisibleRadius) / 2",
        "Visual Feedback: Target icon scales up / highlights; folder creation alarm activates.",
        "Drop Action: Merges both apps into a new Folder (or appends into existing folder).",
        "System Invariant: Suppresses workspace, dock icons, dock glass & search pill to alpha = 0."
    ]
    cur_y = c1_y + 126
    for pt in p1:
        guide_draw.ellipse([card_x + 30, cur_y + 8, card_x + 38, cur_y + 16], fill=(255, 59, 48, 255))
        guide_draw.text((card_x + 50, cur_y), pt, font=font_small, fill=(225, 228, 235, 255))
        cur_y += 39

    # Clean elbow connector from Red zone to Card 1
    red_edge_x = ic_center_x + int(fol_disp_r * 0.707)
    red_edge_y = ic_center_y - int(fol_disp_r * 0.707)
    guide_draw.ellipse([red_edge_x - 5, red_edge_y - 5, red_edge_x + 5, red_edge_y + 5], fill=(255, 59, 48, 255))
    guide_draw.line([(red_edge_x, red_edge_y), (red_edge_x + 60, red_edge_y - 30)], fill=(255, 59, 48, 220), width=2)
    guide_draw.line([(red_edge_x + 60, red_edge_y - 30), (card_x - 10, red_edge_y - 30)], fill=(255, 59, 48, 220), width=2)
    guide_draw.ellipse([card_x - 14, red_edge_y - 34, card_x - 6, red_edge_y - 26], fill=(255, 59, 48, 255))

    # -------------------------------------------------------------
    # CARD 2: BLUE ZONE - MOVE ASIDE (REORDER)
    # -------------------------------------------------------------
    c2_y = 590
    c2_h = 370
    guide_draw.rounded_rectangle([card_x, c2_y, card_x + card_w, c2_y + c2_h], radius=20, fill=(30, 31, 36, 255), outline=(0, 122, 255, 140), width=2)

    # Blue Badge Pill
    guide_draw.rounded_rectangle([card_x + 30, c2_y + 26, card_x + 230, c2_y + 66], radius=14, fill=(0, 122, 255, 255))
    guide_draw.text((card_x + 42, c2_y + 34), "BLUE ZONE", font=font_badge, fill=(255, 255, 255, 255))
    guide_draw.text((card_x + 250, c2_y + 28), "Move Aside (Reorder)", font=font_large_bold, fill=(255, 255, 255, 255))

    guide_draw.text((card_x + 30, c2_y + 84), "State: DRAG_MODE_REORDER  |  Condition: folderRadius < distance <= reorderRadius", font=font_small, fill=(100, 175, 255, 255))

    p2 = [
        "Trigger Region: Outer perimeter edges, corners, and cell margin around the app icon.",
        "Radius Calculation: reorderRadius = calculateReorderRadius() + boundaryExtension",
        "Visual Feedback: Target icon smoothly shifts aside (vacates cell) to indicate drop spot.",
        "Direction Vector: Shifting vector (decX / decY) calculated from drag angle & free space.",
        "Drop Action: Dragged app takes target cell; displaced app is moved to adjacent cell."
    ]
    cur_y = c2_y + 126
    for pt in p2:
        guide_draw.ellipse([card_x + 30, cur_y + 8, card_x + 38, cur_y + 16], fill=(0, 122, 255, 255))
        guide_draw.text((card_x + 50, cur_y), pt, font=font_small, fill=(225, 228, 235, 255))
        cur_y += 39

    # Clean elbow connector from Blue zone to Card 2
    blue_edge_x = icon_pos_x + icon_display_size - 60
    blue_edge_y = icon_pos_y + icon_display_size - 60
    guide_draw.ellipse([blue_edge_x - 5, blue_edge_y - 5, blue_edge_x + 5, blue_edge_y + 5], fill=(0, 122, 255, 255))
    guide_draw.line([(blue_edge_x, blue_edge_y), (blue_edge_x + 40, blue_edge_y + 30)], fill=(0, 122, 255, 220), width=2)
    guide_draw.line([(blue_edge_x + 40, blue_edge_y + 30), (card_x - 10, blue_edge_y + 30)], fill=(0, 122, 255, 220), width=2)
    guide_draw.ellipse([card_x - 14, blue_edge_y + 26, card_x - 6, blue_edge_y + 34], fill=(0, 122, 255, 255))

    # Footer note
    guide_draw.text((80, 1000), "* Principle: Approaching an app from any edge hits the Blue (Move Aside) zone first. Aiming directly into the center circle enters the Red (Folder Creation) zone.", font=font_small, fill=(145, 150, 160, 255))

    out_info_path = r"d:\Code\OS_Launcher\app_icon_trigger_zones.png"
    info_img.save(out_info_path, "PNG")
    print(f"Saved {out_info_path}")

    # Copy all 3 to artifacts directory
    artifact_dir = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"
    shutil.copy(out_pure_path, os.path.join(artifact_dir, "app_icon_zones_pure.png"))
    shutil.copy(out_labeled_path, os.path.join(artifact_dir, "app_icon_zones_only.png"))
    shutil.copy(out_info_path, os.path.join(artifact_dir, "app_icon_trigger_zones.png"))
    print("Copied all images to artifacts directory")

if __name__ == "__main__":
    create_images()
