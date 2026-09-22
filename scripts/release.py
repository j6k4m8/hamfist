"""Validate version tags and package signed, verified phone/watch release APKs."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def version(tag):
    match = re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag)
    if not match:
        raise ValueError("Use a stable version tag such as v0.1.0 (no suffix or leading zeros)")
    major, minor, patch = map(int, match.groups())
    if major > 2099 or minor > 999 or patch > 999:
        raise ValueError("Version components exceed Android versionCode limits")
    code = major * 1_000_000 + minor * 1_000 + patch
    if code == 0:
        raise ValueError("v0.0.0 is not an installable Android version")
    return tag[1:], code


def checked_output(*command):
    return subprocess.check_output([str(part) for part in command], text=True)


def verify_apk(apk, build_tools, name, code):
    badging = checked_output(build_tools / "aapt", "dump", "badging", apk)
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("dev.hamfist", str(code), name):
        raise ValueError(f"Unexpected package/version in {apk.name}")
    if "application-debuggable" in badging:
        raise ValueError(f"Refusing to publish debuggable APK: {apk.name}")
    result = checked_output(build_tools / "apksigner", "verify", "--verbose", "--print-certs", apk)
    certificates = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", result, re.M)
    if len(certificates) != 1:
        raise ValueError(f"Expected exactly one signing certificate in {apk.name}")
    return certificates[0]


def package(tag, build_tools, keystore):
    name, code = version(tag)
    if not os.environ.get("HAMFIST_KEYSTORE_PASSWORD"):
        raise ValueError("HAMFIST_KEYSTORE_PASSWORD must be set")
    output = ROOT / "dist"
    output.mkdir(exist_ok=True)
    apks, certificates = [], []
    with tempfile.TemporaryDirectory(prefix="hamfist-release-") as temp:
        for target in ("phone", "wear"):
            source = ROOT / target / "build/outputs/apk/release" / f"{target}-release-unsigned.apk"
            aligned = Path(temp) / f"{target}-aligned.apk"
            apk = output / f"hamfist-{target}-{tag}.apk"
            subprocess.run([str(build_tools / "zipalign"), "-P", "16", "-f", "4", str(source), str(aligned)], check=True)
            subprocess.run([
                str(build_tools / "apksigner"), "sign", "--ks", str(keystore),
                "--ks-key-alias", "hamfist", "--ks-pass", "env:HAMFIST_KEYSTORE_PASSWORD",
                "--key-pass", "env:HAMFIST_KEYSTORE_PASSWORD", "--v4-signing-enabled", "false",
                "--out", str(apk), str(aligned),
            ], check=True)
            certificates.append(verify_apk(apk, build_tools, name, code))
            subprocess.run([str(build_tools / "zipalign"), "-c", "-P", "16", "4", str(apk)], check=True)
            apks.append(apk)
    if len(set(certificates)) != 1:
        raise ValueError("Phone and watch must share the same signing certificate")
    checksums = "".join(f"{hashlib.sha256(apk.read_bytes()).hexdigest()}  {apk.name}\n" for apk in apks)
    (output / "SHA256SUMS").write_text(checksums)
    print(f"Verified phone + wear: versionName={name}, versionCode={code}, non-debuggable")
    print(f"Shared signing certificate SHA-256: {certificates[0]}")
    print(checksums, end="")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    version_parser = sub.add_parser("version")
    version_parser.add_argument("tag")
    version_parser.add_argument("--github-output", type=Path)
    package_parser = sub.add_parser("package")
    package_parser.add_argument("tag")
    package_parser.add_argument("--build-tools", type=Path, required=True)
    package_parser.add_argument("--keystore", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "version":
        name, code = version(args.tag)
        output = f"name={name}\ncode={code}\n"
        if args.github_output:
            with args.github_output.open("a") as stream:
                stream.write(output)
        print(output, end="")
    else:
        package(args.tag, args.build_tools, args.keystore)


if __name__ == "__main__":
    main()
