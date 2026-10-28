import os, shutil
from PIL import Image, ImageDraw, ImageFont

def generate_folder_next_page_overlay():
    src_img_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\rmx_folder_open_check.png"
    img = Image.open(src_img_path).convert("RGBA")
    W, H = img.size # 1080 x 2400

    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    font_title = ImageFont.truetype(font_bold_path, 42)
    font_subtitle = ImageFont.truetype(font_reg_path, 25)
    font_badge = ImageFont.truetype(font_bold_path, 26)
    font_body = ImageFont.truetype(font_reg_path, 21)
    font_code = ImageFont.truetype(font_bold_path, 21)
    font_dim = ImageFont.truetype(font_bold_path, 32)
    font_small = ImageFont.truetype(font_reg_path, 19)

    # Folder card bounds (Realme 1080p display)
    f_left, f_top, f_right, f_bottom = 94, 472, 985, 1819
    f_w = f_right - f_left # 891px
    f_h = f_bottom - f_top # 1347px
    f_corner_r = f_w * 0.225 # ~200px

    # Trigger widths
    internal_w = 72 # 24dp * 3.0 density inside the card
    beyond_w = 42   # 14dp * 3.0 density extending beyond squircle edge
    total_w = internal_w + beyond_w # 114px

    trigger_inner_left = f_right - internal_w # 913px
    trigger_card_edge = f_right              # 985px
    trigger_outer_right = f_right + beyond_w  # 1027px

    # 1. Folder squircle mask for the inside portion
    mask_folder = Image.new("L", (W, H), 0)
    mf_draw = ImageDraw.Draw(mask_folder)
    mf_draw.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, fill=255)

    # Inside red fill layer (clipped to squircle)
    red_inner_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_ril = ImageDraw.Draw(red_inner_layer)
    draw_ril.rectangle([trigger_inner_left, f_top, trigger_card_edge, f_bottom], fill=(255, 38, 38, 190))

    empty_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    clipped_inner_red = Image.composite(red_inner_layer, empty_layer, mask_folder)
    overlay.paste(clipped_inner_red, (0, 0), clipped_inner_red)

    # 2. Outside extension red layer (extending beyond the squircle edge)
    # Highlighted in a slightly lighter translucent red with distinct dashed boundary
    ext_mask = Image.new("L", (W, H), 0)
    ext_draw = ImageDraw.Draw(ext_mask)
    # Extend smoothly along the card's vertical height with rounded outer corners
    ext_draw.rounded_rectangle([trigger_inner_left, f_top, trigger_outer_right, f_bottom], radius=f_corner_r, fill=255)
    
    red_outer_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_rol = ImageDraw.Draw(red_outer_layer)
    # Outside red fill
    draw_rol.rectangle([trigger_card_edge, f_top, trigger_outer_right, f_bottom], fill=(255, 59, 48, 160))

    clipped_outer_red = Image.composite(red_outer_layer, empty_layer, ext_mask)
    overlay.paste(clipped_outer_red, (0, 0), clipped_outer_red)

    draw = ImageDraw.Draw(overlay)

    # 3. Structural boundary outlines
    # White folder squircle edge
    draw.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, outline=(255, 255, 255, 170), width=3)

    # Internal dividing line (starts the red trigger zone inside card at X=913)
    red_border_col = (255, 45, 45, 255)
    draw.line([(trigger_inner_left, f_top + 15), (trigger_inner_left, f_bottom - 15)], fill=red_border_col, width=4)

    # Squircle boundary divider inside the red zone (dashed/semi-translucent to show where card ends)
    for seg_y in range(f_top + 20, f_bottom - 20, 24):
        draw.line([(trigger_card_edge, seg_y), (trigger_card_edge, seg_y + 14)], fill=(255, 255, 255, 230), width=3)

    # Outer limit line (where trigger zone ends at X=1027 before true workspace exit)
    ext_border_col = (255, 100, 90, 255)
    for seg_y in range(f_top + 30, f_bottom - 30, 20):
        draw.line([(trigger_outer_right, seg_y), (trigger_outer_right, seg_y + 10)], fill=ext_border_col, width=3)

    # Chevrons inside the red zone pointing right
    for cy in [700, 930, 1150, 1370, 1580]:
        mid_x = (trigger_inner_left + trigger_outer_right) / 2
        draw.line([(mid_x - 10, cy - 14), (mid_x + 8, cy)], fill=(255, 255, 255, 255), width=4)
        draw.line([(mid_x - 10, cy + 14), (mid_x + 8, cy)], fill=(255, 255, 255, 255), width=4)

    # Top Header Banner
    banner_y = 90
    banner_h = 150
    draw.rounded_rectangle([50, banner_y, W - 50, banner_y + banner_h], radius=24, fill=(15, 16, 20, 248), outline=(255, 55, 55, 220), width=3)
    draw.text((80, banner_y + 20), "Folder Next Page Trigger: Extended Beyond Card", font=font_title, fill=(255, 255, 255, 255))
    draw.text((80, banner_y + 80), f"Total Trigger Width: {total_w}px ({internal_w}px inside + {beyond_w}px beyond squircle edge)", font=font_subtitle, fill=(255, 120, 110, 255))
    draw.text((80, banner_y + 115), f"Active Red Range: Screen X in [{trigger_inner_left}px, {trigger_outer_right}px]", font=font_code, fill=(240, 200, 190, 255))

    # Prominent Callout Pill on Right
    pill_w = 460
    pill_h = 190
    pill_x = trigger_inner_left - pill_w - 30 # 913 - 460 - 30 = 423
    pill_y = 1040

    draw.rounded_rectangle([pill_x, pill_y, pill_x + pill_w, pill_y + pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=4)
    draw.text((pill_x + 22, pill_y + 18), "NEXT PAGE TRIGGER ZONE", font=font_badge, fill=(255, 65, 65, 255))
    draw.text((pill_x + 22, pill_y + 58), f"Total Width: {total_w}px (38dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw.text((pill_x + 22, pill_y + 104), f"• Inside Card: {internal_w}px (24dp)", font=font_code, fill=(255, 170, 160, 255))
    draw.text((pill_x + 22, pill_y + 134), f"• Beyond Squircle: +{beyond_w}px (14dp)", font=font_code, fill=(255, 170, 160, 255))
    draw.text((pill_x + 22, pill_y + 160), f"Active Screen X: {trigger_inner_left}px -> {trigger_outer_right}px", font=font_small, fill=(220, 225, 235, 255))

    # Pointer line & anchor dot connecting Callout to Red Zone
    draw.line([(pill_x + pill_w, pill_y + 95), (trigger_inner_left, pill_y + 95)], fill=red_border_col, width=4)
    draw.ellipse([trigger_inner_left - 7, pill_y + 88, trigger_inner_left + 7, pill_y + 102], fill=red_border_col)

    # Top Dimension Brackets showing Internal (72px) and Beyond (42px)
    b_y1 = f_top - 35
    # Total width bracket
    draw.line([(trigger_inner_left, b_y1), (trigger_outer_right, b_y1)], fill=(255, 65, 65, 255), width=3)
    draw.line([(trigger_inner_left, b_y1 - 10), (trigger_inner_left, b_y1 + 10)], fill=(255, 65, 65, 255), width=3)
    draw.line([(trigger_outer_right, b_y1 - 10), (trigger_outer_right, b_y1 + 10)], fill=(255, 65, 65, 255), width=3)
    draw.line([(trigger_card_edge, b_y1 - 6), (trigger_card_edge, b_y1 + 6)], fill=(255, 255, 255, 255), width=2)
    draw.text((trigger_inner_left + 15, b_y1 - 42), f"{total_w}px Total ({internal_w}px in + {beyond_w}px out)", font=font_badge, fill=(255, 90, 90, 255))

    # Safe rearrangement zone label (Column 2 side margin)
    safe_callout_y = 1380
    safe_pill_w = 420
    safe_pill_h = 130
    safe_pill_x = trigger_inner_left - safe_pill_w - 30
    draw.rounded_rectangle([safe_pill_x, safe_callout_y, safe_pill_x + safe_pill_w, safe_callout_y + safe_pill_h], radius=18, fill=(18, 20, 26, 245), outline=(52, 199, 89, 220), width=3)
    draw.text((safe_pill_x + 20, safe_callout_y + 16), "ICON SAFETY GAP (23px)", font=font_badge, fill=(52, 199, 89, 255))
    draw.text((safe_pill_x + 20, safe_callout_y + 58), "Column 2 apps end at X = 890px.", font=font_body, fill=(230, 235, 245, 255))
    draw.text((safe_pill_x + 20, safe_callout_y + 90), f"Safe rearrange margin: {trigger_inner_left - 890}px before red trigger", font=font_code, fill=(180, 240, 190, 255))

    # Bottom Explanation Card
    b_card_y = 1880
    b_card_h = 340
    draw.rounded_rectangle([50, b_card_y, W - 50, b_card_y + b_card_h], radius=24, fill=(15, 16, 20, 250), outline=(255, 55, 55, 200), width=3)
    draw.text((80, b_card_y + 24), "Updated Extended Trigger Architecture (Folder.java):", font=font_badge, fill=(255, 255, 255, 255))

    specs = [
        ("• Internal Trigger:", f"{internal_w}px (24dp) inside card. Wide enough for easy targeting."),
        ("• Beyond Squircle:", f"+{beyond_w}px (14dp) extending past the squircle edge onto the border."),
        ("• Total Red Width:", f"{total_w}px total trigger reach (Screen X in [{trigger_inner_left}px, {trigger_outer_right}px])."),
        ("• HitRect Extension:", "getHitRectRelativeToDragLayer expanded by 14dp so card never exits prematurely."),
        ("• True Workspace Exit:", f"Only occurs when dragged past X > {trigger_outer_right}px onto homescreen.")
    ]

    cur_y = b_card_y + 72
    for title, desc in specs:
        draw.text((80, cur_y), title, font=font_code, fill=(255, 115, 105, 255))
        draw.text((340, cur_y), desc, font=font_body, fill=(225, 230, 240, 255))
        cur_y += 48

    # Composite final image
    final_img = Image.alpha_composite(img, overlay)
    
    out_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\folder_next_page_trigger_red.png"
    final_img.save(out_path, "PNG")
    print(f"Saved {out_path}")

if __name__ == "__main__":
    generate_folder_next_page_overlay()
