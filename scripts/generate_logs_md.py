import json
import html
import os

PAYLOAD_PATH = "test_results/test_execution_payload.json"
WORKSPACE_DIR = "test_results"
ARTIFACT_DIR = r"C:\Users\lavan\.gemini\antigravity\brain\5f65ae67-4b2b-4c22-a759-9899820eb034"

PARTS = [
    ("part1", 1, 28, "Part 1 (TC-01 to TC-28)"),
    ("part2", 29, 57, "Part 2 (TC-29 to TC-57)"),
    ("part3", 58, 85, "Part 3 (TC-58 to TC-85)"),
]

def render_part(data_subset, part_name, start_id, end_id, title_suffix):
    md = []
    md.append(f"# Test Cases Execution Logs & Instructions - {title_suffix}\n\n")
    md.append("This document contains the complete test instructions, expected behavior, and full logcat proof for test cases executed on the **iHome Launcher** (`withoutQuickstepDebug` build variant) on `emulator-5554`.\n\n")
    
    # Navigation between parts
    nav_links = []
    for p_id, p_start, p_end, p_title in PARTS:
        filename = f"test_cases_logs_{p_id}.md"
        if p_id == part_name:
            nav_links.append(f"**{p_title}**")
        else:
            nav_links.append(f"[{p_title}]({filename})")
    md.append("**File Navigation:** " + " | ".join(nav_links) + "\n\n")

    # In-page index
    md.append("### In-Page Test Index\n\n")
    index_links = [f"[`{x['id']}`](#{x['id'].lower()})" for x in data_subset]
    for i in range(0, len(index_links), 10):
        md.append(" | ".join(index_links[i:i+10]) + "\n\n")

    md.append("\n---\n\n")
    md.append("## Test Execution Table\n\n")

    md.append('<table style="width: 100%; border-collapse: collapse; font-family: -apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, sans-serif;">\n')
    md.append('  <thead>\n')
    md.append('    <tr style="background-color: #1f242c; color: #ffffff; text-align: left;">\n')
    md.append('      <th style="width: 10%; padding: 12px 10px; border: 1px solid #30363d;">Test ID</th>\n')
    md.append('      <th style="width: 32%; padding: 12px 10px; border: 1px solid #30363d;">Test Instruction</th>\n')
    md.append('      <th style="width: 58%; padding: 12px 10px; border: 1px solid #30363d;">Full Log Proof</th>\n')
    md.append('    </tr>\n')
    md.append('  </thead>\n')
    md.append('  <tbody>\n')

    for idx, tc in enumerate(data_subset):
        tid = tc.get("id", "")
        anchor = tid.lower()
        name = tc.get("name", "")
        cat = tc.get("category", "")
        desc = tc.get("description", "")
        expected = tc.get("expected_behavior", "")
        raw_log = tc.get("log_proof", "")
        
        row_bg = "#f6f8fa" if idx % 2 == 0 else "#ffffff"
        escaped_log = html.escape(raw_log) if raw_log else "<em>No logcat proof captured</em>"

        md.append(f'    <tr id="{anchor}" style="background-color: {row_bg}; border: 1px solid #d0d7de;">\n')
        
        # Column 1: Test ID
        md.append(f'      <td valign="top" style="padding: 10px; border: 1px solid #d0d7de; font-weight: bold; white-space: nowrap;">\n')
        md.append(f'        <span style="font-size: 15px;">{tid}</span>\n')
        md.append(f'      </td>\n')

        # Column 2: Test Instruction & Details
        md.append(f'      <td valign="top" style="padding: 10px; border: 1px solid #d0d7de; font-size: 13px; line-height: 1.5;">\n')
        md.append(f'        <strong style="font-size: 14px; color: #0969da;">{name}</strong><br>\n')
        md.append(f'        <span style="font-size: 11px; color: #6e7781; text-transform: uppercase; font-weight: bold;">{cat}</span><hr style="margin: 6px 0; border: none; border-top: 1px solid #e1e4e8;">\n')
        md.append(f'        <strong>Instruction / Action:</strong><br>{desc}<br><br>\n')
        md.append(f'        <strong>Expected Behavior:</strong><br>{expected}\n')
        md.append(f'      </td>\n')

        # Column 3: Full Log
        md.append(f'      <td valign="top" style="padding: 10px; border: 1px solid #d0d7de;">\n')
        log_line_count = raw_log.count("\n") + 1 if raw_log else 0
        md.append(f'        <div style="font-size: 11px; color: #57606a; margin-bottom: 4px;">Logcat Output ({log_line_count} lines, {len(raw_log):,} characters):</div>\n')
        md.append(f'        <pre style="max-height: 380px; overflow-y: auto; background-color: #0d1117; color: #c9d1d9; padding: 10px; border-radius: 6px; font-family: \'SFMono-Regular\', Consolas, \'Liberation Mono\', Menlo, monospace; font-size: 11px; line-height: 1.4; white-space: pre-wrap; word-break: break-all; margin: 0;"><code>{escaped_log}</code></pre>\n')
        md.append(f'      </td>\n')

        md.append('    </tr>\n')

    md.append('  </tbody>\n')
    md.append('</table>\n')

    return "".join(md)

def main():
    if not os.path.exists(PAYLOAD_PATH):
        raise FileNotFoundError(f"Cannot find {PAYLOAD_PATH}")

    with open(PAYLOAD_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    def get_sort_num(item):
        tid = item.get("id", "")
        if "-" in tid:
            num = tid.split("-")[1]
            if num.isdigit():
                return int(num)
        return 999
    data.sort(key=get_sort_num)

    os.makedirs(WORKSPACE_DIR, exist_ok=True)
    os.makedirs(ARTIFACT_DIR, exist_ok=True)

    for part_name, start_id, end_id, title_suffix in PARTS:
        subset = [
            x for x in data
            if start_id <= get_sort_num(x) <= end_id
        ]
        content = render_part(subset, part_name, start_id, end_id, title_suffix)

        ws_path = os.path.join(WORKSPACE_DIR, f"test_cases_logs_{part_name}.md")
        with open(ws_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Generated {ws_path} ({len(subset)} tests, {len(content):,} bytes)")

        art_path = os.path.join(ARTIFACT_DIR, f"test_cases_logs_{part_name}.md")
        with open(art_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Generated {art_path} ({len(subset)} tests, {len(content):,} bytes)")

if __name__ == "__main__":
    main()
