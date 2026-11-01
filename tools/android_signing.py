#!/usr/bin/env python3
"""Prepare retained release signing, or an isolated temporary Preview signature."""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import zipfile

FIELDS = ("KEYSTORE", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
IDENTITY = "ANDROID_SIGNING_IDENTITY"


def mask(value, environment):
    if environment.get("GITHUB_ACTIONS") == "true":
        print("::add-mask::" + value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A"))


def identity_values(environment):
    values = [environment.get(name, "") for name in FIELDS]
    if any(values):
        if not all(isinstance(v, str) and v for v in values):
            raise ValueError("Existing signing secrets are incomplete. Restore the missing values; no key will be replaced.")
        bundle = dict(zip(FIELDS, values))
    elif environment.get("SIGNING_IDENTITY"):
        try:
            bundle = json.loads(environment["SIGNING_IDENTITY"])
            if not isinstance(bundle, dict) or set(bundle) != set(FIELDS):
                raise ValueError()
        except (ValueError, TypeError):
            raise ValueError("Invalid ANDROID_SIGNING_IDENTITY. Restore the saved identity; do not generate a replacement key.") from None
    else:
        return None
    if not all(isinstance(bundle[name], str) and bundle[name] and "\0" not in bundle[name] for name in FIELDS):
        raise ValueError("Invalid signing identity fields.")
    bundle = dict(bundle)
    # The earlier shell decoder accepted base64 line wrapping in legacy secrets.
    bundle["KEYSTORE"] = "".join(bundle["KEYSTORE"].split())
    try:
        blob = base64.b64decode(bundle["KEYSTORE"], validate=True)
        if not 32 <= len(blob) <= 10 * 1024 * 1024:
            raise ValueError()
    except (ValueError, TypeError):
        raise ValueError("Invalid signing keystore encoding or size.") from None
    return bundle


def generate_identity(alias="kizuna", common_name="Kizuna Android Release"):
    password = secrets.token_urlsafe(48)
    with tempfile.TemporaryDirectory(prefix="kizuna-signing-") as temporary:
        key = Path(temporary) / "release.jks"
        subprocess.run([
            "keytool", "-genkeypair", "-keystore", str(key), "-storetype", "PKCS12",
            "-alias", alias, "-keyalg", "RSA", "-keysize", "4096", "-validity", "10000",
            "-dname", f"CN={common_name}", "-storepass:env", "SIGNING_PASSWORD",
            "-keypass:env", "SIGNING_PASSWORD", "-noprompt",
        ], env=dict(os.environ, SIGNING_PASSWORD=password), check=True,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        key.chmod(0o600)
        return dict(KEYSTORE=base64.b64encode(key.read_bytes()).decode(), STORE_PASSWORD=password,
                    KEY_ALIAS=alias, KEY_PASSWORD=password)


def property_value(value):
    """Properties.load(InputStream) uses Latin-1; escape non-ASCII UTF-16 units."""
    escapes = {"\\": "\\\\", "\n": "\\n", "\r": "\\r", "\t": "\\t", " ": "\\ ", "=": "\\=", ":": "\\:", "#": "\\#", "!": "\\!"}
    result = []
    for character in value:
        if character in escapes:
            result.append(escapes[character])
        elif not 32 <= ord(character) <= 126:
            units = character.encode("utf-16-be")
            result.extend(f"\\u{int.from_bytes(units[i:i+2], 'big'):04x}" for i in range(0, len(units), 2))
        else:
            result.append(character)
    return "".join(result)


def configure(directory, environment):
    bundle = identity_values(environment)
    retained = bundle is not None
    properties = directory / "local.properties"
    key_directory = Path(environment["RUNNER_TEMP"]) / "kizuna-signing"
    if properties.exists() or key_directory.exists():
        raise ValueError("Signing paths already exist. Use a clean runner; existing signing files were left untouched.")
    if bundle is None:
        bundle = generate_identity("development", "Kizuna Preview")
    for field in ("KEYSTORE", "STORE_PASSWORD", "KEY_PASSWORD"):
        mask(bundle[field], environment)
    key_directory.mkdir(mode=0o700)
    key = key_directory / "release.jks"
    wrote_properties = False
    try:
        with key.open("xb") as stream:
            key.chmod(0o600)
            stream.write(base64.b64decode(bundle["KEYSTORE"], validate=True))
        # jarsigner really unlocks the private key. keytool silently ignores a
        # different key password for PKCS12 and cannot validate this reliably.
        probe = key_directory / "validation.jar"
        with zipfile.ZipFile(probe, "w") as archive:
            archive.writestr("probe.txt", "Kizuna signing validation")
        subprocess.run([
            "java", "-m", "jdk.jartool/sun.security.tools.jarsigner.Main", "-keystore", str(key),
            "-storepass:env", "SIGNING_STORE_PASSWORD", "-keypass:env", "SIGNING_KEY_PASSWORD",
            str(probe), bundle["KEY_ALIAS"],
        ], env=dict(os.environ, SIGNING_STORE_PASSWORD=bundle["STORE_PASSWORD"], SIGNING_KEY_PASSWORD=bundle["KEY_PASSWORD"]),
            check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        probe.unlink()
        with properties.open("x", encoding="ascii") as stream:
            wrote_properties = True
            properties.chmod(0o600)
            for name, value in (("storeFile", str(key)), ("storePassword", bundle["STORE_PASSWORD"]),
                                ("keyAlias", bundle["KEY_ALIAS"]), ("keyPassword", bundle["KEY_PASSWORD"])):
                stream.write(f"{name}={property_value(value)}\n")
        return retained
    except Exception:
        if wrote_properties:
            properties.unlink(missing_ok=True)
        shutil.rmtree(key_directory)
        raise ValueError("Could not configure APK signing. Check the saved keystore, alias and both passwords; no saved identity was changed.") from None


def identity_from_kit(kit, output):
    root = Path(__file__).resolve().parents[1]
    destination = output.resolve()
    if destination == root or root in destination.parents:
        raise ValueError("Keep the private signing identity outside the repository.")
    files = dict(KEYSTORE="ANDROID_KEYSTORE_BASE64", STORE_PASSWORD="ANDROID_STORE_PASSWORD",
                 KEY_ALIAS="ANDROID_KEY_ALIAS", KEY_PASSWORD="ANDROID_KEY_PASSWORD")
    with zipfile.ZipFile(kit) as archive:
        bundle = {}
        for field, name in files.items():
            if not 0 < archive.getinfo(name).file_size <= 65536:
                raise ValueError("Invalid signing kit field size.")
            bundle[field] = archive.read(name).decode("utf-8")
    identity_values(dict(SIGNING_IDENTITY=json.dumps(bundle)))
    with os.fdopen(os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as stream:
        json.dump(bundle, stream)
    print("Private identity file written. Use its contents for ANDROID_SIGNING_IDENTITY and keep it private.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--import-kit", type=Path)
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()
    if bool(arguments.import_kit) != bool(arguments.output):
        parser.error("Use --import-kit and --output together")
    try:
        if arguments.import_kit:
            identity_from_kit(arguments.import_kit, arguments.output)
            return
        signed = configure(Path(__file__).resolve().parents[1] / "androidapp3", os.environ)
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            stream.write(f"signed={str(signed).lower()}\n")
        print("Retained release signing configured." if signed else "Temporary Preview signing configured. Stable publishing waits for Set up Android signing.")
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile, KeyError) as error:
        raise SystemExit(str(error) if isinstance(error, ValueError) else "APK signing preparation failed.") from None


if __name__ == "__main__":
    main()
