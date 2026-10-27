import math, os, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_overlays():
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    font_title = ImageFont.truetype(font_bold_path, 42)
    font_subtitle = ImageFont.truetype(font_reg_path, 26)
    font_badge = ImageFont.truetype(font_bold_path, 26)
    font_small = ImageFont.truetype(font_reg_path, 20)

    # =========================================================================
    # 1. HOMESCREEN PAGE TRIGGER OVERLAY (homescreen_page_trigger_zones.png)
    # =========================================================================
    hs_img = Image.open("homescreen_live.png").convert("RGBA")
    W, H = hs_img.size # 1080 x 2400

    hs_overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_hs = ImageDraw.Draw(hs_overlay)

    # Dynamic Edge threshold: padding (48px) + (cellWidth 246px * 0.10) + 2px = 74.6px -> 75px
    edge_width = 75
    bezel_min = 26
    red_fill = (255, 59, 48, 140)
    red_fill_deep = (255, 59, 48, 185)
    red_border = (255, 59, 48, 255)

    # Left translucent red strip (dynamic gutter 75px with 26px inner core)
    draw_hs.rectangle([0, 0, edge_width, H], fill=red_fill)
    draw_hs.rectangle([0, 0, bezel_min, H], fill=red_fill_deep)
    draw_hs.line([(edge_width, 0), (edge_width, H)], fill=red_border, width=5)

    # Right translucent red strip (dynamic gutter 75px with 26px inner core)
    draw_hs.rectangle([W - edge_width, 0, W, H], fill=red_fill)
    draw_hs.rectangle([W - bezel_min, 0, W, H], fill=red_fill_deep)
    draw_hs.line([(W - edge_width, 0), (W - edge_width, H)], fill=red_border, width=5)

    # Top Header Banner Card
    banner_y = 100
    banner_h = 135
    draw_hs.rounded_rectangle([60, banner_y, W - 60, banner_y + banner_h], radius=24, fill=(18, 19, 24, 240), outline=(255, 59, 48, 180), width=3)
    draw_hs.text((90, banner_y + 22), "Homescreen Dynamic Page Flip Zones", font=font_title, fill=(255, 255, 255, 255))
    draw_hs.text((90, banner_y + 78), "Grid-Relative Clamped Dynamic: padding (48px) + (cellWidth * 0.10) + 2px", font=font_subtitle, fill=(255, 120, 110, 255))

    # Left Callout Pill
    l_pill_y = 920
    draw_hs.rounded_rectangle([edge_width + 20, l_pill_y, edge_width + 330, l_pill_y + 115], radius=18, fill=(18, 19, 24, 245), outline=red_border, width=3)
    draw_hs.text((edge_width + 40, l_pill_y + 18), "< PREV PAGE", font=font_badge, fill=(255, 69, 58, 255))
    draw_hs.text((edge_width + 40, l_pill_y + 65), "Width: 75px (Dynamic +2px)", font=font_small, fill=(220, 225, 235, 255))

    # Connector line from left strip to pill
    draw_hs.line([(edge_width, l_pill_y + 57), (edge_width + 20, l_pill_y + 57)], fill=red_border, width=3)
    draw_hs.ellipse([edge_width - 6, l_pill_y + 51, edge_width + 6, l_pill_y + 63], fill=red_border)

    # Right Callout Pill
    r_pill_y = 920
    r_pill_w = 330
    draw_hs.rounded_rectangle([W - edge_width - r_pill_w - 20, r_pill_y, W - edge_width - 20, r_pill_y + 115], radius=18, fill=(18, 19, 24, 245), outline=red_border, width=3)
    draw_hs.text((W - edge_width - r_pill_w + 10, r_pill_y + 18), "NEXT PAGE >", font=font_badge, fill=(255, 69, 58, 255))
    draw_hs.text((W - edge_width - r_pill_w + 10, r_pill_y + 65), "Width: 75px (Dynamic +2px)", font=font_small, fill=(220, 225, 235, 255))

    # Connector line from right strip to pill
    draw_hs.line([(W - edge_width, r_pill_y + 57), (W - edge_width - 20, r_pill_y + 57)], fill=red_border, width=3)
    draw_hs.ellipse([W - edge_width - 6, r_pill_y + 51, W - edge_width + 6, r_pill_y + 63], fill=red_border)

    # Vertical repeating chevron arrows along the strips
    for y_pos in [450, 700, 1150, 1400, 1650, 1900]:
        # Left arrow <
        draw_hs.line([(edge_width - 22, y_pos - 16), (edge_width - 48, y_pos)], fill=(255, 255, 255, 240), width=4)
        draw_hs.line([(edge_width - 22, y_pos + 16), (edge_width - 48, y_pos)], fill=(255, 255, 255, 240), width=4)

        # Right arrow >
        draw_hs.line([(W - edge_width + 22, y_pos - 16), (W - edge_width + 48, y_pos)], fill=(255, 255, 255, 240), width=4)
        draw_hs.line([(W - edge_width + 22, y_pos + 16), (W - edge_width + 48, y_pos)], fill=(255, 255, 255, 240), width=4)

    # Bottom Information Card
    b_card_y = 2120
    b_card_h = 220
    draw_hs.rounded_rectangle([60, b_card_y, W - 60, b_card_y + b_card_h], radius=24, fill=(18, 19, 24, 245), outline=(255, 59, 48, 180), width=3)
    draw_hs.text((90, b_card_y + 24), "How Grid-Relative Dynamic Page Flip Works:", font=font_badge, fill=(255, 255, 255, 255))
    lines_hs = [
        "• Formula: edgePadding (48px) + (cellWidth * 0.10) + 2px = 75px.",
        "• Left threshold: touchX <= 75px (or <= 26px next to widgets) flips Left.",
        "• Right threshold: touchX >= 1005px (or >= 1054px next to widgets) flips Right."
    ]
    cur_y = b_card_y + 70
    for l in lines_hs:
        draw_hs.text((90, cur_y), l, font=font_small, fill=(225, 228, 235, 255))
        cur_y += 42

    final_hs = Image.alpha_composite(hs_img, hs_overlay)
    out_hs_path = r"d:\Code\OS_Launcher\homescreen_page_trigger_zones.png"
    final_hs.save(out_hs_path, "PNG")
    print(f"Saved {out_hs_path}")

    # =========================================================================
    # 2. INSIDE FOLDER TRIGGER OVERLAY (folder_page_trigger_zones.png)
    # =========================================================================
    f_img = Image.open("folder_live.png").convert("RGBA")
    f_overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # Exact Folder Card bounds: Left=94, Top=472, Right=985, Bottom=1819
    f_left, f_top, f_right, f_bottom = 94, 472, 985, 1819
    f_w = f_right - f_left # 891
    f_h = f_bottom - f_top # 1347
    f_corner_r = f_w * 0.225 # ~200px
    # Updated: Non-overlapping trigger width confined to outer card gutter (48px / 16dp)
    # Side icons start at local x = 95px, cell starts at 54px.
    # 48px ensures ZERO overlap with side icons!
    cell_overlap = 48

    # Folder squircle mask
    mask_folder = Image.new("L", (W, H), 0)
    mf_draw = ImageDraw.Draw(mask_folder)
    mf_draw.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, fill=255)

    # Red fill layer (Left & Right strips)
    f_red_source = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_frs = ImageDraw.Draw(f_red_source)
    draw_frs.rectangle([f_left, f_top, f_left + cell_overlap, f_bottom], fill=(255, 59, 48, 160))
    draw_frs.rectangle([f_right - cell_overlap, f_top, f_right, f_bottom], fill=(255, 59, 48, 160))

    # Composite using mask
    empty_layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    f_clipped_red = Image.composite(f_red_source, empty_layer, mask_folder)
    f_overlay.paste(f_clipped_red, (0, 0), f_clipped_red)

    # Draw crisp dividing lines inside the card
    draw_fo = ImageDraw.Draw(f_overlay)
    draw_fo.line([(f_left + cell_overlap, f_top + 10), (f_left + cell_overlap, f_bottom - 10)], fill=red_border, width=5)
    draw_fo.line([(f_right - cell_overlap, f_top + 10), (f_right - cell_overlap, f_bottom - 10)], fill=red_border, width=5)

    # Folder Card Outline
    draw_fo.rounded_rectangle([f_left, f_top, f_right, f_bottom], radius=f_corner_r, outline=(255, 255, 255, 150), width=3)

    # Top Header Banner
    draw_fo.rounded_rectangle([60, banner_y, W - 60, banner_y + banner_h], radius=24, fill=(18, 19, 24, 240), outline=(255, 59, 48, 180), width=3)
    draw_fo.text((90, banner_y + 22), "Folder Non-Overlapping Flip Zones", font=font_title, fill=(255, 255, 255, 255))
    draw_fo.text((90, banner_y + 78), "Confined to 48px card gutter: Zero overlap with side column icons", font=font_subtitle, fill=(255, 120, 110, 255))

    # Folder Left Callout Pill (outside the strip, pointing to it)
    fl_pill_y = 1100
    pill_w = 300
    draw_fo.rounded_rectangle([f_left + cell_overlap + 15, fl_pill_y, f_left + cell_overlap + 15 + pill_w, fl_pill_y + 110], radius=16, fill=(18, 19, 24, 245), outline=red_border, width=3)
    draw_fo.text((f_left + cell_overlap + 30, fl_pill_y + 16), "< PREV PAGE", font=font_badge, fill=(255, 69, 58, 255))
    draw_fo.text((f_left + cell_overlap + 30, fl_pill_y + 60), "Width: 48px (No overlap)", font=font_small, fill=(220, 225, 235, 255))
    # Connector line
    draw_fo.line([(f_left + cell_overlap, fl_pill_y + 55), (f_left + cell_overlap + 15, fl_pill_y + 55)], fill=red_border, width=3)
    draw_fo.ellipse([f_left + cell_overlap - 5, fl_pill_y + 50, f_left + cell_overlap + 5, fl_pill_y + 60], fill=red_border)

    # Folder Right Callout Pill (outside the strip, pointing to it)
    fr_pill_y = 1100
    draw_fo.rounded_rectangle([f_right - cell_overlap - 15 - pill_w, fr_pill_y, f_right - cell_overlap - 15, fr_pill_y + 110], radius=16, fill=(18, 19, 24, 245), outline=red_border, width=3)
    draw_fo.text((f_right - cell_overlap - pill_w + 5, fr_pill_y + 16), "NEXT PAGE >", font=font_badge, fill=(255, 69, 58, 255))
    draw_fo.text((f_right - cell_overlap - pill_w + 5, fr_pill_y + 60), "Width: 48px (No overlap)", font=font_small, fill=(220, 225, 235, 255))
    # Connector line
    draw_fo.line([(f_right - cell_overlap, fr_pill_y + 55), (f_right - cell_overlap - 15, fr_pill_y + 55)], fill=red_border, width=3)
    draw_fo.ellipse([f_right - cell_overlap - 5, fr_pill_y + 50, f_right - cell_overlap + 5, fr_pill_y + 60], fill=red_border)

    # Vertical repeating chevron arrows inside the red zones
    for y_pos in [750, 950, 1350, 1550]:
        # Left arrow < centered at f_left + 24
        mid_lx = f_left + 24
        draw_fo.line([(mid_lx + 10, y_pos - 14), (mid_lx - 10, y_pos)], fill=(255, 255, 255, 240), width=3)
        draw_fo.line([(mid_lx + 10, y_pos + 14), (mid_lx - 10, y_pos)], fill=(255, 255, 255, 240), width=3)

        # Right arrow > centered at f_right - 24
        mid_rx = f_right - 24
        draw_fo.line([(mid_rx - 10, y_pos - 14), (mid_rx + 10, y_pos)], fill=(255, 255, 255, 240), width=3)
        draw_fo.line([(mid_rx - 10, y_pos + 14), (mid_rx + 10, y_pos)], fill=(255, 255, 255, 240), width=3)

    # Bottom Information Card
    draw_fo.rounded_rectangle([60, b_card_y, W - 60, b_card_y + b_card_h], radius=24, fill=(18, 19, 24, 245), outline=(255, 59, 48, 180), width=3)
    draw_fo.text((90, b_card_y + 24), "How Updated Non-Overlapping Folder Paging Works:", font=font_badge, fill=(255, 255, 255, 255))
    lines_fo = [
        "• Confined to card margin: Trigger width = 48px (16dp), completely outside cells.",
        "• Left threshold: local x < 48px (Screen X <= 142px) flips Left.",
        "• Right threshold: local x > 843px (Screen X >= 937px) flips Right.",
        "• Side icons (x in [95px, 275px] and [616px, 796px]): 100% free of page-flip triggers!"
    ]
    cur_y = b_card_y + 65
    for l in lines_fo:
        draw_fo.text((90, cur_y), l, font=font_small, fill=(225, 228, 235, 255))
        cur_y += 38

    final_fo = Image.alpha_composite(f_img, f_overlay)
    out_fo_path = r"d:\Code\OS_Launcher\folder_page_trigger_zones.png"
    final_fo.save(out_fo_path, "PNG")
    print(f"Saved {out_fo_path}")

    # Copy to artifacts directory
    artifact_dir = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"
    shutil.copy(out_hs_path, os.path.join(artifact_dir, "homescreen_page_trigger_zones.png"))
    shutil.copy(out_fo_path, os.path.join(artifact_dir, "folder_page_trigger_zones.png"))
    print("Copied both overlay images to artifacts directory")

if __name__ == "__main__":
    create_overlays()
