#!/usr/bin/env python3
"""Create an owner-controlled Android key and files for GitHub's four signing secrets.
Never run this in CI: releases must reuse the same backed-up key.
"""
import argparse
import base64
import json
import os
import secrets
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', required=True, type=Path, help='Private directory outside the repository')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = args.output.resolve()
if output == root or root in output.parents:
    parser.error('Keep signing material outside the repository')
output.mkdir(parents=True, exist_ok=False, mode=0o700)
password = secrets.token_urlsafe(36)
env = dict(os.environ, KIZUNA_KEY_PASSWORD=password)
key = output / 'kizuna-release.jks'
subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'JKS', '-alias', 'kizuna', '-keyalg', 'RSA', '-keysize', '4096', '-validity', '10000', '-dname', 'CN=Kizuna Release', '-storepass:env', 'KIZUNA_KEY_PASSWORD', '-keypass:env', 'KIZUNA_KEY_PASSWORD'], env=env, check=True)
values = {'ANDROID_KEYSTORE_BASE64': base64.b64encode(key.read_bytes()).decode(), 'ANDROID_STORE_PASSWORD': password, 'ANDROID_KEY_ALIAS': 'kizuna', 'ANDROID_KEY_PASSWORD': password}
for name, value in values.items():
    p = output / name
    p.write_text(value)
    p.chmod(0o600)
key.chmod(0o600)
print(f'Created {output}. Back it up privately. Add each named file as the matching GitHub Actions repository secret; never commit these files.')
