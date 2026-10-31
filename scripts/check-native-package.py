"""Validate and extract our generated platform ZIP before native integration tests."""
from pathlib import Path
import hashlib
import io
import os
import sys
import xml.etree.ElementTree as ET
import zipfile

target = sys.argv[1]
os_name, arch = target.split('-', 1)
archive = Path('build/distributions') / f'AnyPlayer-0.1.0-{target}.zip'
destination = Path('build/installed').resolve()
if destination.exists():
    raise RuntimeError('Use a clean installation test directory')
with zipfile.ZipFile(archive) as package:
    assert package.testzip() is None, 'Corrupt ZIP member'
    names = package.namelist()
    by_lower_name = {name.lower(): name for name in names}
    assert 'AnyPlayer/native/dependencies.json' in names
    extension = '.exe' if os_name == 'windows' else ''
    for tool in ('ffmpeg', 'ffprobe', 'mediainfo'):
        member = package.getinfo(by_lower_name[f'AnyPlayer/native/bin/{tool}{extension}'.lower()])
        assert member.file_size > 10000, f'Empty/stub runtime: {tool}'
        if os_name != 'windows':
            assert (member.external_attr >> 16) & 0o111, f'Executable permission missing: {tool}'
    descriptors = []
    for name in names:
        if name.startswith('AnyPlayer/lib/') and name.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(package.read(name))) as jar:
                if 'META-INF/plugin.xml' in jar.namelist():
                    descriptors.append(ET.fromstring(jar.read('META-INF/plugin.xml')))
    descriptor, = descriptors
    assert descriptor.findtext('version') == f'0.1.0-{target}'
    dependencies = [node.text for node in descriptor.findall('depends')]
    assert f'com.intellij.modules.os.{os_name}' in dependencies, dependencies
    assert f'com.intellij.modules.arch.{arch}' in dependencies, dependencies
    for member in package.infolist():
        path = (destination / member.filename).resolve()
        assert path.is_relative_to(destination), 'Unsafe ZIP path'
        package.extract(member, destination)
        if os_name != 'windows' and not member.is_dir():
            os.chmod(path, (member.external_attr >> 16) & 0o777 or 0o644)
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix('.zip.sha256').write_text(f'{digest}  {archive.name}\n')
print(f'Checked {archive.name}: {digest}')
