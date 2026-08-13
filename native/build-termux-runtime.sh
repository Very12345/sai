#!/usr/bin/env bash
set -euo pipefail

ABI="${1:-aarch64}"
case "$ABI" in
  aarch64) ANDROID_ABI="arm64-v8a" ;;
  x86_64) ANDROID_ABI="x86_64" ;;
  *) echo "Unsupported Termux architecture: $ABI" >&2; exit 64 ;;
esac

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_ROOT="${PHONEAGENT_NATIVE_WORK_ROOT:-$REPO_ROOT/.native-work}"
TERMUX_PACKAGES_DIR="$WORK_ROOT/termux-packages"
TERMUX_PACKAGES_COMMIT="b75aa613b35f86abf0a54ed3541cb9d77fd87b54"
OUTPUT_DIR="$TERMUX_PACKAGES_DIR/output"
STAGE_DIR="$WORK_ROOT/stage-$ABI"
PREFIX_REL="data/data/com.phoneagent.app/files/usr"

mkdir -p "$WORK_ROOT"
if [[ ! -d "$TERMUX_PACKAGES_DIR/.git" ]]; then
  git clone --no-checkout https://github.com/termux/termux-packages.git "$TERMUX_PACKAGES_DIR"
  git -C "$TERMUX_PACKAGES_DIR" fetch --depth 1 origin "$TERMUX_PACKAGES_COMMIT"
  git -C "$TERMUX_PACKAGES_DIR" checkout --detach "$TERMUX_PACKAGES_COMMIT"
elif [[ "$(git -C "$TERMUX_PACKAGES_DIR" rev-parse HEAD)" != "$TERMUX_PACKAGES_COMMIT" ]]; then
  echo "Existing native worktree is on an unexpected revision; use a fresh PHONEAGENT_NATIVE_WORK_ROOT" >&2
  exit 1
fi

PROPERTIES="$TERMUX_PACKAGES_DIR/scripts/properties.sh"
python3 - "$PROPERTIES" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
old = 'TERMUX_APP__PACKAGE_NAME="com.termux"'
new = 'TERMUX_APP__PACKAGE_NAME="com.phoneagent.app"'
if new in text:
    raise SystemExit(0)
if old not in text:
    raise SystemExit(f"Expected package-name definition not found in {path}")
path.write_text(text.replace(old, new, 1))
PY

cd "$TERMUX_PACKAGES_DIR"
./build-package.sh -a "$ABI" -o "$OUTPUT_DIR" proot

rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR"
while IFS= read -r -d '' package; do
  dpkg-deb -x "$package" "$STAGE_DIR"
done < <(find "$OUTPUT_DIR" -maxdepth 1 -type f -name "*.deb" -print0)

PREFIX="$STAGE_DIR/$PREFIX_REL"
PROOT_BIN="$PREFIX/bin/proot"
LOADER="$PREFIX/libexec/proot/loader"
[[ -f "$PROOT_BIN" ]] || { echo "Built proot binary was not found" >&2; exit 1; }
[[ -f "$LOADER" ]] || { echo "Built PRoot loader was not found" >&2; exit 1; }

JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/$ANDROID_ABI"
ASSET_DIR="$REPO_ROOT/app/src/main/assets/runtime/$ANDROID_ABI"
mkdir -p "$JNI_DIR" "$ASSET_DIR"
install -m 755 "$PROOT_BIN" "$JNI_DIR/libproot.so"
install -m 644 "$LOADER" "$ASSET_DIR/loader"

for library in libtalloc.so libandroid-shmem.so; do
  source_path="$(find "$PREFIX/lib" -type f -name "$library" -o -type l -name "$library" | head -n 1)"
  [[ -n "$source_path" ]] || { echo "Missing runtime dependency: $library" >&2; exit 1; }
  install -m 755 -L "$source_path" "$JNI_DIR/$library"
done

sha256sum "$JNI_DIR"/*.so "$ASSET_DIR/loader" > "$WORK_ROOT/checksums-$ANDROID_ABI.txt"
echo "Native runtime staged for $ANDROID_ABI"
cat "$WORK_ROOT/checksums-$ANDROID_ABI.txt"
