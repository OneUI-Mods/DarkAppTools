#!/usr/bin/env python3
"""Copy appearance settings to the renamed app; both apps must be installed and root authorized.

Verifies both APK signatures, saves a private backup and checks copied preferences.
Does not enable modules, uninstall applications or restart Android/SystemUI.
"""

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import uuid
import xml.etree.ElementTree as ET

OLD_PACKAGE = "dev.ryuya.sesameui"
NEW_PACKAGE = "dev.ryuya.darkapps"
CERTIFICATE = "c2020a1338f2504d893b368edfb77b0ab76cedeb8016df42876439a1cc92acd6"
FILES = {"appearance.xml": "appearance_sesame.xml", "appearance_povo.xml": "appearance_povo.xml"}


def preferences(data):
    root = ET.fromstring(data)
    if root.tag != "map":
        raise ValueError("Expected Android preferences")
    values = {"enabled": True, "mode": "system", "reset": 0}
    seen = set()
    for child in root:
        key = child.get("name")
        if key not in values:
            continue  # Runtime status and timestamps are not user preferences.
        if key in seen:
            raise ValueError("Duplicate preference")
        seen.add(key)
        if key == "enabled":
            if child.tag != "boolean" or child.get("value") not in {"true", "false"}:
                raise ValueError("Invalid enabled preference")
            values[key] = child.get("value") == "true"
        elif key == "mode":
            if child.tag != "string" or child.text not in {"system", "dark", "light"}:
                raise ValueError("Invalid theme preference")
            values[key] = child.text
        else:
            if child.tag != "long" or not re.fullmatch(r"[0-9]{1,19}", child.get("value", "")):
                raise ValueError("Invalid reset preference")
            values[key] = int(child.get("value"))
            if values[key] > 2**63 - 1:
                raise ValueError("Reset preference exceeds Android long")
    return values


def serialize(values):
    root = ET.Element("map")
    ET.SubElement(root, "boolean", name="enabled", value=str(values["enabled"]).lower())
    ET.SubElement(root, "string", name="mode").text = values["mode"]
    ET.SubElement(root, "long", name="reset", value=str(values["reset"]))
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


class Device:
    def __init__(self, adb, serial):
        self.adb = [str(adb), "-s", serial]

    def shell(self, *args):
        return subprocess.run(
            self.adb + ["shell", shlex.join(args)], capture_output=True, check=True
        ).stdout

    def root(self, command):
        return self.shell("su", "-c", command)

    def certificate(self, package, signer, folder):
        paths = self.shell("pm", "path", "--user", "0", package).decode().splitlines()
        path = next(p.removeprefix("package:") for p in paths if p.endswith("/base.apk"))
        apk = folder / (package + ".apk")
        apk.write_bytes(self.root("cat " + shlex.quote(path)))
        result = subprocess.run(
            [str(signer), "verify", "--print-certs", str(apk)],
            capture_output=True, text=True, check=True,
        ).stdout
        digests = re.findall(r"certificate SHA-256 digest: ([0-9a-f]+)", result)
        if digests != [CERTIFICATE]:
            raise ValueError("Unexpected application signing certificate")

    def locales(self, package):
        output = self.shell("cmd", "locale", "get-app-locales", package, "--user", "0").decode()
        match = re.search(r"are \[([A-Za-z0-9,-]*)\]", output)
        if not match:
            raise ValueError("Unrecognized locale response")
        return match.group(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    parser.add_argument("--adb", type=Path, default=sdk / "platform-tools/adb")
    parser.add_argument("--apksigner", type=Path, default=sdk / "build-tools/37.0.0/apksigner")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--backup-dir", type=Path, required=True)
    args = parser.parse_args()
    device = Device(args.adb, args.serial)
    if device.root("id -u").strip() != b"0":
        raise RuntimeError("Authorized root is required")
    with tempfile.TemporaryDirectory(prefix="dark-apps-migration-") as directory:
        folder = Path(directory)
        for package in [OLD_PACKAGE, NEW_PACKAGE]:
            device.certificate(package, args.apksigner, folder)
        locale = device.locales(OLD_PACKAGE)
        for package in [OLD_PACKAGE, NEW_PACKAGE]:
            device.shell("am", "force-stop", "--user", "0", package)
        original = {}
        converted = {}
        for source, destination in FILES.items():
            data = device.root(f"cat /data/user/0/{OLD_PACKAGE}/shared_prefs/{source}")
            original[source] = data
            converted[destination] = serialize(preferences(data))
        destination_dir = f"/data/user/0/{NEW_PACKAGE}/shared_prefs"
        owner = device.root(f"stat -c '%u:%g' /data/user/0/{NEW_PACKAGE}").decode().strip()
        if not re.fullmatch(r"[0-9]+:[0-9]+", owner):
            raise ValueError("Invalid destination owner")
        # Never overwrite a configured destination, even on a repeated invocation.
        for name in converted:
            exists = device.root(f"if test -f {destination_dir}/{name}; then echo present; fi")
            if exists.strip():
                raise RuntimeError("Destination is already configured; no settings changed")
        backup = args.backup_dir.resolve()
        backup.mkdir(parents=True, mode=0o700, exist_ok=False)
        for name, data in original.items():
            (backup / name).write_bytes(data)
            (backup / name).chmod(0o600)
        (backup / "locales.json").write_text(json.dumps({"locales": locale}) + "\n")
        (backup / "locales.json").chmod(0o600)
        for name, data in converted.items():
            local = folder / name
            local.write_bytes(data)
            remote = "/data/local/tmp/dark-apps-migration-" + uuid.uuid4().hex + ".xml"
            subprocess.run(device.adb + ["push", str(local), remote], capture_output=True, check=True)
            try:
                target = destination_dir + "/" + name
                device.root(
                    "set -eu\n"
                    f"mkdir -p {destination_dir}\n"
                    f"chown {owner} {destination_dir}\n"
                    f"chmod 700 {destination_dir}\n"
                    f"cp {remote} {target}\n"
                    f"chown {owner} {target}\n"
                    f"chmod 600 {target}\n"
                    f"restorecon -RF {destination_dir}\n"
                )
                if preferences(device.root("cat " + target)) != preferences(data):
                    raise RuntimeError("Copied preferences did not match")
            finally:
                device.shell("rm", "-f", remote)
        device.shell("cmd", "locale", "set-app-locales", NEW_PACKAGE,
                     "--user", "0", "--locales", locale)
        if device.locales(NEW_PACKAGE) != locale:
            raise RuntimeError("Locale migration did not match")
    print("Appearance preferences and language migrated and verified. Old app retained.")


if __name__ == "__main__":
    main()
