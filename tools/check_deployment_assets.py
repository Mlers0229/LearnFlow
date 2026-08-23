from __future__ import annotations

import copy
import json
import re
from pathlib import Path

from check_release_candidate import validate_candidate

ROOT = Path(__file__).resolve().parents[1]
DEPLOYMENT_DIR = ROOT / "ops" / "deployment"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Deployment contract failed: {message}")


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def main() -> None:
    contract = json.loads((DEPLOYMENT_DIR / "runtime-contract.json").read_text(encoding="utf-8"))
    policy = json.loads((DEPLOYMENT_DIR / "release-policy.json").read_text(encoding="utf-8"))
    production = contract["production"]

    require(contract["schemaVersion"] == 1, "unsupported runtime contract version")
    require(contract["platformSelection"]["productionDeploymentsBlocked"], "production must stay blocked until the platform decision is closed")
    require(production["imageReferenceFormat"].startswith("repository@sha256:"), "production images must use immutable digests")
    database = production["database"]
    require(database["mode"] == "managed-postgresql" and database["pitrRequired"], "managed PostgreSQL with PITR is required")
    require(database["rpoMinutes"] <= 15 and database["rtoMinutes"] <= 60, "RPO/RTO exceeds the roadmap")

    expected_paths = {
        "frontend": ("/health/live", "/health/ready"),
        "backend": ("/actuator/health/liveness", "/actuator/health/readiness"),
        "agent": ("/health/live", "/health/ready"),
    }
    for name, (live_path, ready_path) in expected_paths.items():
        service = production["services"][name]
        require(service["minReplicas"] >= 2, f"{name} requires at least two production replicas")
        require(service["livenessPath"] == live_path and service["readinessPath"] == ready_path, f"{name} probe paths differ from the contract")
        require(service["runAsNonRoot"], f"{name} must run as non-root")
        require(service["readOnlyRootFilesystem"], f"{name} must use a read-only root filesystem")
        require(service["dropAllCapabilities"], f"{name} must drop Linux capabilities")
    require(not production["services"]["agent"]["public"], "Agent must never be public")

    rollout = policy["rollout"]
    require(rollout["strategy"] == "canary", "canary rollout is required")
    require(rollout["trafficPercentSteps"][-1] == 100, "rollout must end at 100 percent")
    require(rollout["rollbackTargetMinutes"] <= 10, "rollback target exceeds ten minutes")
    require(policy["migration"]["strategy"] == "expand-contract", "Expand/Contract is required")

    compose = read("docker-compose.yml")
    require(compose.count("read_only: true") >= 3, "three application services must use read-only filesystems")
    require(compose.count("no-new-privileges:true") >= 3, "three application services must set no-new-privileges")
    require(compose.count("condition: service_healthy") >= 4, "service startup must wait for readiness")
    require("SPRING_PROFILES_ACTIVE" in compose, "Backend profile must be explicit")

    dockerfiles = {
        "backend": read("backend/Dockerfile"),
        "agent": read("agent-platform/Dockerfile"),
        "frontend": read("frontend/Dockerfile"),
    }
    for name, contents in dockerfiles.items():
        require(re.search(r"(?m)^USER\s+(?!root\b).+", contents) is not None, f"{name} image must run as non-root")
        require("HEALTHCHECK" in contents, f"{name} image requires a health check")
        require("RUNTIME_IMAGE" in contents, f"{name} runtime base image must be digest-injectable")

    backend_config = read("backend/src/main/resources/application.yml")
    require("include: livenessState,ping" in backend_config, "Backend liveness group is missing")
    require("include: readinessState,db" in backend_config, "Backend readiness group is missing")
    require('"/actuator/health/**"' in read("backend/src/main/java/com/learnflow/config/SecurityConfig.java"), "Backend probe subpaths must be public")

    agent_main = read("agent-platform/app/main.py")
    require('"/health/live"' in agent_main and '"/health/ready"' in agent_main, "Agent probes are missing")
    nginx = read("frontend/nginx.conf")
    require("location = /health/live" in nginx and "location = /health/ready" in nginx, "Frontend probes are missing")

    for name in ("staging.env.template", "production.env.template"):
        template = (DEPLOYMENT_DIR / name).read_text(encoding="utf-8")
        require("dev-only-" not in template and "change_" not in template, f"{name} contains a development secret")
        require("LEARNFLOW_OTEL_ENABLED=true" in template, f"{name} must enable telemetry")
        require("FLYWAY_BASELINE_ON_MIGRATE=false" in template, f"{name} must disable baseline-on-migrate")
    production_template = (DEPLOYMENT_DIR / "production.env.template").read_text(encoding="utf-8")
    require("SPRING_PROFILES_ACTIVE=production" in production_template, "production profile is missing")
    require("LEARNFLOW_ENV=production" in production_template, "production environment is missing")

    candidate_template = json.loads((DEPLOYMENT_DIR / "release-candidate.template.json").read_text(encoding="utf-8"))
    template_errors = validate_candidate(candidate_template, contract)
    require(len(template_errors) >= 10, "unsafe release template must fail closed")

    selected_contract = copy.deepcopy(contract)
    selected_contract["platformSelection"] = {"status": "selected", "productionDeploymentsBlocked": False}
    accepted_candidate = copy.deepcopy(candidate_template)
    accepted_candidate["releaseVersion"] = "commit-0123456789abcdef"
    accepted_candidate["previousReleaseVersion"] = "commit-fedcba9876543210"
    accepted_candidate["images"] = {
        service: f"ghcr.io/owner/learnflow-{service}@sha256:{'0' * 64}"
        for service in ("frontend", "backend", "agent")
    }
    accepted_candidate["stagingEvidence"] = {name: True for name in accepted_candidate["stagingEvidence"]}
    accepted_candidate["approvals"] = {
        "platformSelected": True,
        "releaseOwner": "release-owner",
        "observationOwner": "observation-owner",
        "rollbackOwner": "rollback-owner",
    }
    require(not validate_candidate(accepted_candidate, selected_contract), "complete immutable release candidate must pass")

    env_example = read(".env.example")
    require("90LEARNFLOW_" not in env_example, ".env.example contains a concatenated variable")
    print("Deployment contract OK: probes, runtime hardening, environments, rollout and rollback policy")


if __name__ == "__main__":
    main()
