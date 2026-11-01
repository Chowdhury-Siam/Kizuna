#!/usr/bin/env python3
"""Auralis-style one-time key setup: save one identity atomically in GitHub Secrets."""
import json
import os
import re
import subprocess
from android_signing import IDENTITY, generate_identity, mask

LEGACY_BASE = {"ANDROID_KEYSTORE_BASE64", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD"}
PASSWORDS = {"ANDROID_STORE_PASSWORD", "ANDROID_KEYSTORE_PASSWORD"}
LEGACY = LEGACY_BASE | PASSWORDS


def github(environment, *arguments, value=None):
    result = subprocess.run(["gh", *arguments], input=value, text=True, capture_output=True,
                            env=dict(os.environ, **environment))
    if result.returncode:
        raise ValueError("GitHub secret access failed. ANDROID_SIGNING_SETUP_TOKEN needs this repository's Secrets read/write permission.")
    return result.stdout


def setup(environment):
    if environment.get("GITHUB_EVENT_NAME") != "workflow_dispatch" or environment.get("GITHUB_REF") != "refs/heads/main":
        raise ValueError("Run signing setup manually from main.")
    repo = environment.get("GITHUB_REPOSITORY", "")
    if not re.fullmatch(r"[A-Za-z0-9-]+/[A-Za-z0-9_.-]+", repo) or repo.split("/")[-1] in (".", ".."):
        raise ValueError("Invalid repository.")
    if not environment.get("GH_TOKEN"):
        raise ValueError("Add ANDROID_SIGNING_SETUP_TOKEN once with repository Secrets read/write permission, then run Set up Android signing. Normal builds do not need this token.")

    def existing():
        return {item["name"] for item in json.loads(github(environment, "secret", "list", "--repo", repo, "--json", "name"))}

    names = existing()
    complete_legacy = LEGACY_BASE <= names and bool(names & PASSWORDS)
    if names & LEGACY and not complete_legacy:
        raise ValueError("Existing signing secrets are incomplete. Restore the missing secrets; setup will not replace that key.")
    if IDENTITY in names or complete_legacy:
        print("Existing signing identity preserved. No key was generated or replaced.")
        return False
    # The setup workflow serializes runs. No key enters caches, artifacts or Git.
    bundle = generate_identity()
    for field in ("KEYSTORE", "STORE_PASSWORD", "KEY_PASSWORD"):
        mask(bundle[field], environment)
    if existing() & (LEGACY | {IDENTITY}):
        raise ValueError("Signing secrets appeared during setup. No key was overwritten; rerun setup.")
    github(environment, "secret", "set", IDENTITY, "--repo", repo, value=json.dumps(bundle))
    print("Signing identity saved securely. Future builds reuse it automatically.")
    return True


if __name__ == "__main__":
    try:
        setup(dict(os.environ))
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        # Keep native/GitHub diagnostics and credentials out of action logs.
        raise SystemExit(str(error) if isinstance(error, ValueError) else "Signing key setup failed.") from None
