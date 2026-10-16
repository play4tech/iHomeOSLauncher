#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "Applying patch to libs_systemui..."
cd "$ROOT_DIR/libs_systemui"
git apply "$ROOT_DIR/patches/libs_systemui.patch"
echo "libs_systemui patch successfully applied!"
