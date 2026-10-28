import os, shutil
from PIL import Image, ImageDraw, ImageFont

def generate_overlays():
    artifact_dir = r"C:\Users\lavan\.gemini\antigravity\brain\04e7aaf5-24a1-4b73-bf66-7cb3f0c4038c"
    os.makedirs(artifact_dir, exist_ok=True)

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

    # =========================================================================
    # 1. HOMESCREEN PAGE TRIGGER OVERLAY (homescreen_page_flip_triggers_red.png)
    # =========================================================================
    hs_img_path = r"homescreen_current.png"
    if not os.path.exists(hs_img_path):
        hs_img_path = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\rmx_homescreen_live.png"
    hs_img = Image.open(hs_img_path).convert("RGBA")
    W, H = hs_img.size # 1080 x 2400

    hs_overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_hs = ImageDraw.Draw(hs_overlay)

    # Dynamic Edge threshold: 69px (23dp on 3.0x density)
    trigger_w = 69
    left_trigger_x = trigger_w
    right_trigger_x = W - trigger_w

    ws_top = 110
    ws_bottom = 2050

    # Red fill layers
    red_fill = (255, 38, 38, 175)
    red_border_col = (255, 45, 45, 255)

    draw_hs.rectangle([0, ws_top, left_trigger_x, ws_bottom], fill=red_fill)
    draw_hs.rectangle([right_trigger_x, ws_top, W, ws_bottom], fill=red_fill)

    # Dividing border lines
    draw_hs.line([(left_trigger_x, ws_top), (left_trigger_x, ws_bottom)], fill=red_border_col, width=4)
    draw_hs.line([(right_trigger_x, ws_top), (right_trigger_x, ws_bottom)], fill=red_border_col, width=4)

    # Direction chevrons
    for cy in [450, 750, 1050, 1350, 1650]:
        mid_x = left_trigger_x / 2
        draw_hs.line([(mid_x + 9, cy - 13), (mid_x - 7, cy)], fill=(255, 255, 255, 255), width=4)
        draw_hs.line([(mid_x + 9, cy + 13), (mid_x - 7, cy)], fill=(255, 255, 255, 255), width=4)

        mid_rx = right_trigger_x + (trigger_w / 2)
        draw_hs.line([(mid_rx - 9, cy - 13), (mid_rx + 7, cy)], fill=(255, 255, 255, 255), width=4)
        draw_hs.line([(mid_rx - 9, cy + 13), (mid_rx + 7, cy)], fill=(255, 255, 255, 255), width=4)

    # Top Header Banner
    banner_y = 120
    banner_h = 150
    draw_hs.rounded_rectangle([50, banner_y, W - 50, banner_y + banner_h], radius=24, fill=(15, 16, 20, 248), outline=(255, 55, 55, 220), width=3)
    draw_hs.text((80, banner_y + 18), "Homescreen Screen-Switch Trigger Zones", font=font_title, fill=(255, 255, 255, 255))
    draw_hs.text((80, banner_y + 76), "Symmetrical 69px (23dp) trigger zone on BOTH screen edges", font=font_subtitle, fill=(255, 120, 110, 255))
    draw_hs.text((80, banner_y + 113), f"Previous Screen: X in [0px, {left_trigger_x}px]   |   Next Screen: X in [{right_trigger_x}px, {W}px]", font=font_code, fill=(240, 200, 190, 255))

    # Left Callout Pill
    l_pill_w = 390
    l_pill_h = 160
    l_pill_x = left_trigger_x + 30
    l_pill_y = 960
    draw_hs.rounded_rectangle([l_pill_x, l_pill_y, l_pill_x + l_pill_w, l_pill_y + l_pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=3)
    draw_hs.text((l_pill_x + 20, l_pill_y + 16), "PREVIOUS SCREEN TRIGGER", font=font_badge, fill=(255, 65, 65, 255))
    draw_hs.text((l_pill_x + 20, l_pill_y + 54), f"Trigger Width: {trigger_w}px (24dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw_hs.text((l_pill_x + 20, l_pill_y + 94), f"• Active Range: Touch X <= {left_trigger_x}px", font=font_code, fill=(255, 170, 160, 255))
    draw_hs.text((l_pill_x + 20, l_pill_y + 124), "• Action: Flips back to Previous Screen", font=font_small, fill=(220, 225, 235, 255))
    draw_hs.line([(l_pill_x, l_pill_y + 80), (left_trigger_x, l_pill_y + 80)], fill=red_border_col, width=4)
    draw_hs.ellipse([left_trigger_x - 7, l_pill_y + 73, left_trigger_x + 7, l_pill_y + 87], fill=red_border_col)

    # Right Callout Pill
    r_pill_w = 390
    r_pill_h = 160
    r_pill_x = right_trigger_x - r_pill_w - 30
    r_pill_y = 960
    draw_hs.rounded_rectangle([r_pill_x, r_pill_y, r_pill_x + r_pill_w, r_pill_y + r_pill_h], radius=20, fill=(18, 20, 26, 250), outline=red_border_col, width=3)
    draw_hs.text((r_pill_x + 20, r_pill_y + 16), "NEXT SCREEN TRIGGER", font=font_badge, fill=(255, 65, 65, 255))
    draw_hs.text((r_pill_x + 20, r_pill_y + 54), f"Trigger Width: {trigger_w}px (24dp)", font=font_dim, fill=(255, 255, 255, 255))
    draw_hs.text((r_pill_x + 20, r_pill_y + 94), f"• Active Range: Touch X >= {right_trigger_x}px", font=font_code, fill=(255, 170, 160, 255))
    draw_hs.text((r_pill_x + 20, r_pill_y + 124), "• Action: Flips forward to Next Screen", font=font_small, fill=(220, 225, 235, 255))
    draw_hs.line([(r_pill_x + r_pill_w, r_pill_y + 80), (right_trigger_x, r_pill_y + 80)], fill=red_border_col, width=4)
    draw_hs.ellipse([right_trigger_x - 7, r_pill_y + 73, right_trigger_x + 7, r_pill_y + 87], fill=red_border_col)

    # Column 0 & Column 3 safety buffer callout
    safe_y = 1380
    safe_w = 560
    safe_h = 130
    safe_x = (W - safe_w) // 2
    draw_hs.rounded_rectangle([safe_x, safe_y, safe_x + safe_w, safe_y + safe_h], radius=18, fill=(18, 20, 26, 245), outline=(52, 199, 89, 220), width=3)
    draw_hs.text((safe_x + 20, safe_y + 16), "SAFE REORDER CLEARANCE (85px BUFFER)", font=font_badge, fill=(52, 199, 89, 255))
    draw_hs.text((safe_x + 20, safe_y + 58), "Column 0 center: X = 157px | Column 3 center: X = 923px", font=font_body, fill=(230, 235, 245, 255))
    draw_hs.text((safe_x + 20, safe_y + 90), "Icons in edge columns reorder cleanly without accidental page flips.", font=font_code, fill=(180, 240, 190, 255))

    # Bottom Explanation Card
    b_card_y = 1720
    b_card_h = 320
    draw_hs.rounded_rectangle([50, b_card_y, W - 50, b_card_y + b_card_h], radius=24, fill=(15, 16, 20, 250), outline=(255, 55, 55, 200), width=3)
    draw_hs.text((80, b_card_y + 24), "Homescreen Screen Flip Architecture (Workspace.java):", font=font_badge, fill=(255, 255, 255, 255))

    specs_hs = [
        ("• Previous Screen (Left):", f"Screen X in [0px, {left_trigger_x}px]. Flips to previous screen when app is dragged to left edge."),
        ("• Next Screen (Right):", f"Screen X in [{right_trigger_x}px, {W}px]. Flips to next screen when app is dragged to right edge."),
        ("• Dual Trigger Detection:", "Checks EITHER touch finger (d.x) OR icon center (centerX) so flipping feels natural."),
        ("• Hover Delay Timer:", "SpringLoadedDragController snaps to target screen after 500ms hover in red zone."),
        ("• Active Vertical Range:", "Active across the entire workspace height (Y: 110px to 2050px) between status bar and dock.")
    ]

    cur_y = b_card_y + 70
    for title, desc in specs_hs:
        draw_hs.text((80, cur_y), title, font=font_code, fill=(255, 115, 105, 255))
        draw_hs.text((380, cur_y), desc, font=font_body, fill=(225, 230, 240, 255))
        cur_y += 46

    final_hs = Image.alpha_composite(hs_img, hs_overlay)
    out_hs_file = os.path.join(artifact_dir, "homescreen_page_flip_triggers_red.png")
    final_hs.save(out_hs_file, "PNG")
    print(f"Saved {out_hs_file}")

    # =========================================================================
    # 2. FOLDER PAGE TRIGGER OVERLAY (folder_page_flip_triggers_red.png)
    # =========================================================================
    fo_src = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2\rmx_folder_open_check.png"
    if os.path.exists(fo_src):
        fo_img = Image.open(fo_src).convert("RGBA")
        f_overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))

        f_left, f_top, f_right, f_bottom = 94, 472, 985, 1819
        f_w = f_right - f_left
        f_h = f_bottom - f_top
        f_corner_r = f_w * 0.225

        internal_w = 78
        beyond_w = 84

        r_trigger_inner_left = f_right - internal_w
        r_trigger_card_edge = f_right
        r_trigger_outer_right = min(W - 10, f_right + beyond_w)

        l_trigger_inner_right = f_left + internal_w
        l_trigger_card_edge = f_left
        l_trigger_outer_left = max(10, f_left - beyond_w)

        mask_folder = Image.new("L", (W, H), 0)
        mf_draw = ImageDraw.Draw(mask_folder)
        mf_draw.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, fill=255)

        red_inner_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        draw_ril = ImageDraw.Draw(red_inner_layer)
        draw_ril.rectangle([r_trigger_inner_left, f_top, r_trigger_card_edge, f_bottom], fill=(255, 38, 38, 190))
        draw_ril.rectangle([l_trigger_card_edge, f_top, l_trigger_inner_right, f_bottom], fill=(255, 38, 38, 190))

        empty_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        clipped_inner_red = Image.composite(red_inner_layer, empty_layer, mask_folder)
        f_overlay.paste(clipped_inner_red, (0, 0), clipped_inner_red)

        draw_fo = ImageDraw.Draw(f_overlay)
        # Beyond gutter (outside squircle card)
        draw_fo.rectangle([r_trigger_card_edge, f_top + 100, r_trigger_outer_right, f_bottom - 100], fill=(255, 60, 60, 140))
        draw_fo.rectangle([l_trigger_outer_left, f_top + 100, l_trigger_card_edge, f_bottom - 100], fill=(255, 60, 60, 140))

        # Dividing lines
        draw_fo.line([(r_trigger_inner_left, f_top + 10), (r_trigger_inner_left, f_bottom - 10)], fill=red_border_col, width=4)
        draw_fo.line([(l_trigger_inner_right, f_top + 10), (l_trigger_inner_right, f_bottom - 10)], fill=red_border_col, width=4)

        # Folder card outline
        draw_fo.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, outline=(255, 255, 255, 180), width=3)

        # Chevrons
        for cy in [700, 950, 1200, 1450]:
            draw_fo.line([(l_trigger_inner_right - 25, cy - 14), (l_trigger_inner_right - 45, cy)], fill=(255, 255, 255, 255), width=4)
            draw_fo.line([(l_trigger_inner_right - 25, cy + 14), (l_trigger_inner_right - 45, cy)], fill=(255, 255, 255, 255), width=4)

            draw_fo.line([(r_trigger_inner_left + 25, cy - 14), (r_trigger_inner_left + 45, cy)], fill=(255, 255, 255, 255), width=4)
            draw_fo.line([(r_trigger_inner_left + 25, cy + 14), (r_trigger_inner_left + 45, cy)], fill=(255, 255, 255, 255), width=4)

        # Top Header Banner
        draw_fo.rounded_rectangle([50, banner_y, W - 50, banner_y + banner_h], radius=24, fill=(15, 16, 20, 248), outline=(255, 55, 55, 220), width=3)
        draw_fo.text((80, banner_y + 18), "Inside Folder Screen-Switch Trigger Zones", font=font_title, fill=(255, 255, 255, 255))
        draw_fo.text((80, banner_y + 76), "Symmetrical 78px card-interior + 84px beyond-card gutter on BOTH edges", font=font_subtitle, fill=(255, 120, 110, 255))
        draw_fo.text((80, banner_y + 113), f"Previous Screen: X in [{l_trigger_outer_left}px, {l_trigger_inner_right}px]   |   Next Screen: X in [{r_trigger_inner_left}px, {r_trigger_outer_right}px]", font=font_code, fill=(240, 200, 190, 255))

        # Bottom Explanation Card
        draw_fo.rounded_rectangle([50, 1860, W - 50, 1860 + 340], radius=24, fill=(15, 16, 20, 250), outline=(255, 55, 55, 200), width=3)
        draw_fo.text((80, 1860 + 24), "Inside-Folder Screen Flip Architecture (Folder.java):", font=font_badge, fill=(255, 255, 255, 255))

        specs_fo = [
            ("• Previous Folder Screen:", f"Active X in [{l_trigger_outer_left}px, {l_trigger_inner_right}px]. Flips to previous folder page."),
            ("• Next Folder Screen:", f"Active X in [{r_trigger_inner_left}px, {r_trigger_outer_right}px]. Flips to next folder page."),
            ("• Beyond-Card Gutter:", "Extends 84px past squircle card edge to bezels so flipping never triggers premature desktop exit."),
            ("• Desktop Exit Protection:", "Horizontal drag stays inside folder; only vertical drag (UP to desktop / DOWN to dock) exits."),
            ("• Reorder Icon Protection:", "18px safe margin inside Column 0 and Column 2 ensures side icons never flip pages by accident.")
        ]

        cur_y = 1860 + 72
        for title, desc in specs_fo:
            draw_fo.text((80, cur_y), title, font=font_code, fill=(255, 115, 105, 255))
            draw_fo.text((370, cur_y), desc, font=font_body, fill=(225, 230, 240, 255))
            cur_y += 48

        final_fo = Image.alpha_composite(fo_img, f_overlay)
        out_fo_file = os.path.join(artifact_dir, "folder_page_flip_triggers_red.png")
        final_fo.save(out_fo_file, "PNG")
        print(f"Saved {out_fo_file}")

if __name__ == "__main__":
    generate_overlays()
