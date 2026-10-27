import math, os, shutil
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def create_comparison():
    font_bold_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font_reg_path = r"C:\Windows\Fonts\segoeui.ttf"
    if not os.path.exists(font_bold_path):
        font_bold_path = r"C:\Windows\Fonts\arialbd.ttf"
        font_reg_path = r"C:\Windows\Fonts\arial.ttf"

    font_title = ImageFont.truetype(font_bold_path, 40)
    font_sub = ImageFont.truetype(font_reg_path, 24)
    font_h2 = ImageFont.truetype(font_bold_path, 28)
    font_body = ImageFont.truetype(font_reg_path, 17)
    font_badge = ImageFont.truetype(font_bold_path, 17)

    W, H = 1600, 1020
    img = Image.new("RGBA", (W, H), (20, 21, 24, 255))
    draw = ImageDraw.Draw(img)

    # Title
    draw.text((80, 45), "Hit Zone Analysis: Circular vs. Squircle Folder Creation", font=font_title, fill=(255, 255, 255, 255))
    draw.text((80, 100), "Why a Circular Trigger Zone Causes Corner Inconsistencies on Squircle App Icons", font=font_sub, fill=(160, 165, 175, 255))
    draw.line([(80, 145), (W - 80, 145)], fill=(45, 48, 56, 255), width=2)

    def render_panel(is_squircle, icon_size=330):
        scale = 2
        S = icon_size * scale
        icon_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        m = S * 0.08
        icon_box = [m, m, S - m, S - m]
        corner_r = (S - 2 * m) * 0.225
        c = (S / 2.0, S / 2.0)
        half_w = (S - 2 * m) / 2.0

        mask_icon = Image.new("L", (S, S), 0)
        md = ImageDraw.Draw(mask_icon)
        md.rounded_rectangle(icon_box, radius=corner_r, fill=255)

        s_img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        sd = ImageDraw.Draw(s_img)
        sd.rounded_rectangle([m + 8, m + 20, S - m - 8, S - m + 20], radius=corner_r, fill=(0, 0, 0, 120))
        s_img = s_img.filter(ImageFilter.GaussianBlur(radius=25 * scale))
        icon_img.paste(s_img, (0, 0), s_img)

        content = Image.new("RGBA", (S, S), (0, 122, 255, 255))
        cd = ImageDraw.Draw(content)

        if not is_squircle:
            fol_r = half_w * 0.65
            fol_box = [c[0] - fol_r, c[1] - fol_r, c[0] + fol_r, c[1] + fol_r]
            cd.ellipse(fol_box, fill=(255, 59, 48, 255), outline=(255, 255, 255, 230), width=int(4 * scale))
        else:
            fol_scale = 0.65
            fol_box = [c[0] - half_w * fol_scale, c[1] - half_w * fol_scale, c[0] + half_w * fol_scale, c[1] + half_w * fol_scale]
            fol_corner_r = corner_r * fol_scale
            cd.rounded_rectangle(fol_box, radius=fol_corner_r, fill=(255, 59, 48, 255), outline=(255, 255, 255, 230), width=int(4 * scale))

        masked = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        masked.paste(content, (0, 0), mask_icon)
        od = ImageDraw.Draw(masked)
        od.rounded_rectangle(icon_box, radius=corner_r, outline=(255, 255, 255, 80), width=int(3 * scale))
        icon_img.paste(masked, (0, 0), masked)

        return icon_img.resize((icon_size, icon_size), Image.Resampling.LANCZOS)

    # Panel 1: Current Circular Zone (Left)
    p1_x, p1_y = 70, 210
    p1_w, p1_h = 710, 730
    draw.rounded_rectangle([p1_x, p1_y, p1_x + p1_w, p1_y + p1_h], radius=20, fill=(30, 31, 36, 255), outline=(255, 59, 48, 120), width=2)
    
    # Header 1
    draw.rounded_rectangle([p1_x + 30, p1_y + 25, p1_x + 220, p1_y + 65], radius=12, fill=(255, 59, 48, 220))
    draw.text((p1_x + 45, p1_y + 32), "CURRENT AOSP", font=font_badge, fill=(255, 255, 255, 255))
    draw.text((p1_x + 240, p1_y + 28), "Circular Hit Zone", font=font_h2, fill=(255, 255, 255, 255))

    icon1 = render_panel(False, 320)
    img.paste(icon1, (p1_x + (p1_w - 320) // 2, p1_y + 80), icon1)

    # Bullet points below
    y_text = p1_y + 430
    bullets1 = [
        ("Corner Deadzone", "Flat edge margin is ~15px, but corner margin is ~45px (3x wider)."),
        ("Accidental Move-Aside", "Dropping on an icon corner unexpectedly moves the app aside."),
        ("Legacy AOSP Heritage", "Designed for circular Pixel icons (108dp circles), not iOS squircles."),
        ("Irregular Threshold", "User must steer 3x deeper when dragging from a diagonal angle.")
    ]
    for title, desc in bullets1:
        draw.ellipse([p1_x + 30, y_text + 6, p1_x + 38, y_text + 14], fill=(255, 59, 48, 255))
        draw.text((p1_x + 50, y_text), title + ": ", font=font_badge, fill=(255, 120, 110, 255))
        tb = draw.textbbox((0, 0), title + ": ", font=font_badge)
        tw = tb[2] - tb[0]
        draw.text((p1_x + 50 + tw, y_text), desc, font=font_body, fill=(225, 228, 235, 255))
        y_text += 48

    # Panel 2: Recommended Squircle Zone (Right)
    p2_x = 820
    draw.rounded_rectangle([p2_x, p1_y, p2_x + p1_w, p1_y + p1_h], radius=20, fill=(30, 31, 36, 255), outline=(48, 209, 88, 140), width=2)
    
    # Header 2
    draw.rounded_rectangle([p2_x + 30, p1_y + 25, p2_x + 240, p1_y + 65], radius=12, fill=(48, 209, 88, 220))
    draw.text((p2_x + 45, p1_y + 32), "RECOMMENDED", font=font_badge, fill=(0, 0, 0, 255))
    draw.text((p2_x + 260, p1_y + 28), "Squircle Hit Zone", font=font_h2, fill=(255, 255, 255, 255))

    icon2 = render_panel(True, 320)
    img.paste(icon2, (p2_x + (p1_w - 320) // 2, p1_y + 80), icon2)

    # Bullet points below
    y_text = p1_y + 430
    bullets2 = [
        ("Uniform Inset Margin", "Constant 18% margin along all 4 edges AND all 4 rounded corners."),
        ("iOS SpringBoard Parity", "Dropping anywhere on the visual icon face reliably creates a folder."),
        ("Zero Corner Ambiguity", "Eliminates false reorder triggers when dropping at an angle."),
        ("Visual-Physical Sync", "The touch hit target perfectly mirrors the rendered squircle shape.")
    ]
    for title, desc in bullets2:
        draw.ellipse([p2_x + 30, y_text + 6, p2_x + 38, y_text + 14], fill=(48, 209, 88, 255))
        draw.text((p2_x + 50, y_text), title + ": ", font=font_badge, fill=(48, 209, 88, 255))
        tb = draw.textbbox((0, 0), title + ": ", font=font_badge)
        tw = tb[2] - tb[0]
        draw.text((p2_x + 50 + tw, y_text), desc, font=font_body, fill=(225, 228, 235, 255))
        y_text += 48

    # Bottom summary
    draw.text((80, 965), "Conclusion: YES — The red folder creation zone SHOULD be a squircle. A circular zone causes unnatural corner drop failures on squircle icons.", font=font_body, fill=(255, 214, 10, 255))

    out_comp_path = r"d:\Code\OS_Launcher\squircle_vs_circle_comparison.png"
    img.save(out_comp_path, "PNG")
    print(f"Saved {out_comp_path}")

    artifact_dir = r"C:\Users\lavan\.gemini\antigravity\brain\54406007-f430-4773-8eba-d5d1b6116db2"
    shutil.copy(out_comp_path, os.path.join(artifact_dir, "squircle_vs_circle_comparison.png"))
    print("Copied comparison to artifacts directory")

if __name__ == "__main__":
    create_comparison()
