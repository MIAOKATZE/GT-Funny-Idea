#!/usr/bin/env python3
"""Verify a clean tagged GTFI normal JAR against the actual tested normal JAR.

Only the complete, independently identified build version may change. ZIP entry
timestamps/compression are immaterial; every file entry's content is inspected.
No Git state, source file, build output or candidate JAR is modified.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
from datetime import datetime, timezone
import zipfile


class VerificationError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise VerificationError(message)


class Reader:
    def __init__(self, data):
        self.data = data
        self.offset = 0

    def take(self, count):
        require(0 <= count <= len(self.data) - self.offset, "Truncated class file")
        start = self.offset
        self.offset += count
        return self.data[start:self.offset]

    def u1(self):
        return self.take(1)[0]

    def u2(self):
        return struct.unpack(">H", self.take(2))[0]

    def u4(self):
        return struct.unpack(">I", self.take(4))[0]


def class_pool(data):
    """Read the real JVM constant pool, preserving every original byte span."""
    reader = Reader(data)
    require(reader.take(4) == b"\xca\xfe\xba\xbe", "Invalid class magic")
    reader.u2()
    major = reader.u2()
    count = reader.u2()
    require(count > 0, "Invalid constant-pool count")
    pool = [None] * count
    index = 1
    lengths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
               11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        start = reader.offset
        kind = reader.u1()
        if kind == 1:
            value = reader.take(reader.u2())
        else:
            require(kind in lengths, "Unknown JVM constant-pool tag: %s" % kind)
            value = reader.take(lengths[kind])
        pool[index] = (kind, value, start, reader.offset)
        if kind in (5, 6):
            require(index + 1 < count, "Invalid two-slot constant")
            index += 1
        index += 1
    return major, pool, reader


def pool_item(pool, index, kind):
    require(0 < index < len(pool) and pool[index] is not None,
            "Invalid constant-pool reference")
    require(pool[index][0] == kind, "Unexpected constant-pool reference type")
    return pool[index][1]


def tag_constants(data):
    """Resolve ConstantValue attributes for the actual Tags string fields."""
    _, pool, reader = class_pool(data)
    reader.take(6)  # access_flags, this_class, super_class
    reader.take(2 * reader.u2())  # interfaces
    values = {}
    for _ in range(reader.u2()):
        access = reader.u2()
        name = pool_item(pool, reader.u2(), 1)
        descriptor = pool_item(pool, reader.u2(), 1)
        for _ in range(reader.u2()):
            attribute = pool_item(pool, reader.u2(), 1)
            payload = reader.take(reader.u4())
            if attribute == b"ConstantValue" and name in (b"VERSION", b"RELEASE_VERSION"):
                require(name not in values, "Duplicate Tags constant")
                require(access & 0x0018 == 0x0018 and descriptor == b"Ljava/lang/String;",
                        "Tags version field must be a static final String")
                require(len(payload) == 2, "Invalid ConstantValue attribute")
                string_index = struct.unpack(">H", payload)[0]
                utf8_index = struct.unpack(">H", pool_item(pool, string_index, 8))[0]
                values[name.decode("ascii")] = pool_item(pool, utf8_index, 1).decode("ascii")
    require(set(values) == {"VERSION", "RELEASE_VERSION"}, "Tags version fields missing")
    return values


def normalize_class_version(data, candidate_version, release_version):
    """Change only whole CONSTANT_Utf8 entries equal to the verified version."""
    _, pool, reader = class_pool(data)
    old = candidate_version.encode("ascii")
    new = release_version.encode("ascii")
    require(len(new) <= 65535, "Version is too long for a JVM UTF8 constant")
    output = [data[:10]]
    indexes = []
    for index, entry in enumerate(pool):
        if entry is None:
            continue
        kind, value, start, end = entry
        if kind == 1 and value == old and old != new:
            output.append(b"\x01" + struct.pack(">H", len(new)) + new)
            indexes.append(index)
        else:
            output.append(data[start:end])
    output.append(data[reader.offset:])
    return b"".join(output), indexes


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "Duplicate JSON key: %s" % key)
        result[key] = value
    return result


def read_metadata(data):
    document = json.loads(data.decode("utf-8"), object_pairs_hook=unique_object)
    entries = document.get("modList", []) if isinstance(document, dict) else document
    require(isinstance(entries, list) and len(entries) == 1,
            "mcmod.info must describe exactly one mod")
    metadata = entries[0]
    require(isinstance(metadata, dict) and metadata.get("modid") == "gtfi",
            "mcmod.info must describe gtfi")
    return document, metadata


def normalize_metadata_version(data, candidate_version, release_version):
    _, metadata = read_metadata(data)
    require(metadata.get("version") == candidate_version, "Candidate metadata version changed")
    # Token-level replacement preserves all whitespace, ordering and unrelated fields.
    pattern = re.compile(rb'("version"\s*:\s*)("(?:[^"\\]|\\.)*")')
    matches = list(pattern.finditer(data))
    require(len(matches) == 1, "mcmod.info must have exactly one version field")
    match = matches[0]
    require(json.loads(match.group(2)) == candidate_version, "Unexpected metadata version token")
    return data[:match.start(2)] + json.dumps(release_version).encode("ascii") + data[match.end(2):]


def read_jar(path):
    require(path.is_file(), "JAR does not exist: %s" % path)
    blob = path.read_bytes()
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, "ZIP CRC failure: %s" % path)
        entries = {}
        all_names = set()
        for entry in archive.infolist():
            require(entry.filename not in all_names, "Duplicate ZIP entry: %s" % entry.filename)
            all_names.add(entry.filename)
            require(not entry.filename.startswith(("/", "\\")) and "\\" not in entry.filename
                    and ".." not in entry.filename.split("/"), "Unsafe ZIP entry path")
            entries[entry.filename] = archive.read(entry)
    return entries, {"path": str(path), "bytes": len(blob), "sha256": hashlib.sha256(blob).hexdigest()}


def compare_entries(final, candidate, candidate_version, release_version):
    missing = sorted(set(candidate) - set(final))
    added = sorted(set(final) - set(candidate))
    require(not missing and not added,
            "ZIP entry set differs; removed=%s added=%s" % (missing, added))
    normalized = []
    different = []
    exact = 0
    for name in sorted(final):
        before, after = candidate[name], final[name]
        if before == after:
            exact += 1
            continue
        if name.endswith(".class"):
            adjusted, indexes = normalize_class_version(before, candidate_version, release_version)
            if adjusted == after and indexes:
                normalized.append({"entry": name, "kind": "constant_pool_utf8_version", "indexes": indexes})
                continue
        elif name == "mcmod.info":
            adjusted = normalize_metadata_version(before, candidate_version, release_version)
            if adjusted == after:
                normalized.append({"entry": name, "kind": "mcmod.info.version"})
                continue
        # The current convention manifest contains no changing version/git fields.
        # Therefore the manifest is deliberately required to match byte for byte.
        different.append(name)
    require(not different, "Unapproved ZIP content differences: %s" % different)
    return {"zip_entries": len(final), "byte_identical_entries": exact,
            "normalized_entries": normalized,
            "manifest_policy": "exact bytes; no manifest field normalization"}


def properties(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            require(key.strip() not in values, "Duplicate Gradle property: %s" % key)
            values[key.strip()] = value.strip()
    return values


def git(repo, *arguments):
    result = subprocess.run(["git", *arguments], cwd=repo, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    require(result.returncode == 0, "git %s failed: %s" % (" ".join(arguments), result.stderr.strip()))
    return result.stdout.strip()


def resolve_input(repo, value):
    return (value if value.is_absolute() else repo / value).resolve()


def verify(args, receipt):
    repo = args.repo.resolve()
    final_path = resolve_input(repo, args.jar)
    candidate_path = resolve_input(repo, args.candidate)
    receipt.update({"repo": str(repo), "checks": []})
    require(Path(git(repo, "rev-parse", "--show-toplevel")).resolve() == repo,
            "--repo must name the Git repository root")
    state = git(repo, "status", "--porcelain=v1", "--untracked-files=all")
    receipt["git_status"] = state
    require(not state, "Git worktree is not clean")
    props = properties(repo / "gradle.properties")
    release = props.get("RELEASE_VERSION", "")
    require(re.fullmatch(r"\d+\.\d+\.\d+", release), "Invalid RELEASE_VERSION")
    require(props.get("modId") == "gtfi" and props.get("modGroup") == "com.miaokatze.gtfi",
            "Unexpected mod identity in Gradle properties")
    tag = "v" + release
    head = git(repo, "rev-parse", "HEAD")
    tag_commit = git(repo, "rev-parse", "refs/tags/" + tag + "^{commit}")
    receipt.update({"release_version": release, "build_version": tag,
                    "head": head, "tag": tag, "tag_commit": tag_commit})
    require(head == tag_commit, "HEAD does not match " + tag)
    require(final_path.name == "gtfi-" + tag + ".jar", "Final normal JAR filename must match the release tag")
    require(final_path != candidate_path, "Candidate must be the separately retained tested JAR")
    receipt["checks"].append("clean Git worktree; HEAD equals release tag; filename matches")
    final, receipt["artifact"] = read_jar(final_path)
    candidate, receipt["tested_candidate"] = read_jar(candidate_path)
    tags_path = "com/miaokatze/gtfi/Tags.class"
    require(tags_path in final and tags_path in candidate, "Tags.class is missing")
    final_tags = tag_constants(final[tags_path])
    candidate_tags = tag_constants(candidate[tags_path])
    _, final_metadata = read_metadata(final["mcmod.info"])
    _, candidate_metadata = read_metadata(candidate["mcmod.info"])
    candidate_version = candidate_metadata.get("version")
    require(isinstance(candidate_version, str) and len(candidate_version) >= 5
            and candidate_version.isascii() and not re.search(r"\s|\$\{", candidate_version),
            "Invalid candidate build version")
    require(final_metadata.get("version") == final_tags["VERSION"] == tag,
            "Final metadata and Tags.VERSION must exactly match the release tag")
    require(candidate_tags["VERSION"] == candidate_version,
            "Candidate metadata and Tags.VERSION disagree")
    require(final_tags["RELEASE_VERSION"] == candidate_tags["RELEASE_VERSION"] == release,
            "Tags.RELEASE_VERSION must match the declared release in both JARs")
    require(not any(marker in final_tags["VERSION"] for marker in ("dirty", "master+")),
            "Final JAR carries a dirty or branch version")
    expected_url = "https://github.com/MIAOKATZE/GT-Funny-Idea"
    require(final_metadata.get("url") == expected_url, "Metadata URL does not reference this repository")
    require(final_metadata.get("name") == props.get("modName")
            and final_metadata.get("mcversion") == props.get("minecraftVersion"), "Metadata identity mismatch")
    receipt.update({"metadata_url": expected_url, "candidate_build_version": candidate_version,
                    "final_tags": final_tags, "candidate_tags": candidate_tags})
    receipt["checks"].append("metadata URL/identity and actual Tags string constants")
    class_counts = {}
    for label, entries in (("final", final), ("candidate", candidate)):
        classes = [name for name in entries if name.endswith(".class")]
        require(classes, "No classes in " + label + " JAR")
        bad = [name for name in classes if class_pool(entries[name])[0] != 52]
        require(not bad, "Non-Java-8 classes in %s: %s" % (label, bad))
        class_counts[label] = len(classes)
    receipt["class_major"] = 52
    receipt["class_counts"] = class_counts
    receipt["checks"].append("every final and candidate class uses Java 8 major 52")
    resource_root = repo / "src/main/resources"
    resources = sorted(path for path in resource_root.rglob("*") if path.is_file())
    require(resources, "Source resources are missing")
    required = {
        "mcmod.info", "META-INF/MANIFEST.MF",
        "assets/gtfi/lang/en_US.lang", "assets/gtfi/lang/zh_CN.lang",
        "assets/gtfi/textures/blocks/warp_ejector.png",
        "assets/gtfi/textures/items/portable_ejector.png",
        "assets/gtfi/textures/items/warp_transferer.png",
        "assets/gtfi/textures/gui/pocket/POCKET_C2_btn.png",
        "assets/gtfi/textures/gui/pocket/POCKET_C2_btn_pressed.png",
        "assets/gtfi/textures/gui/pocket/POCKET_C2_cloth.png",
        "assets/gtfi/textures/gui/pocket/POCKET_C2_panel.png",
    }
    require(required <= set(final), "Required runtime resources missing: %s" % sorted(required - set(final)))
    for source in resources:
        name = source.relative_to(resource_root).as_posix()
        require(name in final, "Source resource absent from final JAR: " + name)
        expected = source.read_bytes()
        if name == "mcmod.info":
            for key, value in {"modId": "gtfi", "modName": props["modName"],
                               "modVersion": tag, "minecraftVersion": props["minecraftVersion"]}.items():
                expected = expected.replace(("${" + key + "}").encode("ascii"), value.encode("utf-8"))
            require(read_metadata(expected)[0] == read_metadata(final[name])[0],
                    "Final metadata differs from expanded source metadata")
        else:
            require(final[name] == expected, "Final resource differs from source: " + name)
    receipt["resources"] = [path.relative_to(resource_root).as_posix() for path in resources]
    receipt["checks"].append("all source and required runtime resources present and source contents verified")
    receipt["candidate_comparison"] = compare_entries(final, candidate, candidate_version, tag)
    receipt["checks"].append("candidate ZIP contents identical except precise complete version values")
    require(not git(repo, "status", "--porcelain=v1", "--untracked-files=all"),
            "Git worktree changed during verification")
    require(git(repo, "rev-parse", "HEAD") == head
            and git(repo, "rev-parse", "refs/tags/" + tag + "^{commit}") == tag_commit,
            "Git HEAD or release tag changed during verification")
    require(hashlib.sha256(final_path.read_bytes()).hexdigest() == receipt["artifact"]["sha256"]
            and hashlib.sha256(candidate_path.read_bytes()).hexdigest() == receipt["tested_candidate"]["sha256"],
            "A JAR changed during verification")
    receipt["checks"].append("Git identity/worktree and JAR hashes unchanged at completion")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, default=Path("."))
    parser.add_argument("--jar", type=Path, required=True, help="Final normal JAR")
    parser.add_argument("--candidate", type=Path, required=True, help="Actual retained native-tested normal JAR")
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args(argv)
    destination = resolve_input(args.repo.resolve(), args.receipt)
    build_root = args.repo.resolve() / "build"
    if not destination.is_relative_to(build_root) or destination.suffix.lower() != ".json":
        print("FAIL: receipt must be a JSON file inside the repository build directory", file=sys.stderr)
        return 1
    protected = [resolve_input(args.repo.resolve(), args.jar), resolve_input(args.repo.resolve(), args.candidate)]
    if destination in protected:
        print("FAIL: receipt path would overwrite an input", file=sys.stderr)
        return 1
    receipt = {"schema": "gtfi-delivery-verification-1", "status": "FAIL",
               "verified_at_utc": datetime.now(timezone.utc).isoformat()}
    code = 1
    try:
        verify(args, receipt)
        receipt["status"] = "PASS"
        code = 0
    except Exception as error:
        receipt["error"] = "%s: %s" % (type(error).__name__, error)
    try:
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    except OSError as error:
        print("FAIL: cannot write receipt: %s" % error, file=sys.stderr)
        return 1
    print(json.dumps(receipt, indent=2, ensure_ascii=False))
    print("%s: %s%s" % (receipt["status"], destination,
                           "; " + receipt["error"] if "error" in receipt else ""), file=sys.stderr)
    return code


if __name__ == "__main__":
    sys.exit(main())
