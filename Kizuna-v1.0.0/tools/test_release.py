import json
import tempfile
import unittest
from pathlib import Path
from release import build_manifest, versions, ROOT

class ReleaseTests(unittest.TestCase):
    def test_generated_manifest_is_shared_with_both_clients(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'Kizuna-v1.1.0-android.apk').write_bytes(b'APK')
            (directory / 'Kizuna-v1.1.0-Setup.exe').write_bytes(b'EXE')
            actual = build_manifest(directory, 'Chowdhury-Siam/Kizuna', '1.1.0', 8)
            expected = json.loads((ROOT / 'tests/fixtures/update.json').read_text())
            self.assertEqual(expected, actual)
    def test_versions_are_stable_and_match(self):
        version, code = versions()
        self.assertRegex(version, r'^\d+\.\d+\.\d+$')
        self.assertGreater(code, 0)
    def test_unsigned_or_missing_asset_cannot_enter_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            (directory / 'Kizuna-v1.0.0-android-unsigned.apk').write_bytes(b'APK')
            with self.assertRaises(FileNotFoundError): build_manifest(directory, 'Owner/Repo', '1.0.0', 7)
            with self.assertRaises(ValueError): build_manifest(directory, 'Owner/../Repo', '1.0.0', 7)
            with self.assertRaises(ValueError): build_manifest(directory, 'Owner/Repo', '1.0.0-beta', 7)

if __name__ == '__main__': unittest.main()
