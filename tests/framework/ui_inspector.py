"""
UI Inspector module for dynamically locating icons, folders, and grid slots.
Eliminates brittle hardcoded coordinates by querying live view hierarchies.
"""

import xml.etree.ElementTree as ET
import re
from typing import List, Dict, Any, Optional

class UIInspector:
    def __init__(self, device):
        self.device = device
        self.d = device.d

    def get_screen_elements(self) -> List[Dict[str, Any]]:
        """
        Dumps hierarchy and returns a list of visible elements with their text,
        resourceId, class, center coordinates, and bounds.
        """
        try:
            xml_str = self.d.dump_hierarchy()
            root = ET.fromstring(xml_str)
        except Exception:
            return []

        elements = []
        for node in root.iter("node"):
            text = node.attrib.get("text", "")
            content_desc = node.attrib.get("content-desc", "")
            res_id = node.attrib.get("resource-id", "")
            cls = node.attrib.get("class", "")
            bounds = node.attrib.get("bounds", "")
            
            label = text if text else content_desc
            if not label and not res_id:
                continue

            match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                cx = (x1 + x2) // 2
                cy = (y1 + y2) // 2
                elements.append({
                    "name": label,
                    "resource_id": res_id,
                    "class": cls,
                    "center": (cx, cy),
                    "bounds": (x1, y1, x2, y2)
                })
        return elements

    def find_element(self, query: str) -> Optional[Dict[str, Any]]:
        """Finds an element whose name or resource_id contains query (case-insensitive)."""
        elements = self.get_screen_elements()
        query_lower = query.lower()
        for elem in elements:
            if query_lower in elem["name"].lower() or query_lower in elem["resource_id"].lower():
                return elem
        return None

    def find_all_folders(self) -> List[Dict[str, Any]]:
        """Returns all folder preview icons on the current screen."""
        elements = self.get_screen_elements()
        folders = []
        status_bar_y = int(self.device.height * 0.055)
        dock_y = int(self.device.height * 0.84)
        max_w = int(self.device.width * 0.35)
        max_h = int(self.device.height * 0.20)
        for e in elements:
            name = e["name"].strip().lower()
            res_id = e["resource_id"].lower()
            cls = e["class"]
            w = e["bounds"][2] - e["bounds"][0]
            h = e["bounds"][3] - e["bounds"][1]
            cy = e["center"][1]
            cx = e["center"][0]
            if "systemui" in res_id:
                continue
            if status_bar_y < cy < dock_y and 40 < w < max_w and 40 < h < max_h:
                if name.startswith("folder:") or "folder" in name or "folder_icon" in res_id or "FolderIcon" in cls:
                    # Deduplicate if another node for this folder was already added
                    if not any(abs(cx - u["center"][0]) < 60 and abs(cy - u["center"][1]) < 60 for u in folders):
                        folders.append(e)
        return folders

    def get_workspace_icons(self) -> List[Dict[str, Any]]:
        """
        Returns visible desktop app icons sorted in reading order (top-to-bottom, left-to-right).
        Excludes widgets, dock, status bar, and folders.
        """
        elements = self.get_screen_elements()
        icons = []
        status_bar_y = int(self.device.height * 0.055)
        dock_y = int(self.device.height * 0.84)
        max_w = int(self.device.width * 0.35)
        max_h = int(self.device.height * 0.20)
        row_bucket = max(1, int(self.device.height * 0.08))

        for e in elements:
            cx, cy = e["center"]
            w = e["bounds"][2] - e["bounds"][0]
            h = e["bounds"][3] - e["bounds"][1]
            name = e["name"].strip()
            name_lower = name.lower()
            res_id = e["resource_id"].lower()
            cls = e["class"]
            cls_lower = cls.lower()
            
            if "systemui" in res_id:
                continue

            # Exclude full-screen root views, widgets, search widgets, dock, and status bar
            if status_bar_y < cy < dock_y and 20 < cx < self.device.width - 20 and 40 < w < max_w and 40 < h < max_h:
                if not name_lower.startswith("folder:") and "folder" not in res_id and "Folder" not in cls:
                    if "widget" not in res_id and "widgethost" not in cls_lower and "appwidget" not in cls_lower and "clockwidget" not in cls_lower and "search" not in res_id and name_lower != "google":
                        # Skip clock text view inside clock widget
                        if name_lower == "clock" and ("clockwidget" in cls_lower or cy < int(self.device.height * 0.35)):
                            continue
                        if name and name_lower != "home":
                            icons.append(e)

        # Sort in reading order (row-by-row, top-to-bottom, left-to-right)
        icons.sort(key=lambda x: (x["center"][1] // row_bucket, x["center"][0]))
        return icons

    def get_folder_items(self) -> List[Dict[str, Any]]:
        """Returns visible icons inside an open folder card."""
        elements = self.get_screen_elements()
        folder_items = []
        card_top = int(self.device.height * 0.12)
        card_bottom = int(self.device.height * 0.88)
        max_w = int(self.device.width * 0.35)
        max_h = int(self.device.height * 0.20)
        row_bucket = max(1, int(self.device.height * 0.06))

        for e in elements:
            w = e["bounds"][2] - e["bounds"][0]
            h = e["bounds"][3] - e["bounds"][1]
            cy = e["center"][1]
            res_id = e["resource_id"].lower()
            if "systemui" in res_id:
                continue
            # In-folder icons are bounded within the central folder card
            if card_top < cy < card_bottom and int(self.device.width * 0.05) < e["center"][0] < int(self.device.width * 0.95):
                if 40 < w < max_w and 40 < h < max_h and e["name"].strip():
                    folder_items.append(e)
        folder_items.sort(key=lambda x: (x["center"][1] // row_bucket, x["center"][0]))
        return folder_items
