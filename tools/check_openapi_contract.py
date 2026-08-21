import json
import sys
import urllib.request
from pathlib import Path


def load_spec(source: str) -> dict:
    if source.startswith(("http://", "https://")):
        with urllib.request.urlopen(source, timeout=10) as response:  # noqa: S310 - CI localhost only
            return json.load(response)
    return json.loads(Path(source).read_text(encoding="utf-8"))


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: check_openapi_contract.py <spec-url-or-file> <required-paths-file>")
        return 2
    spec = load_spec(sys.argv[1])
    actual = set(spec.get("paths", {}))
    required = {
        line.strip()
        for line in Path(sys.argv[2]).read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }
    missing = sorted(required - actual)
    if missing:
        print("OpenAPI contract is missing required paths:")
        for path in missing:
            print(f"- {path}")
        return 1
    print(f"OpenAPI contract check passed: {len(required)} required paths present")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
