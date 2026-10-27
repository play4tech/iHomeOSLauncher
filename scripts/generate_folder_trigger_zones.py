import math, os, sys, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

sys.stdout.reconfigure(encoding='utf-8')

def squircle_pts(cx, cy, rx, ry, n=4.0, steps=720):
    pts = []
    p = 2.0 / n
    for i in range(steps):
        theta = 2.0 * math.pi * i / steps
        cos_t = math.cos(theta)
        sin_t = math.sin(theta)
        sgn_x = 1.0 if cos_t >= 0 else -1.0
        sgn_y = 1.0 if sin_t >= 0 else -1.0
        x = cx + rx * sgn_x * (abs(cos_t) ** p)
        y = cy + ry * sgn_y * (abs(sin_t) ** p)
        pts.append((x, y))
    return pts

def create_folder_trigger_zones():
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    font_mono_path = r"C:\Windows\Fonts\consola.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    font_huge = ImageFont.truetype(font_bold_path, 42)
    font_large_bold = ImageFont.truetype(font_bold_path, 28)
    font_large_reg = ImageFont.truetype(font_reg_path, 26)
    font_med_bold = ImageFont.truetype(font_bold_path, 22)
    font_med_reg = ImageFont.truetype(font_reg_path, 20)
    font_small_bold = ImageFont.truetype(font_bold_path, 17)
    font_small = ImageFont.truetype(font_reg_path, 16)
    font_badge = ImageFont.truetype(font_bold_path, 15)
    font_mono = ImageFont.truetype(font_mono_path if os.path.exists(font_mono_path) else font_reg_path, 17)

    # =========================================================================
    # 1. GENERATE folder_trigger_zones.png (Infographic)
    # =========================================================================
    W, H = 1600, 1100
    img = Image.new("RGBA", (W, H), (20, 21, 24, 255))
    draw = ImageDraw.Draw(img)

    # Top Banner
    draw.text((80, 45), "Homescreen Folder Drag & Drop Trigger Zones", font=font_huge, fill=(255, 255, 255, 255))
    draw.text((80, 102), "Concentric 65% Squircle (Red Zone) vs. Outer 35% Margin (Blue Zone: Move Aside)", font=font_large_reg, fill=(160, 165, 175, 255))
    draw.line([(80, 145), (W - 80, 145)], fill=(45, 48, 56, 255), width=2)

    # -------------------------------------------------------------
    # Left Side: Render Large Folder Card (700 x 700 virtual canvas)
    # -------------------------------------------------------------
    F_SIZE = 600
    folder_canvas = Image.new("RGBA", (F_SIZE, F_SIZE), (0, 0, 0, 0))
    f_draw = ImageDraw.Draw(folder_canvas)

    fc_x, fc_y = F_SIZE / 2.0, F_SIZE / 2.0
    f_outer_r = (F_SIZE * 0.85) / 2.0  # Folder card radius
    f_inner_r = f_outer_r * 0.65       # 65% Squircle trigger radius

    # Outer folder shadow
    shadow_img = Image.new("RGBA", (F_SIZE, F_SIZE), (0, 0, 0, 0))
    s_draw = ImageDraw.Draw(shadow_img)
    outer_pts_shadow = squircle_pts(fc_x, fc_y + 16, f_outer_r, f_outer_r, n=4.0)
    s_draw.polygon(outer_pts_shadow, fill=(0, 0, 0, 140))
    shadow_img = shadow_img.filter(ImageFilter.GaussianBlur(radius=20))
    folder_canvas.paste(shadow_img, (0, 0), shadow_img)

    # Folder Card Background: Blue Zone (Move Aside 35% margin)
    folder_card = Image.new("RGBA", (F_SIZE, F_SIZE), (0, 0, 0, 0))
    card_draw = ImageDraw.Draw(folder_card)
    outer_pts = squircle_pts(fc_x, fc_y, f_outer_r, f_outer_r, n=4.0)
    
    # Outer squircle mask
    mask_outer = Image.new("L", (F_SIZE, F_SIZE), 0)
    m_draw = ImageDraw.Draw(mask_outer)
    m_draw.polygon(outer_pts, fill=255)

    # Gradient blue fill for outer 35%
    for y_i in range(int(fc_y - f_outer_r), int(fc_y + f_outer_r)):
        prog = (y_i - (fc_y - f_outer_r)) / (2 * f_outer_r)
        r_c = int(0 * (1 - prog) + 10 * prog)
        g_c = int(122 * (1 - prog) + 140 * prog)
        b_c = int(255 * (1 - prog) + 235 * prog)
        card_draw.line([(fc_x - f_outer_r, y_i), (fc_x + f_outer_r, y_i)], fill=(r_c, g_c, b_c, 255))

    # 4 mini app preview icons in corners of folder card (frosted background hints)
    mini_size = f_outer_r * 0.35
    mini_margin = f_outer_r * 0.12
    mini_offsets = [
        (fc_x - mini_size - mini_margin, fc_y - mini_size - mini_margin),
        (fc_x + mini_margin, fc_y - mini_size - mini_margin),
        (fc_x - mini_size - mini_margin, fc_y + mini_margin),
        (fc_x + mini_margin, fc_y + mini_margin)
    ]
    for mx, my in mini_offsets:
        card_draw.rounded_rectangle([mx, my, mx + mini_size, my + mini_size], radius=int(mini_size * 0.22), fill=(255, 255, 255, 45), outline=(255, 255, 255, 70), width=2)

    # Central Red 65% Squircle (Add to Folder)
    red_layer = Image.new("RGBA", (F_SIZE, F_SIZE), (0, 0, 0, 0))
    r_draw = ImageDraw.Draw(red_layer)
    inner_pts = squircle_pts(fc_x, fc_y, f_inner_r, f_inner_r, n=4.0)
    r_draw.polygon(inner_pts, fill=(255, 59, 48, 250))
    r_draw.polygon(inner_pts, outline=(255, 255, 255, 255), width=4)
    card_draw.bitmap((0, 0), red_layer)

    # Mask card to squircle
    masked_folder = Image.new("RGBA", (F_SIZE, F_SIZE), (0, 0, 0, 0))
    masked_folder.paste(folder_card, (0, 0), mask_outer)

    # Outer border of folder card
    f_border_draw = ImageDraw.Draw(masked_folder)
    f_border_draw.polygon(outer_pts, outline=(255, 255, 255, 180), width=4)

    folder_canvas.paste(masked_folder, (0, 0), masked_folder)

    # Labels inside folder canvas
    fc_draw = ImageDraw.Draw(folder_canvas)

    # Red Squircle Text
    f_bold_large = ImageFont.truetype(font_bold_path, 26)
    f_bold_sub = ImageFont.truetype(font_bold_path, 18)
    
    t_red_1 = "ADD TO FOLDER"
    t_red_2 = "65% SQUIRCLE"
    t_red_3 = "DROP ZONE"
    b1 = fc_draw.textbbox((0, 0), t_red_1, font=f_bold_large)
    b2 = fc_draw.textbbox((0, 0), t_red_2, font=f_bold_sub)
    b3 = fc_draw.textbbox((0, 0), t_red_3, font=f_bold_sub)
    fc_draw.text((fc_x - (b1[2]-b1[0])/2, fc_y - 38), t_red_1, font=f_bold_large, fill=(255, 255, 255, 255))
    fc_draw.text((fc_x - (b2[2]-b2[0])/2, fc_y - 2), t_red_2, font=f_bold_sub, fill=(255, 255, 255, 230))
    fc_draw.text((fc_x - (b3[2]-b3[0])/2, fc_y + 24), t_red_3, font=f_bold_sub, fill=(255, 255, 255, 210))

    # Outer Blue Margin Labels & Arrows
    f_side_label = ImageFont.truetype(font_bold_path, 16)
    t_blue_top = "MOVE ASIDE (REORDER 35% MARGIN)"
    bb = fc_draw.textbbox((0, 0), t_blue_top, font=f_side_label)
    fc_draw.text((fc_x - (bb[2]-bb[0])/2, fc_y - f_outer_r + 14), t_blue_top, font=f_side_label, fill=(255, 255, 255, 240))
    fc_draw.text((fc_x - (bb[2]-bb[0])/2, fc_y + f_outer_r - 32), t_blue_top, font=f_side_label, fill=(255, 255, 255, 240))

    # Push arrows on left & right sides
    # Left arrow
    fc_draw.line([(fc_x - f_outer_r + 15, fc_y), (fc_x - f_inner_r - 12, fc_y)], fill=(255, 255, 255, 240), width=3)
    fc_draw.line([(fc_x - f_outer_r + 15, fc_y), (fc_x - f_outer_r + 27, fc_y - 10)], fill=(255, 255, 255, 240), width=3)
    fc_draw.line([(fc_x - f_outer_r + 15, fc_y), (fc_x - f_outer_r + 27, fc_y + 10)], fill=(255, 255, 255, 240), width=3)

    # Right arrow
    fc_draw.line([(fc_x + f_inner_r + 12, fc_y), (fc_x + f_outer_r - 15, fc_y)], fill=(255, 255, 255, 240), width=3)
    fc_draw.line([(fc_x + f_outer_r - 15, fc_y), (fc_x + f_outer_r - 27, fc_y - 10)], fill=(255, 255, 255, 240), width=3)
    fc_draw.line([(fc_x + f_outer_r - 15, fc_y), (fc_x + f_outer_r - 27, fc_y + 10)], fill=(255, 255, 255, 240), width=3)

    # Paste folder on main image
    img.paste(folder_canvas, (70, 200), folder_canvas)

    # Dimensions markers on left
    dim_cx = 70 + fc_x
    dim_cy = 200 + fc_y
    # Squircle radius dimension line
    draw.line([(dim_cx, dim_cy + 75), (dim_cx + f_inner_r, dim_cy + 75)], fill=(255, 255, 255, 255), width=2)
    draw.ellipse([dim_cx - 4, dim_cy + 75 - 4, dim_cx + 4, dim_cy + 75 + 4], fill=(255, 255, 255, 255))
    draw.ellipse([dim_cx + f_inner_r - 4, dim_cy + 75 - 4, dim_cx + f_inner_r + 4, dim_cy + 75 + 4], fill=(255, 255, 255, 255))
    draw.text((dim_cx + 25, dim_cy + 52), "r_trigger = 55.9px (65%)", font=font_small_bold, fill=(255, 255, 255, 255))

    # Outer margin dimension line
    draw.line([(dim_cx + f_inner_r, dim_cy + 75), (dim_cx + f_outer_r, dim_cy + 75)], fill=(120, 200, 255, 255), width=2)
    draw.ellipse([dim_cx + f_outer_r - 4, dim_cy + 75 - 4, dim_cx + f_outer_r + 4, dim_cy + 75 + 4], fill=(120, 200, 255, 255))
    draw.text((dim_cx + f_inner_r + 8, dim_cy + 82), "margin = 30.1px (35%)", font=font_small_bold, fill=(120, 200, 255, 255))

    # Bottom caption under folder
    draw.text((120, 840), "Figure 1: Concentric 65% Squircle (L4 superellipse) matches App Icon trigger.", font=font_small, fill=(170, 175, 185, 255))
    draw.text((120, 865), "Outer 35% margin (30.1px) cleanly triggers Move Aside instead of swallowing the drop.", font=font_small, fill=(170, 175, 185, 255))

    # -------------------------------------------------------------
    # Right Side: Detailed Spec Cards
    # -------------------------------------------------------------
    card_x = 720
    card_w = 800

    # CARD 1: RED ZONE (DROP INTO FOLDER)
    c1_y = 175
    c1_h = 370
    draw.rounded_rectangle([card_x, c1_y, card_x + card_w, c1_y + c1_h], radius=16, fill=(28, 29, 34, 255), outline=(255, 59, 48, 140), width=2)
    # Header badge
    draw.rounded_rectangle([card_x + 25, c1_y + 20, card_x + 380, c1_y + 60], radius=8, fill=(255, 59, 48, 220))
    draw.text((card_x + 35, c1_y + 27), "RED ZONE: DROP INTO FOLDER", font=font_small_bold, fill=(255, 255, 255, 255))

    draw.text((card_x + 25, c1_y + 75), "• Geometry: Concentric 65% Squircle (Superellipse n=4)", font=font_med_bold, fill=(255, 255, 255, 255))
    draw.text((card_x + 45, c1_y + 105), "Math Formula: (dx^4 + dy^4)^(1/4) <= r_drop", font=font_mono, fill=(255, 180, 170, 255))
    draw.text((card_x + 45, c1_y + 135), "Device Radius: r_drop = 86px * 0.65 = 55.9px (18.6dp)", font=font_med_reg, fill=(230, 230, 235, 255))
    draw.text((card_x + 45, c1_y + 165), "Code Hook: CellLayout.getFolderCreationRadius()", font=font_mono, fill=(180, 215, 255, 255))

    draw.text((card_x + 25, c1_y + 205), "• Launcher State & Reaction:", font=font_med_bold, fill=(255, 255, 255, 255))
    draw.text((card_x + 45, c1_y + 235), "1. DragController enters DRAG_MODE_ADD_TO_FOLDER.", font=font_med_reg, fill=(200, 205, 215, 255))
    draw.text((card_x + 45, c1_y + 265), "2. Folder stays stationary in cell and pulses accept highlight.", font=font_med_reg, fill=(200, 205, 215, 255))
    draw.text((card_x + 45, c1_y + 295), "3. Releasing drop adds item directly inside the folder card.", font=font_med_reg, fill=(200, 205, 215, 255))
    draw.text((card_x + 45, c1_y + 325), "4. Hovering for 800ms triggers smooth spring open into folder view.", font=font_med_reg, fill=(200, 205, 215, 255))

    # CARD 2: BLUE ZONE (MOVE ASIDE / REORDER)
    c2_y = 570
    c2_h = 475
    draw.rounded_rectangle([card_x, c2_y, card_x + card_w, c2_y + c2_h], radius=16, fill=(28, 29, 34, 255), outline=(0, 122, 255, 140), width=2)
    # Header badge
    draw.rounded_rectangle([card_x + 25, c2_y + 20, card_x + 430, c2_y + 60], radius=8, fill=(0, 122, 255, 220))
    draw.text((card_x + 35, c2_y + 27), "BLUE ZONE: MOVE ASIDE (REORDER)", font=font_small_bold, fill=(255, 255, 255, 255))

    draw.text((card_x + 25, c2_y + 75), "• Outer 35% Margin Inside Card + Surrounding Cell Space:", font=font_med_bold, fill=(255, 255, 255, 255))
    draw.text((card_x + 45, c2_y + 105), "Folder Card Outer Margin: 55.9px < dist <= 86px (width: 30.1px / 10.0dp)", font=font_med_reg, fill=(160, 220, 255, 255))
    draw.text((card_x + 45, c2_y + 135), "Cell Boundary Region: 86px < dist <= 123px+ (reorder perimeter)", font=font_med_reg, fill=(160, 220, 255, 255))
    draw.text((card_x + 45, c2_y + 165), "Code Hook: CellLayout.performReorder() & MODE_SHOW_REORDER_HINT", font=font_mono, fill=(180, 215, 255, 255))

    draw.text((card_x + 25, c2_y + 205), "• Move Aside Reaction & Invariants:", font=font_med_bold, fill=(255, 255, 255, 255))
    draw.text((card_x + 45, c2_y + 235), "1. Dragging into outer 35% margin signals intent to place on desktop.", font=font_med_reg, fill=(200, 205, 215, 255))
    draw.text((card_x + 45, c2_y + 265), "2. Folder smoothly slides aside to adjacent cell with push animation.", font=font_med_reg, fill=(200, 205, 215, 255))
    draw.text((card_x + 45, c2_y + 295), "3. Releasing drop places dragged app into this cell on the workspace.", font=font_med_reg, fill=(200, 205, 215, 255))

    # Architectural Comparison Table inside Card 2
    tb_y = c2_y + 340
    draw.rounded_rectangle([card_x + 25, tb_y, card_x + card_w - 25, tb_y + 115], radius=10, fill=(22, 23, 27, 255), outline=(45, 48, 56, 255), width=1)
    draw.text((card_x + 40, tb_y + 15), "Element", font=font_small_bold, fill=(150, 155, 165, 255))
    draw.text((card_x + 200, tb_y + 15), "Total Visual Size", font=font_small_bold, fill=(150, 155, 165, 255))
    draw.text((card_x + 400, tb_y + 15), "65% Drop Squircle", font=font_small_bold, fill=(255, 100, 90, 255))
    draw.text((card_x + 610, tb_y + 15), "35% Move Aside", font=font_small_bold, fill=(100, 180, 255, 255))
    draw.line([(card_x + 35, tb_y + 40), (card_x + card_w - 35, tb_y + 40)], fill=(45, 48, 56, 255), width=1)

    draw.text((card_x + 40, tb_y + 50), "App Icon", font=font_small, fill=(255, 255, 255, 255))
    draw.text((card_x + 200, tb_y + 50), "207px (half: 103.5px)", font=font_small, fill=(210, 215, 225, 255))
    draw.text((card_x + 400, tb_y + 50), "r = 67.3px (22.4dp)", font=font_small_bold, fill=(255, 120, 110, 255))
    draw.text((card_x + 610, tb_y + 50), "margin = 36.2px", font=font_small_bold, fill=(120, 190, 255, 255))

    draw.text((card_x + 40, tb_y + 80), "Folder Card", font=font_small, fill=(255, 255, 255, 255))
    draw.text((card_x + 200, tb_y + 80), "172px (half: 86.0px)", font=font_small, fill=(210, 215, 225, 255))
    draw.text((card_x + 400, tb_y + 80), "r = 55.9px (18.6dp)", font=font_small_bold, fill=(255, 120, 110, 255))
    draw.text((card_x + 610, tb_y + 80), "margin = 30.1px", font=font_small_bold, fill=(120, 190, 255, 255))

    out_file = r"d:\Code\OS_Launcher\folder_trigger_zones.png"
    img.save(out_file, "PNG")
    print(f"Saved infographic to {out_file}")

    # =========================================================================
    # 2. GENERATE homescreen_folder_trigger_overlay.png (Live Overlay)
    # =========================================================================
    live_screen_path = r"d:\Code\OS_Launcher\live_device_screen.png"
    if not os.path.exists(live_screen_path):
        live_screen_path = r"d:\Code\OS_Launcher\verify_screen_fixed.png"
    
    base_screen = Image.open(live_screen_path).convert("RGBA")
    sw, sh = base_screen.size

    overlay_canvas = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    o_draw = ImageDraw.Draw(overlay_canvas)

    # Folder: Finance at cell [0, 3]
    # bounds: [48, 1265][294, 1542] -> Cell: 246 x 277
    cell_l, cell_t, cell_r, cell_b = 48, 1265, 294, 1542
    f_center_x = (cell_l + cell_r) / 2.0  # 171.0
    # Visual center of folder card
    f_card_size = 172.0
    f_card_half = f_card_size / 2.0       # 86.0
    f_center_y = cell_t + 17.5 + f_card_half # 1368.5

    f_trigger_half = f_card_half * 0.65   # 55.9px

    # Draw Blue Zone: Cell outline & folder margin
    o_draw.rectangle([cell_l, cell_t, cell_r, cell_b], fill=(0, 122, 255, 50), outline=(0, 122, 255, 200), width=3)
    
    # Outer squircle of folder card in blue
    card_pts = squircle_pts(f_center_x, f_center_y, f_card_half, f_card_half, n=4.0)
    o_draw.polygon(card_pts, fill=(0, 122, 255, 110), outline=(0, 122, 255, 255), width=3)

    # Inner Red Squircle (65% Drop Zone)
    trig_pts = squircle_pts(f_center_x, f_center_y, f_trigger_half, f_trigger_half, n=4.0)
    o_draw.polygon(trig_pts, fill=(255, 59, 48, 185), outline=(255, 255, 255, 255), width=4)

    # Inset / Zoomed Callout View for absolute clarity
    # Crop a 400x400 region around the folder
    crop_box = [int(f_center_x - 170), int(f_center_y - 170), int(f_center_x + 170), int(f_center_y + 170)]
    # Composite overlay on base first
    comp_full = Image.alpha_composite(base_screen, overlay_canvas)
    
    # Let's create an annotated full presentation with zoomed callout
    final_overlay = comp_full.copy()
    fo_draw = ImageDraw.Draw(final_overlay)

    # Callout Box placed in empty space on right side of screen (around x=480, y=1180)
    inset_w, inset_h = 520, 520
    inset_x, inset_y = 500, 1150

    # Draw arrow from folder to inset box
    fo_draw.line([(f_center_x + f_card_half + 10, f_center_y), (inset_x, inset_y + inset_h // 2)], fill=(255, 255, 255, 220), width=3)
    fo_draw.ellipse([f_center_x + f_card_half + 6, f_center_y - 4, f_center_x + f_card_half + 14, f_center_y + 4], fill=(255, 255, 255, 255))

    # Inset background
    fo_draw.rounded_rectangle([inset_x - 10, inset_y - 10, inset_x + inset_w + 10, inset_y + inset_h + 10], radius=20, fill=(15, 16, 20, 245), outline=(255, 255, 255, 180), width=3)

    # High-res crop of folder from base_screen
    folder_crop = base_screen.crop(crop_box).resize((inset_w, inset_h), Image.Resampling.LANCZOS)
    
    # Overlay on zoomed crop
    crop_overlay = Image.new("RGBA", (inset_w, inset_h), (0, 0, 0, 0))
    co_draw = ImageDraw.Draw(crop_overlay)
    
    scale_crop = inset_w / (crop_box[2] - crop_box[0])
    zoom_cx = inset_w / 2.0
    zoom_cy = inset_h / 2.0
    zoom_card_r = f_card_half * scale_crop
    zoom_trig_r = f_trigger_half * scale_crop

    # Zoomed outer 35% margin (blue)
    zoom_card_pts = squircle_pts(zoom_cx, zoom_cy, zoom_card_r, zoom_card_r, n=4.0)
    co_draw.polygon(zoom_card_pts, fill=(0, 122, 255, 90), outline=(0, 122, 255, 255), width=4)

    # Zoomed inner 65% squircle (red)
    zoom_trig_pts = squircle_pts(zoom_cx, zoom_cy, zoom_trig_r, zoom_trig_r, n=4.0)
    co_draw.polygon(zoom_trig_pts, fill=(255, 59, 48, 180), outline=(255, 255, 255, 255), width=5)

    # Labels inside zoom
    font_zoom_large = ImageFont.truetype(font_bold_path, 26)
    font_zoom_sub = ImageFont.truetype(font_bold_path, 18)
    font_zoom_tag = ImageFont.truetype(font_bold_path, 16)

    zt1 = "DROP (65%)"
    zt2 = "r = 55.9px"
    zb1 = co_draw.textbbox((0, 0), zt1, font=font_zoom_large)
    zb2 = co_draw.textbbox((0, 0), zt2, font=font_zoom_sub)
    co_draw.text((zoom_cx - (zb1[2]-zb1[0])/2, zoom_cy - 28), zt1, font=font_zoom_large, fill=(255, 255, 255, 255))
    co_draw.text((zoom_cx - (zb2[2]-zb2[0])/2, zoom_cy + 5), zt2, font=font_zoom_sub, fill=(255, 255, 255, 230))

    # Top & bottom zoom margin labels
    co_draw.text((zoom_cx - 100, zoom_cy - zoom_card_r + 15), "MOVE ASIDE (35%)", font=font_zoom_tag, fill=(255, 255, 255, 240))
    co_draw.text((zoom_cx - 100, zoom_cy + zoom_card_r - 35), "MOVE ASIDE (35%)", font=font_zoom_tag, fill=(255, 255, 255, 240))

    # Push arrows on zoomed margin
    co_draw.line([(zoom_cx - zoom_card_r + 12, zoom_cy), (zoom_cx - zoom_trig_r - 12, zoom_cy)], fill=(255, 255, 255, 230), width=4)
    co_draw.line([(zoom_cx - zoom_card_r + 12, zoom_cy), (zoom_cx - zoom_card_r + 24, zoom_cy - 10)], fill=(255, 255, 255, 230), width=4)
    co_draw.line([(zoom_cx - zoom_card_r + 12, zoom_cy), (zoom_cx - zoom_card_r + 24, zoom_cy + 10)], fill=(255, 255, 255, 230), width=4)

    co_draw.line([(zoom_cx + zoom_trig_r + 12, zoom_cy), (zoom_cx + zoom_card_r - 12, zoom_cy)], fill=(255, 255, 255, 230), width=4)
    co_draw.line([(zoom_cx + zoom_card_r - 12, zoom_cy), (zoom_cx + zoom_card_r - 24, zoom_cy - 10)], fill=(255, 255, 255, 230), width=4)
    co_draw.line([(zoom_cx + zoom_card_r - 12, zoom_cy), (zoom_cx + zoom_card_r - 24, zoom_cy + 10)], fill=(255, 255, 255, 230), width=4)

    # Composite zoom into callout
    final_crop = Image.alpha_composite(folder_crop, crop_overlay)
    final_overlay.paste(final_crop, (inset_x, inset_y))

    # Header on zoomed callout
    fo_draw.rounded_rectangle([inset_x, inset_y - 45, inset_x + 360, inset_y - 5], radius=8, fill=(30, 31, 36, 240), outline=(255, 255, 255, 120), width=1)
    fo_draw.text((inset_x + 15, inset_y - 37), "FOLDER #76 ZOOMED VIEW", font=font_small_bold, fill=(255, 255, 255, 255))

    out_overlay_file = r"d:\Code\OS_Launcher\homescreen_folder_trigger_overlay.png"
    final_overlay.save(out_overlay_file, "PNG")
    print(f"Saved live homescreen overlay to {out_overlay_file}")

    # Copy both files to brain directory
    brain_dir = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"
    shutil.copy2(out_file, os.path.join(brain_dir, "folder_trigger_zones.png"))
    shutil.copy2(out_overlay_file, os.path.join(brain_dir, "homescreen_folder_trigger_overlay.png"))
    print(f"Copied both images to brain directory: {brain_dir}")

if __name__ == "__main__":
    create_folder_trigger_zones()
