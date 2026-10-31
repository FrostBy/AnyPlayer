"""Validate and extract our generated platform ZIP before native integration tests."""
from pathlib import Path
import hashlib
import io
import os
import sys
import xml.etree.ElementTree as ET
import zipfile

mojibake = set()
for character in '\u2192\u2013\u2014\u2026\u2212\u00d7\u2018\u2019\u201c\u201d\u2022\u00a0\u00a9\u22ef\u25b6':
    for encoding in ('cp1251', 'cp1252', 'latin1'):
        try:
            mojibake.add(character.encode('utf-8').decode(encoding))
        except UnicodeError:
            pass

text_extensions = {'.md', '.xml', '.kt', '.kts', '.json', '.yaml', '.yml', '.py', '.ps1', '.sh', '.js', '.css', '.html', '.svg', '.properties'}
source_files = list(Path('.').glob('*'))
for directory in ('src', 'docs', 'openspec', 'scripts', '.github'):
    source_files.extend(Path(directory).rglob('*'))
for path in source_files:
    if path.is_file() and path.suffix in text_extensions:
        text = path.read_text(encoding='utf-8-sig')
        assert not any(marker in text for marker in mojibake), f'Corrupt text encoding: {path}'

target = sys.argv[1]
os_name, arch = target.split('-', 1)
archive = Path('build/distributions') / f'AnyPlayer-0.1.0-{target}.zip'
destination = Path(sys.argv[2] if len(sys.argv) > 2 else 'build/installed').resolve()
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
                assert not any(n.startswith('dev/anyplayer/') for n in jar.namelist()), 'Obsolete classes in the package'
                if 'META-INF/plugin.xml' in jar.namelist():
                    xml = jar.read('META-INF/plugin.xml').decode('utf-8')
                    assert not any(marker in xml for marker in mojibake), 'Corrupt packaged descriptor encoding'
                    descriptors.append(ET.fromstring(xml))
    descriptor, = descriptors
    assert descriptor.findtext('id') == 'me.romanovsky.anyplayer', 'Unexpected Plugin ID'
    assert descriptor.findtext('name') == 'Any Player', 'Unexpected plugin name'
    assert descriptor.findtext('vendor') == 'Romanovsky', 'Unexpected vendor'
    assert descriptor.findtext('change-notes', '').strip(), 'Missing release notes'
    assert descriptor.find('actions/action').get('text') == 'Open Media\u2026', 'Corrupt Open Media label'
    for node in descriptor.iter():
        for attribute in ('implementationClass', 'implementation', 'class'):
            if attribute in node.attrib:
                assert node.attrib[attribute].startswith('me.romanovsky.anyplayer.'), 'Unexpected extension namespace'
    assert descriptor.findtext('version') == f'0.1.0-{target}'
    compatibility = descriptor.find('idea-version')
    assert compatibility is not None and compatibility.get('since-build') == '261', 'Unexpected minimum IDE build'
    assert compatibility.get('until-build') is None, 'Artificial maximum IDE build in the package'
    assert compatibility.get('strict-until-build') is None, 'Unexpected strict maximum IDE build'
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
archive.with_suffix('.zip.sha256').write_text(f'{digest}  {archive.name}\n', encoding='utf-8')
print(f'Checked {archive.name}: {digest}')
