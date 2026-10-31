"""Relocate the copied VLC Mach-O dependency graph without a system VLC install."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
magic = {b'\xcf\xfa\xed\xfe', b'\xfe\xed\xfa\xcf', b'\xca\xfe\xba\xbe', b'\xbe\xba\xfe\xca', b'\xca\xfe\xba\xbf'}
files = []
for path in root.rglob('*'):
    if path.is_file() and not path.is_symlink():
        with path.open('rb') as stream:
            if stream.read(4) in magic:
                files.append(path)
by_name = {}
for path in root.rglob('*'):
    if path.is_file():
        by_name.setdefault(path.name, set()).add(path.resolve())
for path in files:
    lines = subprocess.check_output(['otool', '-L', str(path)], text=True).splitlines()[1:]
    for line in lines:
        dependency = line.strip().split(' (compatibility version', 1)[0]
        if dependency.startswith(('/usr/lib/', '/System/Library/')):
            continue
        candidates = by_name.get(Path(dependency).name, set())
        if len(candidates) != 1:
            raise RuntimeError(f'Unresolved/ambiguous dependency of {path.name}: {dependency}')
        target = next(iter(candidates))
        if target == path.resolve():
            continue
        if not target.is_relative_to(root):
            raise RuntimeError(f'Dependency escapes the bundle: {target}')
        relative = os.path.relpath(target, path.parent)
        replacement = '@loader_path/' + relative
        if dependency != replacement:
            subprocess.run(['install_name_tool', '-change', dependency, replacement, str(path)], check=True)
    subprocess.run(['codesign', '--force', '--sign', '-', str(path)], check=True)
    subprocess.run(['codesign', '--verify', '--strict', str(path)], check=True)
print(f'Relocated and ad-hoc signed {len(files)} VLC Mach-O files')
