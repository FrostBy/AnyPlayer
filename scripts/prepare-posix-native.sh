#!/usr/bin/env bash
set -euo pipefail
target=${1:?target required}
case "$target" in mac-x86_64|mac-arm64|linux-x86_64|linux-arm64) ;; *) echo "Unsupported target: $target" >&2; exit 1;; esac
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
mkdir -p "native/$target" native/downloads
stage=$(mktemp -d "$root/native/$target/.preparing-XXXXXXXX")
work=$(mktemp -d "${TMPDIR:-/tmp}/anyplayer-native.XXXXXXXX")
cleanup() {
  if [[ -d "$work/mount" ]] && [[ "$target" == mac-* ]]; then hdiutil detach "$work/mount" >/dev/null 2>&1 || true; fi
  case "$stage" in "$root/native/$target/".preparing-*) rm -rf -- "$stage";; esac
  case "$work" in "${TMPDIR:-/tmp}/"anyplayer-native.*) rm -rf -- "$work";; esac
}
trap cleanup EXIT
mkdir -p "$stage/bin" "$stage/vlc/lib" "$stage/licenses"
fetch() {
  local component=$1 url sha kind file
  IFS=$'\t' read -r url sha kind < <(python3 - "$target" "$component" <<'PY'
import json,sys
d=json.load(open('native/dependencies.json'))[sys.argv[1]][sys.argv[2]]
print(d['url'],d['sha256'],d['archive'],sep='\t')
PY
  )
  file="$root/native/downloads/$component-$sha.$kind"
  if [[ ! -f "$file" ]]; then curl --fail --location --retry 3 "$url" -o "$file"; fi
  [[ $(shasum -a 256 "$file" | cut -d' ' -f1) == "$sha" ]] || { echo "Checksum mismatch: $component" >&2; return 1; }
  printf '%s\n' "$file"
}
if [[ "$target" == mac-* ]]; then
  expected=x86_64; [[ "$target" == mac-arm64 ]] && expected=arm64
  [[ $(uname -m) == "$expected" ]] || { echo 'Use a matching native macOS runner' >&2; exit 1; }
  archive=$(fetch vlc)
  mkdir -p "$work/mount"
  hdiutil attach -readonly -nobrowse -mountpoint "$work/mount" "$archive" >/dev/null
  cp -a "$work/mount/VLC.app/Contents/MacOS/." "$stage/vlc/"
  if [[ -d "$work/mount/VLC.app/Contents/Frameworks" ]]; then cp -a "$work/mount/VLC.app/Contents/Frameworks" "$stage/vlc/"; fi
  if [[ -d "$stage/vlc/plugins" ]]; then mkdir -p "$stage/vlc/lib/vlc"; mv "$stage/vlc/plugins" "$stage/vlc/lib/vlc/plugins"; fi
  if [[ -d "$stage/vlc/share" ]]; then mv "$stage/vlc/share" "$stage/vlc/lib/vlc/share"; fi
  find "$work/mount/VLC.app/Contents/Resources" -iname '*license*' -o -iname 'copying*' | while IFS= read -r file; do
    [[ ! -f "$file" ]] || cp "$file" "$stage/licenses/$(basename "$file")"
  done
  hdiutil detach "$work/mount" >/dev/null
  brew install ffmpeg media-info dylibbundler
  python3 - "$target" <<'PY'
import json,subprocess
import sys
d=json.load(open('native/dependencies.json'))
for name in ('ffmpeg','mediainfo'):
    item=d[sys.argv[1]][name]
    f=json.loads(subprocess.check_output(['brew','info','--json=v2',item['formula']]))['formulae'][0]
    if f['versions']['stable'] != item['version']: raise SystemExit('Update/review the native lock before changing '+name)
PY
  cp -L "$(brew --prefix)/bin/ffmpeg" "$stage/bin/ffmpeg"
  cp -L "$(brew --prefix)/bin/ffprobe" "$stage/bin/ffprobe"
  cp -L "$(brew --prefix)/bin/mediainfo" "$stage/bin/mediainfo"
  dylibbundler -b -x "$stage/bin/ffmpeg" -x "$stage/bin/ffprobe" -x "$stage/bin/mediainfo" -d "$stage/bin/lib/" -p '@executable_path/lib/'
  python3 scripts/relocate-macos-vlc.py "$stage/vlc"
  find "$stage/bin" -type f | while IFS= read -r file; do
    if file "$file" | grep -q 'Mach-O'; then codesign --force --sign - "$file"; codesign --verify --strict "$file"; fi
  done
  brew info --json=v2 --installed > "$stage/licenses/homebrew-inventory.json"
  for formula in ffmpeg media-info; do
    prefix=$(brew --prefix "$formula")
    for file in "$prefix"/COPYING* "$prefix"/LICENSE*; do [[ ! -f "$file" ]] || cp "$file" "$stage/licenses/$formula-$(basename "$file")"; done
  done
  for file in "$stage/bin/ffmpeg" "$stage/bin/ffprobe" "$stage/bin/mediainfo" "$stage/vlc/lib/libvlc.dylib"; do lipo -verify_arch "$expected" "$file"; done
else
  archive=$(fetch ffmpeg)
  mkdir -p "$work/ffmpeg"
  tar -xJf "$archive" --strip-components=1 -C "$work/ffmpeg"
  cp "$work/ffmpeg/bin/ffmpeg" "$work/ffmpeg/bin/ffprobe" "$stage/bin/"
  cp "$work/ffmpeg"/LICENSE* "$stage/licenses/" 2>/dev/null || true
  export ANYPLAYER_STAGE="$stage" ANYPLAYER_TARGET="$target"
  docker run --rm -e ANYPLAYER_TARGET -v "$root:/workspace" -w /workspace rockylinux:8.10 bash scripts/prepare-linux-runtime.sh "${stage#"$root/"}"
fi
cp native/dependencies.json "$stage/dependencies.json"
cp THIRD_PARTY_NOTICES.md "$stage/licenses/THIRD_PARTY_NOTICES.md"
chmod 755 "$stage/bin/ffmpeg" "$stage/bin/ffprobe" "$stage/bin/mediainfo"
"$stage/bin/ffmpeg" -hide_banner -decoders > "$work/decoders" 2>&1
for codec in webp_anim apng gif; do grep -Eq "[[:space:]]$codec[[:space:]]" "$work/decoders" || { echo "Missing decoder: $codec" >&2; exit 1; }; done
"$stage/bin/ffprobe" -version > "$stage/licenses/ffprobe-version.txt"
"$stage/bin/mediainfo" --Version > "$stage/licenses/mediainfo-version.txt"
find "$stage/vlc" -name plugins.dat -delete
python3 - "$stage" "$target" <<'PY'
import hashlib,json,sys
from pathlib import Path
r=Path(sys.argv[1]); files=[]
for p in sorted(r.rglob('*')):
    if p.is_file(): files.append({'path':str(p.relative_to(r)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'bytes':p.stat().st_size})
(r/'inventory.json').write_text(json.dumps({'target':sys.argv[2],'files':files},indent=2))
PY
final="$root/native/$target/native"
backup="$root/native/$target/.previous-native"
[[ ! -e "$backup" ]] || { echo "Review existing backup: $backup" >&2; exit 1; }
[[ ! -d "$final" ]] || mv "$final" "$backup"
if ! mv "$stage" "$final"; then [[ ! -d "$backup" ]] || mv "$backup" "$final"; exit 1; fi
case "$backup" in "$root/native/$target/".previous-native) rm -rf -- "$backup";; esac
echo "Native runtime prepared: $target"
