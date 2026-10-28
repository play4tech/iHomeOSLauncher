import os
from PIL import Image, ImageDraw, ImageFont

def generate_homescreen_triggers_overlay():
    src_img_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\rmx_homescreen_live.png"
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

    # Homescreen trigger dimensions (72px / 24dp on 3.0x density)
    trigger_w = 72 # 24dp * 3.0 density
    left_trigger_x = trigger_w           # 72px
    right_trigger_x = W - trigger_w      # 1008px

    # Workspace vertical active bounds (below status bar, above dock glass)
    ws_top = 110
    ws_bottom = 2050

    # 1. Red fill layers
    # Left red fill
    draw_ov = ImageDraw.Draw(overlay)
    draw_ov.rectangle([0, ws_top, left_trigger_x, ws_bottom], fill=(255, 38, 38, 185))
    # Right red fill
    draw_ov.rectangle([right_trigger_x, ws_top, W, ws_bottom], fill=(255, 38, 38, 185))

    # 2. Structural dividing lines
    red_border_col = (255, 45, 45, 255)
    # Left dividing border line
    draw_ov.line([(left_trigger_x, ws_top), (left_trigger_x, ws_bottom)], fill=red_border_col, width=4)
    # Right dividing border line
    draw_ov.line([(right_trigger_x, ws_top), (right_trigger_x, ws_bottom)], fill=red_border_col, width=4)

    # 3. Direction chevrons
    # Left: <<<
    for cy in [450, 750, 1050, 1350, 1650]:
        mid_x = left_trigger_x / 2 # 36
        draw_ov.line([(mid_x + 9, cy - 13), (mid_x - 7, cy)], fill=(255, 255, 255, 255), width=4)
        draw_ov.line([(mid_x + 9, cy + 13), (mid_x - 7, cy)], fill=(255, 255, 255, 255), width=4)

    # Right: >>>
    for cy in [450, 750, 1050, 1350, 1650]:
        mid_x = right_trigger_x + (trigger_w / 2) # 1044
        draw_ov.line([(mid_x - 9, cy - 13), (mid_x + 7, cy)], fill=(255, 255, 255, 255), width=4)
        draw_ov.line([(mid_x - 9, cy + 13), (mid_x + 7, cy)], fill=(255, 255, 255, 255), width=4)

    # Top Header Banner
    banner_y = 120
    banner_h = 150
    draw_ov.rounded_rectangle([50, banner_y, W - 50, banner_y + banner_h], radius=24, fill=(15, 16, 20, 248), outline=(255, 55, 55, 220), width=3)
    draw_ov.text((80, banner_y + 18), "Homescreen Page Flip Triggers (Left & Right)", font=font_title, fill=(255, 255, 255, 255))
    draw_ov.text((80, banner_y + 76), f"Symmetrical 72px (24dp) trigger width on BOTH sides for effortless paging", font=font_subtitle, fill=(255, 120, 110, 255))
    draw_ov.text((80, banner_y + 113), f"Left Trigger: X in [0px, {left_trigger_x}px]   |   Right Trigger: X in [{right_trigger_x}px, {W}px]", font=font_code, fill=(240, 200, 190, 255))

    # Left Side Callout Pill
    l_pill_w = 390
    l_pill_h = 160
    l_pill_x = left_trigger_x + 30 # 72 + 30 = 102
    l_pill_y = 1000
    draw_ov.rounded_rectangle([l_pill_x, l_pill_y, l_pill_x + l_pill_w, l_pill_y + l_pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=3)
    draw_ov.text((l_pill_x + 20, l_pill_y + 16), "GOING BACK TRIGGER (PREV PAGE)", font=font_badge, fill=(255, 65, 65, 255))
    draw_ov.text((l_pill_x + 20, l_pill_y + 54), f"Trigger Width: {trigger_w}px (24dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw_ov.text((l_pill_x + 20, l_pill_y + 94), f"• Active Range: Screen X <= {left_trigger_x}px", font=font_code, fill=(255, 170, 160, 255))
    draw_ov.text((l_pill_x + 20, l_pill_y + 124), "• Action: Flips back to Previous Page", font=font_small, fill=(220, 225, 235, 255))

    # Left connector
    draw_ov.line([(l_pill_x, l_pill_y + 80), (left_trigger_x, l_pill_y + 80)], fill=red_border_col, width=4)
    draw_ov.ellipse([left_trigger_x - 7, l_pill_y + 73, left_trigger_x + 7, l_pill_y + 87], fill=red_border_col)

    # Right Side Callout Pill
    r_pill_w = 390
    r_pill_h = 160
    r_pill_x = right_trigger_x - r_pill_w - 30 # 1008 - 390 - 30 = 588
    r_pill_y = 1000
    draw_ov.rounded_rectangle([r_pill_x, r_pill_y, r_pill_x + r_pill_w, r_pill_y + r_pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=3)
    draw_ov.text((r_pill_x + 20, r_pill_y + 16), "NEXT PAGE TRIGGER", font=font_badge, fill=(255, 65, 65, 255))
    draw_ov.text((r_pill_x + 20, r_pill_y + 54), f"Trigger Width: {trigger_w}px (24dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw_ov.text((r_pill_x + 20, r_pill_y + 94), f"• Active Range: Screen X >= {right_trigger_x}px", font=font_code, fill=(255, 170, 160, 255))
    draw_ov.text((r_pill_x + 20, r_pill_y + 124), "• Action: Flips forward to Next Page", font=font_small, fill=(220, 225, 235, 255))

    # Right connector
    draw_ov.line([(r_pill_x + r_pill_w, r_pill_y + 80), (right_trigger_x, r_pill_y + 80)], fill=red_border_col, width=4)
    draw_ov.ellipse([right_trigger_x - 7, r_pill_y + 73, right_trigger_x + 7, r_pill_y + 87], fill=red_border_col)

    # Column 0 & Column 3 safety buffer callout
    safe_y = 1380
    safe_w = 540
    safe_h = 130
    safe_x = (W - safe_w) // 2
    draw_ov.rounded_rectangle([safe_x, safe_y, safe_x + safe_w, safe_y + safe_h], radius=18, fill=(18, 20, 26, 245), outline=(52, 199, 89, 220), width=3)
    draw_ov.text((safe_x + 20, safe_y + 16), "SAFE REORDER CLEARANCE (85px BUFFER)", font=font_badge, fill=(52, 199, 89, 255))
    draw_ov.text((safe_x + 20, safe_y + 58), "Column 0 center: X = 157px | Column 3 center: X = 923px", font=font_body, fill=(230, 235, 245, 255))
    draw_ov.text((safe_x + 20, safe_y + 90), "Icons in edge columns reorder cleanly without accidental page flips.", font=font_code, fill=(180, 240, 190, 255))

    # Bottom Explanation Card
    b_card_y = 1720
    b_card_h = 320
    draw_ov.rounded_rectangle([50, b_card_y, W - 50, b_card_y + b_card_h], radius=24, fill=(15, 16, 20, 250), outline=(255, 55, 55, 200), width=3)
    draw_ov.text((80, b_card_y + 24), "Homescreen Drag Paging Architecture (Workspace.java):", font=font_badge, fill=(255, 255, 255, 255))

    specs = [
        ("• Left Trigger (Go Back):", f"Screen X in [0px, {left_trigger_x}px]. Flips to previous page when dragged to left edge."),
        ("• Right Trigger (Next Page):", f"Screen X in [{right_trigger_x}px, {W}px]. Flips to next page when dragged to right edge."),
        ("• Dual Detection Active:", "Checks EITHER touch point (d.x) OR icon center (centerX) so paging feels effortless."),
        ("• Timer Duration:", "SpringLoadedDragController triggers page snap after 500ms hover in red zone."),
        ("• Upgraded Reach:", "Expanded from cramped 48px to generous 72px (+50% wider target).")
    ]

    cur_y = b_card_y + 70
    for title, desc in specs:
        draw_ov.text((80, cur_y), title, font=font_code, fill=(255, 115, 105, 255))
        draw_ov.text((380, cur_y), desc, font=font_body, fill=(225, 230, 240, 255))
        cur_y += 46

    # Composite final image
    final_img = Image.alpha_composite(img, overlay)
    
    out_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\homescreen_both_sides_triggers_red.png"
    final_img.save(out_path, "PNG")
    print(f"Saved {out_path}")

if __name__ == "__main__":
    generate_homescreen_triggers_overlay()
