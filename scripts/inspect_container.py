import xml.etree.ElementTree as ET

tree = ET.parse("test_results/window_dump.xml")
for parent in tree.iter():
    for child in parent:
        if "LauncherAppWidgetHostView" in child.attrib.get("class", ""):
            print("Children of this CellLayout ShortcutsAndWidgetsContainer:")
            for idx, c in enumerate(parent):
                txt = c.attrib.get("text") or c.attrib.get("content-desc")
                cls = c.attrib.get("class").split(".")[-1]
                b = c.attrib.get("bounds")
                print(f"  [{idx}] {cls}: '{txt}' at {b}")
