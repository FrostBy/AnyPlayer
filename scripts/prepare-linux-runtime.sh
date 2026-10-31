#!/usr/bin/env bash
set -euo pipefail
stage=${1:?staging path required}
case "$stage" in native/linux-x86_64/.preparing-*|native/linux-arm64/.preparing-*) ;; *) echo 'Unsafe staging path' >&2; exit 1;; esac
stage="/workspace/$stage"
dnf install -y epel-release dnf-plugins-core
dnf config-manager --set-enabled powertools
dnf install -y https://download1.rpmfusion.org/free/el/rpmfusion-free-release-8.noarch.rpm
dnf install -y vlc-core-3.0.24 mediainfo libcurl patchelf python3 file
mkdir -p "$stage/vlc/lib/vlc" "$stage/licenses/rpm"
cp -a /usr/lib64/vlc/plugins "$stage/vlc/lib/vlc/"
cp -L /usr/lib64/libvlc.so.5 "$stage/vlc/lib/libvlc.so"
cp -L /usr/lib64/libvlccore.so.9 "$stage/vlc/lib/libvlccore.so"
cp /usr/bin/mediainfo "$stage/bin/mediainfo"
cp -L /usr/lib64/libcurl.so.4 "$stage/vlc/lib/libcurl.so.4"
python3 - "$stage" <<'PY'
from pathlib import Path
import shutil,subprocess,sys,json
r=Path(sys.argv[1]); lib=r/'vlc/lib'
pending=list((r/'vlc').rglob('*.so'))+[r/'bin/mediainfo',lib/'libcurl.so.4']
seen=set(); owners=set()
system={'libc.so.6','libm.so.6','libdl.so.2','libpthread.so.0','librt.so.1','libresolv.so.2','libutil.so.1','libanl.so.1'}
while pending:
    p=pending.pop()
    if p in seen: continue
    seen.add(p)
    out=subprocess.run(['ldd',str(p)],capture_output=True,text=True)
    if out.returncode: raise RuntimeError('Cannot inspect '+str(p)+': '+out.stderr)
    for line in out.stdout.splitlines():
        if 'not found' in line: raise RuntimeError('Missing dependency: '+line)
        fields=line.split()
        src=next((Path(x) for x in fields if x.startswith('/')),None)
        if not src or src.name in system or src.name.startswith('ld-linux'): continue
        dst=lib/src.name
        if not dst.exists(): shutil.copy2(src.resolve(),dst); pending.append(dst)
        owner=subprocess.run(['rpm','-qf',str(src)],capture_output=True,text=True).stdout.strip()
        if owner: owners.add(owner)
for p in seen:
    relative=__import__('os').path.relpath(lib,p.parent)
    subprocess.run(['patchelf','--force-rpath','--set-rpath','$ORIGIN/'+relative,str(p)],check=True)
for owner in sorted(owners):
    for directory in subprocess.run(['rpm','-ql',owner],capture_output=True,text=True).stdout.splitlines():
        if directory.startswith('/usr/share/licenses/') and Path(directory).is_file():
            dst=r/'licenses/rpm'/owner/Path(directory).name; dst.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(directory,dst)
(r/'licenses/rpm-packages.txt').write_text(subprocess.check_output(['rpm','-qa','--qf','%{NAME} %{VERSION}-%{RELEASE} %{ARCH} %{SOURCERPM}\n'],text=True))
PY
for file in "$stage/bin/mediainfo" "$stage/vlc/lib/libvlc.so"; do ldd "$file"; done
