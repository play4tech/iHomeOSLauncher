import os, shutil
from PIL import Image, ImageDraw, ImageFont

def generate_both_sides_overlay():
    src_img_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\rmx_folder_open_check.png"
    img = Image.open(src_img_path).convert("RGBA")
    W, H = img.size # 1080 x 2400

    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    font_title = ImageFont.truetype(font_bold_path, 36)
    font_subtitle = ImageFont.truetype(font_reg_path, 23)
    font_badge = ImageFont.truetype(font_bold_path, 25)
    font_body = ImageFont.truetype(font_reg_path, 21)
    font_code = ImageFont.truetype(font_bold_path, 21)
    font_dim = ImageFont.truetype(font_bold_path, 28)
    font_small = ImageFont.truetype(font_reg_path, 18)

    # Folder card bounds (Realme 1080p display)
    f_left, f_top, f_right, f_bottom = 94, 472, 985, 1819
    f_w = f_right - f_left # 891px
    f_h = f_bottom - f_top # 1347px
    f_corner_r = f_w * 0.225 # ~200px

    # Trigger widths (Symmetrical & Expanded)
    internal_w = 78 # 26dp * 3.0 density inside the card
    beyond_w = 84   # 28dp * 3.0 density extending beyond squircle edge (all the way to screen bezel)
    total_w = internal_w + beyond_w # 162px

    # RIGHT TRIGGER BOUNDS
    r_trigger_inner_left = f_right - internal_w  # 907px
    r_trigger_card_edge = f_right               # 985px
    r_trigger_outer_right = min(W - 10, f_right + beyond_w)   # 1069px (screen bezel)

    # LEFT TRIGGER BOUNDS (Symmetric!)
    l_trigger_inner_right = f_left + internal_w  # 172px
    l_trigger_card_edge = f_left                # 94px
    l_trigger_outer_left = max(10, f_left - beyond_w)         # 10px (screen bezel)

    # 1. Folder squircle mask for the inside portion
    mask_folder = Image.new("L", (W, H), 0)
    mf_draw = ImageDraw.Draw(mask_folder)
    mf_draw.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, fill=255)

    # Inside red fill layer (both left and right inside squircle)
    red_inner_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_ril = ImageDraw.Draw(red_inner_layer)
    # Right inside red
    draw_ril.rectangle([r_trigger_inner_left, f_top, r_trigger_card_edge, f_bottom], fill=(255, 38, 38, 190))
    # Left inside red
    draw_ril.rectangle([l_trigger_card_edge, f_top, l_trigger_inner_right, f_bottom], fill=(255, 38, 38, 190))

    empty_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    clipped_inner_red = Image.composite(red_inner_layer, empty_layer, mask_folder)
    overlay.paste(clipped_inner_red, (0, 0), clipped_inner_red)

    # 2. Outside extension red layer (both left and right beyond squircle)
    ext_mask = Image.new("L", (W, H), 0)
    ext_draw = ImageDraw.Draw(ext_mask)
    # Right outer rounded rect
    ext_draw.rounded_rectangle([r_trigger_inner_left, f_top, r_trigger_outer_right, f_bottom], radius=f_corner_r, fill=255)
    # Left outer rounded rect
    ext_draw.rounded_rectangle([l_trigger_outer_left, f_top, l_trigger_inner_right, f_bottom], radius=f_corner_r, fill=255)
    
    red_outer_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_rol = ImageDraw.Draw(red_outer_layer)
    # Right outside red fill
    draw_rol.rectangle([r_trigger_card_edge, f_top, r_trigger_outer_right, f_bottom], fill=(255, 59, 48, 160))
    # Left outside red fill
    draw_rol.rectangle([l_trigger_outer_left, f_top, l_trigger_card_edge, f_bottom], fill=(255, 59, 48, 160))

    clipped_outer_red = Image.composite(red_outer_layer, empty_layer, ext_mask)
    overlay.paste(clipped_outer_red, (0, 0), clipped_outer_red)

    draw = ImageDraw.Draw(overlay)

    # 3. Structural boundary outlines
    # White folder squircle edge
    draw.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, outline=(255, 255, 255, 170), width=3)

    red_border_col = (255, 45, 45, 255)
    ext_border_col = (255, 100, 90, 255)

    # Right internal divider & dashed borders
    draw.line([(r_trigger_inner_left, f_top + 15), (r_trigger_inner_left, f_bottom - 15)], fill=red_border_col, width=4)
    for seg_y in range(f_top + 20, f_bottom - 20, 24):
        draw.line([(r_trigger_card_edge, seg_y), (r_trigger_card_edge, seg_y + 14)], fill=(255, 255, 255, 230), width=3)
    for seg_y in range(f_top + 30, f_bottom - 30, 20):
        draw.line([(r_trigger_outer_right, seg_y), (r_trigger_outer_right, seg_y + 10)], fill=ext_border_col, width=3)

    # Left internal divider & dashed borders (symmetric!)
    draw.line([(l_trigger_inner_right, f_top + 15), (l_trigger_inner_right, f_bottom - 15)], fill=red_border_col, width=4)
    for seg_y in range(f_top + 20, f_bottom - 20, 24):
        draw.line([(l_trigger_card_edge, seg_y), (l_trigger_card_edge, seg_y + 14)], fill=(255, 255, 255, 230), width=3)
    for seg_y in range(f_top + 30, f_bottom - 30, 20):
        draw.line([(l_trigger_outer_left, seg_y), (l_trigger_outer_left, seg_y + 10)], fill=ext_border_col, width=3)

    # Chevrons
    # Right: >>>
    for cy in [700, 930, 1150, 1370, 1580]:
        mid_x = (r_trigger_inner_left + r_trigger_outer_right) / 2
        draw.line([(mid_x - 10, cy - 14), (mid_x + 8, cy)], fill=(255, 255, 255, 255), width=4)
        draw.line([(mid_x - 10, cy + 14), (mid_x + 8, cy)], fill=(255, 255, 255, 255), width=4)

    # Left: <<<
    for cy in [700, 930, 1150, 1370, 1580]:
        mid_x = (l_trigger_inner_right + l_trigger_outer_left) / 2
        draw.line([(mid_x + 10, cy - 14), (mid_x - 8, cy)], fill=(255, 255, 255, 255), width=4)
        draw.line([(mid_x + 10, cy + 14), (mid_x - 8, cy)], fill=(255, 255, 255, 255), width=4)

    # Top Header Banner
    banner_y = 90
    banner_h = 150
    draw.rounded_rectangle([50, banner_y, W - 50, banner_y + banner_h], radius=24, fill=(15, 16, 20, 248), outline=(255, 55, 55, 220), width=3)
    draw.text((80, banner_y + 18), "Both Sides Symmetrical Page Flip Triggers (Left & Right)", font=font_title, fill=(255, 255, 255, 255))
    draw.text((80, banner_y + 76), f"Guaranteed 162px reach on BOTH sides: 78px inside squircle + 84px to screen bezel", font=font_subtitle, fill=(255, 120, 110, 255))
    draw.text((80, banner_y + 113), f"Left: X in [{l_trigger_outer_left}px, {l_trigger_inner_right}px]   |   Right: X in [{r_trigger_inner_left}px, {r_trigger_outer_right}px]", font=font_code, fill=(240, 200, 190, 255))

    # Left Side Callout Pill
    l_pill_w = 340
    l_pill_h = 160
    l_pill_x = l_trigger_inner_right + 30 # 172 + 30 = 202
    l_pill_y = 1040
    draw.rounded_rectangle([l_pill_x, l_pill_y, l_pill_x + l_pill_w, l_pill_y + l_pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=3)
    draw.text((l_pill_x + 20, l_pill_y + 16), "PREVIOUS PAGE", font=font_badge, fill=(255, 65, 65, 255))
    draw.text((l_pill_x + 20, l_pill_y + 54), f"Width: {total_w}px (54dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw.text((l_pill_x + 20, l_pill_y + 94), f"• Inside: {internal_w}px | Out: +{beyond_w}px", font=font_code, fill=(255, 170, 160, 255))
    draw.text((l_pill_x + 20, l_pill_y + 124), f"Active X: [{l_trigger_outer_left}px, {l_trigger_inner_right}px]", font=font_small, fill=(220, 225, 235, 255))

    # Left connector
    draw.line([(l_pill_x, l_pill_y + 80), (l_trigger_inner_right, l_pill_y + 80)], fill=red_border_col, width=4)
    draw.ellipse([l_trigger_inner_right - 7, l_pill_y + 73, l_trigger_inner_right + 7, l_pill_y + 87], fill=red_border_col)

    # Right Side Callout Pill
    r_pill_w = 340
    r_pill_h = 160
    r_pill_x = r_trigger_inner_left - r_pill_w - 30 # 907 - 340 - 30 = 537
    r_pill_y = 1040
    draw.rounded_rectangle([r_pill_x, r_pill_y, r_pill_x + r_pill_w, r_pill_y + r_pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=3)
    draw.text((r_pill_x + 20, r_pill_y + 16), "NEXT PAGE", font=font_badge, fill=(255, 65, 65, 255))
    draw.text((r_pill_x + 20, r_pill_y + 54), f"Width: {total_w}px (54dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw.text((r_pill_x + 20, r_pill_y + 94), f"• Inside: {internal_w}px | Out: +{beyond_w}px", font=font_code, fill=(255, 170, 160, 255))
    draw.text((r_pill_x + 20, r_pill_y + 124), f"Active X: [{r_trigger_inner_left}px, {r_trigger_outer_right}px]", font=font_small, fill=(220, 225, 235, 255))

    # Right connector
    draw.line([(r_pill_x + r_pill_w, r_pill_y + 80), (r_trigger_inner_left, r_pill_y + 80)], fill=red_border_col, width=4)
    draw.ellipse([r_trigger_inner_left - 7, r_pill_y + 73, r_trigger_inner_left + 7, r_pill_y + 87], fill=red_border_col)

    # Bottom Explanation Card
    b_card_y = 1880
    b_card_h = 340
    draw.rounded_rectangle([50, b_card_y, W - 50, b_card_y + b_card_h], radius=24, fill=(15, 16, 20, 250), outline=(255, 55, 55, 200), width=3)
    draw.text((80, b_card_y + 24), "Fixed Parity & Premature Exit Prevention (Folder.java):", font=font_badge, fill=(255, 255, 255, 255))

    specs = [
        ("• Dual Trigger Detection:", "Checks EITHER finger touch point (d.x) OR icon center (visualX) so neither lags."),
        ("• Fixed Positive Extension:", "outRect expanded symmetrically to phone bezels. Eliminates negative shrinking."),
        ("• Never Closes on Paging:", "Holding or hovering at card edges or bezel will NEVER trigger premature onDragExit."),
        ("• True Desktop Exit:", "Dragging vertically (UP onto desktop, DOWN onto dock) exits cleanly and immediately."),
        ("• Safe Reorder Buffers:", "18px gap on Left (Col 0) and 17px on Right (Col 2) protects apps from accidental flip.")
    ]

    cur_y = b_card_y + 72
    for title, desc in specs:
        draw.text((80, cur_y), title, font=font_code, fill=(255, 115, 105, 255))
        draw.text((360, cur_y), desc, font=font_body, fill=(225, 230, 240, 255))
        cur_y += 48

    # Composite final image
    final_img = Image.alpha_composite(img, overlay)
    
    out_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\folder_both_sides_triggers_red.png"
    final_img.save(out_path, "PNG")
    print(f"Saved {out_path}")

if __name__ == "__main__":
    generate_both_sides_overlay()
