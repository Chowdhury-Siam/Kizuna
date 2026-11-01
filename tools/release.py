#!/usr/bin/env python3
"""Build the same versioned manifest consumed by both in-app updaters."""
import argparse
import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def versions(root=ROOT):
    android = (root / 'androidapp3/app/build.gradle.kts').read_text()
    windows = (root / 'server/DroidOSS.App/DroidOSS.App.csproj').read_text()
    version = re.search(r'versionName = "([^"]+)"', android)[1]
    code = int(re.search(r'versionCode = (\d+)', android)[1])
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version):
        raise ValueError('Stable releases require major.minor.patch without a prerelease suffix')
    if re.search(r'<Version>([^<]+)</Version>', windows)[1] != version:
        raise ValueError('Android and Windows versions must match')
    return version, code

def build_manifest(directory, repository, version, code):
    if not re.fullmatch(r'[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+', repository) or repository.split('/')[1] in ('.', '..'):
        raise ValueError('Invalid repository')
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', version) or not 0 < code <= 2100000000:
        raise ValueError('Invalid release version')
    assets = {}
    for platform, name in [('android', f'Kizuna-v{version}-android.apk'), ('windows', f'Kizuna-v{version}-Setup.exe')]:
        path = directory / name
        size = path.stat().st_size
        if not 0 < size <= 512 * 1024 * 1024:
            raise ValueError('Empty or oversized release asset')
        with path.open('rb') as stream:
            assets[platform] = dict(name=name, size=size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
    return dict(schema=1, repository=repository, version=version, androidVersionCode=code, assets=assets)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory', type=Path)
    parser.add_argument('--repository')
    args = parser.parse_args()
    version, code = versions()
    if args.directory is None:
        print(f'version={version}\ncode={code}\ntag=v{version}')
        return
    manifest = build_manifest(args.directory, args.repository, version, code)
    (args.directory / 'update.json').write_text(json.dumps(manifest, indent=2) + '\n')
    paths = sorted(p for p in args.directory.iterdir() if p.is_file() and p.name != 'SHA256SUMS.txt')
    checksums = []
    for path in paths:
        with path.open('rb') as stream:
            checksums.append(f'{hashlib.file_digest(stream, "sha256").hexdigest()}  {path.name}\n')
    (args.directory / 'SHA256SUMS.txt').write_text(''.join(checksums))

if __name__ == '__main__':
    main()
