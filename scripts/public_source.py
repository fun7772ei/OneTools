"""Export and inspect the explicitly reviewed public source set; never print findings' contents."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath

MANIFEST = "scripts/public-source-files.json"
WRAPPER = "gradle/wrapper/gradle-wrapper.jar"
WRAPPER_SHA256 = "b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13"
BLOCKED_PARTS = {".git", ".gradle", ".idea", ".kotlin", "build", "artifacts", "__pycache__"}
BLOCKED_SUFFIXES = {".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".apk", ".aab", ".log", ".zip", ".png", ".jpg"}
CHECKS = {
    "private-key": re.compile(r"-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----"),
    "github-token": re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})\b"),
    "cloud-access-key": re.compile(r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b"),
    "api-secret": re.compile(r"\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{24,}|AIza[A-Za-z0-9_-]{30,})\b"),
    "user-directory": re.compile(r"(?:[A-Za-z]:[\\/]+Users[\\/]+|/(?:Users|home)/)[A-Za-z0-9_.-]+", re.I),
    "device-serial": re.compile(r"\bR[A-Z0-9]{10}\b"),
    "literal-credential": re.compile(r'''(?i)(?:password|api[_-]?key|access[_-]?token|client[_-]?secret)\s*[:=]\s*["'][A-Za-z0-9_+/=-]{16,}["']'''),
    "credential-url": re.compile(r"https?://[^\s/:]+:[^\s/@]+@", re.I),
}


def safe_path(name):
    path = PurePosixPath(name)
    if (not name or path.is_absolute() or "\\" in name or ":" in name or
            any(p in {"", ".", ".."} for p in path.parts) or str(path) != name or
            any(p.lower() in BLOCKED_PARTS for p in path.parts) or
            path.suffix.lower() in BLOCKED_SUFFIXES or
            path.name.lower() in {"local.properties", "keystore.properties", "signing.local.json"} or
            path.name.lower().startswith(".env")):
        raise ValueError(f"Prohibited path: {name}")
    return path


def read_regular(root, name):
    relative = safe_path(name)
    file = root.joinpath(*relative.parts)
    for part in (file, *file.parents):
        if part == root:
            break
        if part.is_symlink() or part.is_junction():
            raise ValueError(f"Links are prohibited: {name}")
    if not file.is_file() or not file.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"Missing or outside-root file: {name}")
    return file.read_bytes()


def inspect(name, data):
    safe_path(name)
    if name == WRAPPER:
        if hashlib.sha256(data).hexdigest() != WRAPPER_SHA256:
            raise ValueError("Gradle wrapper differs from the official reviewed checksum")
        return
    if len(data) > 2_000_000 or b"\0" in data:
        raise ValueError(f"Unexpected binary or oversized text: {name}")
    text = data.decode("utf-8-sig")
    for kind, pattern in CHECKS.items():
        match = pattern.search(text)
        if match:
            line = text.count("\n", 0, match.start()) + 1
            raise ValueError(f"{kind} found in {name}:{line} (content redacted)")


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def audit(root, tracked=False):
    if tracked:
        entries = git(root, "ls-files", "--stage", "-z").split(b"\0")
        files = {}
        for entry in filter(None, entries):
            attributes, name = entry.split(b"\t", 1)
            mode, oid, stage = attributes.split()
            if mode not in (b"100644", b"100755") or stage != b"0":
                raise ValueError("Links, submodules and merge conflicts cannot be published")
            files[name.decode()] = git(root, "cat-file", "blob", oid.decode())
        manifest = json.loads(files[MANIFEST].decode("utf-8-sig"))
    else:
        manifest = json.loads(read_regular(root, MANIFEST).decode("utf-8-sig"))
        files = {name: read_regular(root, name) for name in manifest["files"]}
    expected = manifest["files"]
    if len(expected) != len(set(expected)) or set(files) != set(expected) or MANIFEST not in expected:
        raise ValueError("The exact tracked file set must match the reviewed manifest")
    for name, data in files.items():
        inspect(name, data)
    # Compare the same source bytes on Windows and Linux, regardless of checkout EOLs.
    return {name: data if name == WRAPPER else data.decode("utf-8-sig").replace("\r\n", "\n").encode("utf-8")
            for name, data in files.items()}


def export(root, destination):
    files = audit(root)
    if destination.exists() and (destination.is_symlink() or destination.is_junction()):
        raise ValueError("Export destination cannot be a link")
    destination.mkdir(parents=True, exist_ok=True)
    old_manifest = destination / MANIFEST
    if old_manifest.exists():
        old = json.loads(read_regular(destination, MANIFEST).decode("utf-8-sig"))["files"]
        for name in set(old) - set(files):
            read_regular(destination, name)
            destination.joinpath(*safe_path(name).parts).unlink()
    for name, data in files.items():
        target = destination.joinpath(*safe_path(name).parts)
        if target.exists():
            read_regular(destination, name)
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.parent.resolve().is_relative_to(destination.resolve()):
            raise ValueError("Export path escaped the destination")
        target.write_bytes(data)
    audit(destination)
    return files


def self_test():
    rejected = [".env", "app/src/main/release.jks", "local.properties", "../outside.kt", "app/build/output.txt"]
    for name in rejected:
        try:
            safe_path(name)
        except ValueError:
            continue
        raise AssertionError("Unsafe path accepted")
    samples = ["ghp_" + "a" * 36, "-----BEGIN " + "PRIVATE KEY-----", "C:" + "/Users/" + "sample/project", "R" + "ABC1234567"]
    for data in samples:
        try:
            inspect("sample.txt", data.encode())
        except ValueError:
            continue
        raise AssertionError("Sensitive content accepted")
    inspect("sample.kt", b'val password = System.getenv("RELEASE_PASSWORD")')
    print("Public-source guard negative checks passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["audit", "export", "self-test"])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--destination", type=Path)
    parser.add_argument("--tracked", action="store_true")
    args = parser.parse_args()
    try:
        if args.command == "self-test":
            self_test()
        else:
            files = export(args.root.resolve(), args.destination.resolve()) if args.command == "export" and args.destination else audit(args.root.resolve(), args.tracked)
            digest = hashlib.sha256(b"".join(name.encode() + b"\0" + hashlib.sha256(data).digest() for name, data in sorted(files.items()))).hexdigest()
            print(json.dumps({"files": len(files), "sourceSha256": digest}))
    except (ValueError, OSError, KeyError, UnicodeError, subprocess.CalledProcessError) as error:
        print(f"Public-source audit failed: {error}", file=sys.stderr)
        sys.exit(1)
