from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from typing import NoReturn

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration"
CHECKSUM_PATH = ROOT / "ops" / "deployment" / "flyway-checksums.json"
POLICY_PATH = ROOT / "ops" / "deployment" / "release-policy.json"


def fail(message: str) -> NoReturn:
    raise SystemExit(f"Flyway contract failed: {message}")


def main() -> None:
    manifest = json.loads(CHECKSUM_PATH.read_text(encoding="utf-8"))
    policy = json.loads(POLICY_PATH.read_text(encoding="utf-8"))
    reviewed = int(manifest["reviewedThroughVersion"])
    if reviewed != int(policy["migration"]["reviewedThroughVersion"]):
        fail("reviewed migration versions disagree between policy and checksum manifest")

    migrations: dict[int, Path] = {}
    for path in MIGRATION_DIR.glob("V*__*.sql"):
        match = re.fullmatch(r"V(\d+)__.+\.sql", path.name)
        if not match:
            fail(f"invalid migration name: {path.name}")
        version = int(match.group(1))
        if version in migrations:
            fail(f"duplicate version V{version}")
        migrations[version] = path

    expected = list(range(1, max(migrations, default=0) + 1))
    if sorted(migrations) != expected:
        fail(f"versions must be contiguous: found {sorted(migrations)}")

    checksums = manifest["sha256"]
    reviewed_names = {migrations[version].name for version in range(1, reviewed + 1)}
    if set(checksums) != reviewed_names:
        fail("checksum manifest must cover every reviewed migration exactly once")

    for version in range(1, reviewed + 1):
        reviewed_path = migrations.get(version)
        if reviewed_path is None:
            fail(f"reviewed migration V{version} is missing")
        digest = hashlib.sha256(reviewed_path.read_bytes()).hexdigest()
        if digest != checksums[reviewed_path.name]:
            fail(f"reviewed migration was modified: {reviewed_path.name}")

    patterns = [re.compile(value, re.IGNORECASE | re.DOTALL) for value in policy["migration"]["forbiddenNewMigrationPatterns"]]
    for version, path in migrations.items():
        if version <= reviewed:
            continue
        sql = re.sub(r"--.*?$|/\*.*?\*/", " ", path.read_text(encoding="utf-8"), flags=re.MULTILINE | re.DOTALL)
        if any(pattern.search(sql) for pattern in patterns):
            fail(f"V{version} contains a destructive change; use an Expand/Contract migration")

    print(f"Flyway contract OK: V1-V{reviewed} immutable; {len(migrations) - reviewed} new migrations checked")


if __name__ == "__main__":
    main()
