import base64
import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import android_signing as signing
import setup_android_signing as setup


@unittest.skipUnless(shutil.which('keytool'), 'JDK required for native keystore checks')
class SigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.bundle = signing.generate_identity()

    def environment(self, runtime):
        return dict(RUNNER_TEMP=str(runtime), SIGNING_IDENTITY=json.dumps(self.bundle))

    def test_retained_key_restores_unchanged_and_validates_with_native_signing_tools(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.assertTrue(signing.configure(root, self.environment(root)))
            key = root / 'kizuna-signing/release.jks'
            self.assertEqual(base64.b64decode(self.bundle['KEYSTORE']), key.read_bytes())
            self.assertEqual(0o600, key.stat().st_mode & 0o777)
            self.assertEqual(0o600, (root / 'local.properties').stat().st_mode & 0o777)
            self.assertFalse((key.parent / 'validation.jar').exists())

    def test_legacy_secret_identity_keeps_priority_for_existing_installs(self):
        environment = dict(self.bundle, SIGNING_IDENTITY='invalid-unused-json')
        self.assertEqual(self.bundle, signing.identity_values(environment))

    def test_wrapped_legacy_base64_keeps_the_same_key(self):
        encoded = self.bundle['KEYSTORE']
        wrapped = '\n'.join(encoded[i:i+76] for i in range(0, len(encoded), 76)) + '\n'
        restored = signing.identity_values(dict(self.bundle, KEYSTORE=wrapped))
        self.assertEqual(base64.b64decode(encoded), base64.b64decode(restored['KEYSTORE']))

    def test_no_saved_key_prepares_only_temporary_preview_signing(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(signing, 'generate_identity', return_value=self.bundle) as generate:
            root = Path(tmp)
            self.assertFalse(signing.configure(root, dict(RUNNER_TEMP=tmp)))
            generate.assert_called_once_with('development', 'Kizuna Preview')
            self.assertTrue((root / 'local.properties').exists())

    def test_partial_invalid_identities_never_fall_back_to_a_new_key(self):
        invalid = [dict(KEYSTORE=self.bundle['KEYSTORE']), dict(SIGNING_IDENTITY='not-json'),
                   dict(SIGNING_IDENTITY='[]'), dict(SIGNING_IDENTITY='{}'),
                   dict(SIGNING_IDENTITY=json.dumps(dict(self.bundle, KEYSTORE='invalid!!'))),
                   dict(SIGNING_IDENTITY=json.dumps(dict(self.bundle, KEYSTORE=base64.b64encode(b'short').decode()))),
                   dict(SIGNING_IDENTITY=json.dumps(dict(self.bundle, KEY_PASSWORD='')))]
        for values in invalid:
            with self.subTest(fields=list(values)), tempfile.TemporaryDirectory() as tmp, patch.object(signing, 'generate_identity') as generate:
                with self.assertRaises(ValueError):
                    signing.configure(Path(tmp), dict(values, RUNNER_TEMP=tmp))
                generate.assert_not_called()
                self.assertFalse((Path(tmp) / 'local.properties').exists())

    def test_wrong_store_password_key_password_and_alias_are_sanitized_and_cleaned(self):
        for field in ('STORE_PASSWORD', 'KEY_PASSWORD', 'KEY_ALIAS'):
            bundle = dict(self.bundle, **{field: 'incorrect-value'})
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stdout(io.StringIO()) as logs:
                with self.assertRaises(ValueError) as error:
                    signing.configure(Path(tmp), dict(RUNNER_TEMP=tmp, SIGNING_IDENTITY=json.dumps(bundle)))
                self.assertNotIn(bundle['KEYSTORE'], str(error.exception) + logs.getvalue())
                self.assertNotIn(self.bundle['STORE_PASSWORD'], str(error.exception) + logs.getvalue())
                self.assertFalse((Path(tmp) / 'local.properties').exists())
                self.assertFalse((Path(tmp) / 'kizuna-signing').exists())

    def test_existing_private_files_are_preserved(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(signing, 'generate_identity') as generate:
            root = Path(tmp)
            props = root / 'local.properties'
            props.write_text('sdk.dir=keep-me\n')
            with self.assertRaises(ValueError):
                signing.configure(root, dict(RUNNER_TEMP=tmp))
            self.assertEqual('sdk.dir=keep-me\n', props.read_text())
            generate.assert_not_called()

    @unittest.skipUnless(shutil.which('java'), 'Java required for Properties.load integration')
    def test_properties_escaping_is_read_correctly_by_java(self):
        value = ' spaces:=#!\\line\nবাংলা 🎮\t'
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            props = root / 'test.properties'
            props.write_text('value=' + signing.property_value(value) + '\n', encoding='ascii')
            java = root / 'ReadProperties.java'
            java.write_text('''import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;
class ReadProperties { public static void main(String[] args) throws Exception {
  var p = new Properties();
  try (var input = Files.newInputStream(Path.of(args[0]))) { p.load(input); }
  if (!p.getProperty("value").equals(System.getenv("EXPECTED_VALUE"))) throw new Exception("Property mismatch");
}}''')
            subprocess.run(['java', str(java), str(props)], env=dict(os.environ, EXPECTED_VALUE=value),
                           check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)

    def test_private_kit_import_preserves_the_existing_apk_identity(self):
        names = dict(KEYSTORE='ANDROID_KEYSTORE_BASE64', STORE_PASSWORD='ANDROID_STORE_PASSWORD',
                     KEY_ALIAS='ANDROID_KEY_ALIAS', KEY_PASSWORD='ANDROID_KEY_PASSWORD')
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            kit = root / 'kit.zip'
            with zipfile.ZipFile(kit, 'w') as z:
                for field, name in names.items(): z.writestr(name, self.bundle[field])
            output = root / 'ANDROID_SIGNING_IDENTITY'
            with contextlib.redirect_stdout(io.StringIO()): signing.identity_from_kit(kit, output)
            self.assertEqual(self.bundle, json.loads(output.read_text()))
            self.assertEqual(0o600, output.stat().st_mode & 0o777)
            with self.assertRaises(FileExistsError): signing.identity_from_kit(kit, output)


class SetupTests(unittest.TestCase):
    def environment(self):
        return dict(GITHUB_EVENT_NAME='workflow_dispatch', GITHUB_REF='refs/heads/main',
                    GITHUB_REPOSITORY='Owner/Kizuna', GH_TOKEN='fixture-token')

    def test_non_manual_non_main_invalid_repo_and_missing_token_are_rejected(self):
        for changed in (dict(GITHUB_EVENT_NAME='push'), dict(GITHUB_REF='refs/heads/feature/test'),
                        dict(GITHUB_REPOSITORY='owner/../repo'), dict(GH_TOKEN='')):
            with self.subTest(changed=changed), patch.object(setup, 'github') as github, patch.object(setup, 'generate_identity') as generate:
                with self.assertRaises(ValueError): setup.setup(dict(self.environment(), **changed))
                github.assert_not_called(); generate.assert_not_called()

    def test_existing_identity_and_both_legacy_password_names_are_preserved(self):
        for names in ({signing.IDENTITY}, setup.LEGACY_BASE | {'ANDROID_STORE_PASSWORD'},
                      setup.LEGACY_BASE | {'ANDROID_KEYSTORE_PASSWORD'}):
            with self.subTest(names=names), patch.object(setup, 'github', return_value=json.dumps([dict(name=n) for n in names])) as github, patch.object(setup, 'generate_identity') as generate, contextlib.redirect_stdout(io.StringIO()):
                self.assertFalse(setup.setup(self.environment()))
                generate.assert_not_called(); self.assertEqual(1, github.call_count)

    def test_partial_existing_credentials_are_never_replaced(self):
        for names in ({'ANDROID_KEYSTORE_BASE64'}, {'ANDROID_STORE_PASSWORD'}, {signing.IDENTITY, 'ANDROID_KEY_ALIAS'}):
            with patch.object(setup, 'github', return_value=json.dumps([dict(name=n) for n in names])), patch.object(setup, 'generate_identity') as generate:
                with self.assertRaises(ValueError): setup.setup(self.environment())
                generate.assert_not_called()

    def test_fresh_setup_saves_one_bundle_and_next_run_reuses_it(self):
        bundle = dict(KEYSTORE='fixture-base64', STORE_PASSWORD='fixture-password', KEY_ALIAS='kizuna', KEY_PASSWORD='fixture-password')
        calls = []; saved = {}
        def github(environment, *arguments, value=None):
            calls.append(arguments)
            if arguments[:2] == ('secret', 'list'):
                return json.dumps([dict(name=n) for n in saved])
            self.assertEqual(('secret', 'set', signing.IDENTITY), arguments[:3])
            saved[arguments[2]] = value
            return ''
        with patch.object(setup, 'github', side_effect=github), patch.object(setup, 'generate_identity', return_value=bundle) as generate, contextlib.redirect_stdout(io.StringIO()) as logs:
            self.assertTrue(setup.setup(self.environment()))
            self.assertEqual(bundle, json.loads(saved[signing.IDENTITY]))
            self.assertFalse(setup.setup(self.environment()))
            generate.assert_called_once_with()
            self.assertEqual(1, sum(c[:2] == ('secret', 'set') for c in calls))
            self.assertNotIn('fixture-password', logs.getvalue())
            self.assertNotIn('fixture-base64', logs.getvalue())

    def test_an_identity_appearing_during_setup_aborts_before_write(self):
        with patch.object(setup, 'github', side_effect=['[]', json.dumps([dict(name=signing.IDENTITY)])]) as github, patch.object(setup, 'generate_identity', return_value={k:'fixture' for k in signing.FIELDS}):
            with self.assertRaises(ValueError): setup.setup(self.environment())
            self.assertEqual(2, github.call_count)

    def test_github_errors_do_not_expose_token_or_native_output(self):
        result = subprocess.CompletedProcess(['gh'], 1, 'fixture-token', 'private-response')
        with patch.object(setup.subprocess, 'run', return_value=result):
            with self.assertRaises(ValueError) as error: setup.github(self.environment(), 'secret', 'list')
            self.assertNotIn('fixture-token', str(error.exception))
            self.assertNotIn('private-response', str(error.exception))

    def test_command_masking_escapes_linebreaks(self):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            signing.mask('value%\n::warning::not-a-command', dict(GITHUB_ACTIONS='true'))
        self.assertEqual('::add-mask::value%25%0A::warning::not-a-command\n', output.getvalue())


if __name__ == '__main__': unittest.main()
