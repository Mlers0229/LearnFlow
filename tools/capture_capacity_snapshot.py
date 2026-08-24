from __future__ import annotations

import argparse
import json
import math
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen


def reduce_values(values: list[float], reducer: str) -> float:
    if not values:
        raise ValueError("cannot reduce an empty metric series")
    if reducer == "max":
        return max(values)
    if reducer == "min":
        return min(values)
    if reducer == "last":
        return values[-1]
    if reducer == "delta":
        return max(0.0, values[-1] - values[0])
    raise ValueError(f"unsupported reducer: {reducer}")


def evaluate(value: float, spec: dict[str, Any]) -> tuple[bool, str]:
    minimum = spec.get("minimum")
    maximum = spec.get("maximum")
    if minimum is not None and value < float(minimum):
        return False, f"{value:g} is below minimum {minimum}"
    if maximum is not None and value > float(maximum):
        return False, f"{value:g} exceeds maximum {maximum}"
    return True, "within configured boundary"


def query_range(
    prometheus_url: str,
    promql: str,
    start: str,
    end: str,
    step: str,
    bearer_token: str | None,
) -> list[float]:
    query = urlencode({"query": promql, "start": start, "end": end, "step": step})
    request = Request(prometheus_url.rstrip("/") + "/api/v1/query_range?" + query)
    if bearer_token:
        request.add_header("Authorization", "Bearer " + bearer_token)
    with urlopen(request, timeout=30) as response:
        payload = json.load(response)
    if payload.get("status") != "success":
        raise RuntimeError("Prometheus returned a non-success response")
    result = payload.get("data", {}).get("result", [])
    values: list[float] = []
    for series in result:
        for sample in series.get("values", []):
            value = float(sample[1])
            if math.isfinite(value):
                values.append(value)
    return values


def safe_source(prometheus_url: str) -> str:
    parsed = urlsplit(prometheus_url)
    if parsed.username or parsed.password:
        raise ValueError("Prometheus URL must not embed credentials")
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("Prometheus URL must be absolute HTTP(S)")
    port = f":{parsed.port}" if parsed.port else ""
    return f"{parsed.scheme}://{parsed.hostname}{port}"


def capture(args: argparse.Namespace) -> dict[str, Any]:
    specs = json.loads(args.queries.read_text(encoding="utf-8"))
    token = os.environ.get("PROMETHEUS_BEARER_TOKEN")
    results: list[dict[str, Any]] = []
    all_passed = True
    for spec in specs["queries"]:
        try:
            values = query_range(
                args.prometheus_url,
                spec["promql"],
                args.start,
                args.end,
                args.step,
                token,
            )
            if not values:
                if spec.get("required", False):
                    raise ValueError("required query returned no samples")
                results.append({"name": spec["name"], "status": "NO_DATA", "required": False})
                continue
            reduced = reduce_values(values, spec["reducer"])
            passed, detail = evaluate(reduced, spec)
            all_passed = all_passed and passed
            results.append(
                {
                    "name": spec["name"],
                    "status": "PASS" if passed else "FAIL",
                    "value": reduced,
                    "sampleCount": len(values),
                    "reducer": spec["reducer"],
                    "minimum": spec.get("minimum"),
                    "maximum": spec.get("maximum"),
                    "detail": detail,
                }
            )
        except (HTTPError, URLError, OSError, RuntimeError, ValueError) as exc:
            all_passed = False
            results.append(
                {
                    "name": spec.get("name", "unknown"),
                    "status": "ERROR",
                    "detail": str(exc),
                }
            )
    return {
        "schemaVersion": 1,
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "source": safe_source(args.prometheus_url),
        "window": {"start": args.start, "end": args.end, "step": args.step},
        "passed": all_passed,
        "results": results,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Capture bounded LearnFlow capacity telemetry from Prometheus"
    )
    parser.add_argument("--prometheus-url", required=True)
    parser.add_argument("--start", required=True, help="RFC3339 or Unix timestamp")
    parser.add_argument("--end", required=True, help="RFC3339 or Unix timestamp")
    parser.add_argument("--step", default="15s")
    parser.add_argument(
        "--queries",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "ops"
        / "performance"
        / "telemetry-queries.json",
    )
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    artifact = capture(args)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(artifact, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    if not artifact["passed"]:
        raise SystemExit("Capacity telemetry contains failed, missing, or invalid required metrics")
    print(f"Capacity telemetry written to {args.output}")


if __name__ == "__main__":
    main()
