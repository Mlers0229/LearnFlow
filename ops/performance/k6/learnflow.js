import http from "k6/http";
import { check, fail, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const workload = JSON.parse(open("../workload-model.json"));
const thresholds = JSON.parse(open("../thresholds.json"));
const testDataPath = __ENV.LEARNFLOW_PERF_TEST_DATA || "../test-data.example.json";
const testData = JSON.parse(open(testDataPath));

const profileName = __ENV.LEARNFLOW_PERF_PROFILE || "smoke";
const profile = workload.profiles[profileName];
const baseUrl = (__ENV.LEARNFLOW_BASE_URL || "http://127.0.0.1").replace(/\/$/, "");
const summaryPath = __ENV.LEARNFLOW_PERF_SUMMARY || "k6-summary.json";
const confirmation = __ENV.LEARNFLOW_PERF_CONFIRM || "";
const targetEnvironment = __ENV.LEARNFLOW_PERF_ENVIRONMENT || "";
const allowedHosts = (__ENV.LEARNFLOW_PERF_ALLOWED_HOSTS || "127.0.0.1,localhost")
  .split(",")
  .map(function (value) { return value.trim().toLowerCase(); })
  .filter(function (value) { return value.length > 0; });

const operationSuccess = new Rate("learnflow_operation_success");
const operationDuration = new Trend("learnflow_operation_duration_ms", true);
const planTaskSuccess = new Rate("learnflow_plan_task_success");
const planTaskDuration = new Trend("learnflow_plan_task_end_to_end_ms", true);
const planTaskPolls = new Trend("learnflow_plan_task_poll_count", true);

validateConfiguration();

export const options = {
  setupTimeout: "3m",
  teardownTimeout: "30s",
  discardResponseBodies: false,
  scenarios: buildScenarios(),
  thresholds: {
    "http_req_failed{operation:api_read}": [
      "rate<" + thresholds.ordinaryApi.failureRateMaximum
    ],
    "learnflow_operation_duration_ms{operation:api_read}": [
      "p(95)<" + thresholds.ordinaryApi.p95Milliseconds
    ],
    "http_req_failed{operation:login}": [
      "rate<" + thresholds.login.failureRateMaximum
    ],
    "learnflow_operation_duration_ms{operation:login}": [
      "p(95)<" + thresholds.login.p95Milliseconds
    ],
    "learnflow_operation_success{operation:rag}": [
      "rate>=" + thresholds.rag.successRateMinimum
    ],
    "learnflow_operation_duration_ms{operation:rag}": [
      "p(95)<" + thresholds.rag.p95Milliseconds
    ],
    "learnflow_operation_success{operation:tutor}": [
      "rate>=" + thresholds.tutor.successRateMinimum
    ],
    "learnflow_operation_duration_ms{operation:tutor}": [
      "p(95)<" + thresholds.tutor.p95Milliseconds
    ],
    "learnflow_plan_task_success": [
      "rate>=" + thresholds.planTask.successRateMinimum
    ],
    "learnflow_plan_task_end_to_end_ms": [
      "p(95)<" + thresholds.planTask.p95EndToEndMilliseconds
    ],
    "dropped_iterations": [
      "count<=" + thresholds.saturation.droppedIterationsMaximum
    ]
  }
};

function validateConfiguration() {
  if (!profile) {
    throw new Error("Unknown LEARNFLOW_PERF_PROFILE: " + profileName);
  }
  const hostMatch = baseUrl.match(/^https?:\/\/([^/:]+)/i);
  if (!hostMatch) {
    throw new Error("LEARNFLOW_BASE_URL must be an absolute HTTP(S) URL");
  }
  const host = hostMatch[1].toLowerCase();
  const isLocal = host === "127.0.0.1" || host === "localhost";
  if (!isLocal && targetEnvironment !== "staging") {
    throw new Error("Set LEARNFLOW_PERF_ENVIRONMENT=staging for non-local runs");
  }
  if (!isLocal && String(testData.environment).toLowerCase() !== "staging") {
    throw new Error("Mounted performance fixtures must identify staging");
  }
  if (!isLocal && allowedHosts.indexOf(host) < 0) {
    throw new Error("Target host is not listed in LEARNFLOW_PERF_ALLOWED_HOSTS");
  }
  if ((!isLocal || profileName !== "smoke") && confirmation !== "staging-capacity-test") {
    throw new Error("Set LEARNFLOW_PERF_CONFIRM=staging-capacity-test for non-local or non-smoke runs");
  }
  if (String(testData.environment || "").toLowerCase() === "production") {
    throw new Error("Performance test data must never identify the production environment");
  }
  if (!Array.isArray(testData.users) || testData.users.length < profile.requiredUsers) {
    throw new Error(
      profileName + " requires at least " + profile.requiredUsers + " dedicated test users"
    );
  }
  testData.users.forEach(function (user) {
    if (!String(user.username || "").startsWith("perf_")) {
      throw new Error("Every performance-test username must start with perf_");
    }
    if (!user.password || String(user.password).indexOf("replace-") === 0) {
      throw new Error("Replace placeholder passwords through an ignored test-data file");
    }
    if (!(Number(user.planId) > 0) || !(Number(user.dayId) > 0)) {
      throw new Error("Each test user needs owned planId and dayId fixtures");
    }
  });
}

function buildScenarios() {
  if (profileName === "smoke") {
    return {
      smoke_workflow: {
        executor: "per-vu-iterations",
        vus: 1,
        iterations: 1,
        maxDuration: profile.duration,
        exec: "smokeWorkflow"
      }
    };
  }

  const scenarios = {
    login: {
      executor: "constant-arrival-rate",
      rate: profile.loginRps,
      timeUnit: "1s",
      duration: profile.duration || "7m",
      preAllocatedVUs: Math.max(2, profile.loginRps * 2),
      maxVUs: Math.max(10, profile.loginRps * 5),
      exec: "loginScenario",
      gracefulStop: "15s"
    }
  };

  if (profileName === "spike") {
    scenarios.api_read = {
      executor: "ramping-arrival-rate",
      startRate: 10,
      timeUnit: "1s",
      stages: profile.ordinaryApiStages,
      preAllocatedVUs: profile.preAllocatedApiVUs,
      maxVUs: profile.maxApiVUs,
      exec: "apiReadScenario",
      gracefulStop: "30s"
    };
    ["plan", "rag", "tutor"].forEach(function (operation) {
      scenarios[operation] = {
        executor: "ramping-vus",
        startVUs: 1,
        stages: profile.aiStages[operation],
        exec: operation + "Scenario",
        gracefulRampDown: "30s",
        gracefulStop: "2m"
      };
    });
    return scenarios;
  }

  scenarios.api_read = {
    executor: "constant-arrival-rate",
    rate: profile.ordinaryApiRps,
    timeUnit: "1s",
    duration: profile.duration,
    preAllocatedVUs: profile.preAllocatedApiVUs,
    maxVUs: profile.maxApiVUs,
    exec: "apiReadScenario",
    gracefulStop: "30s"
  };
  ["plan", "rag", "tutor"].forEach(function (operation) {
    scenarios[operation] = {
      executor: "constant-vus",
      vus: profile.aiConcurrency[operation],
      duration: profile.duration,
      exec: operation + "Scenario",
      gracefulStop: "2m"
    };
  });
  return scenarios;
}

function jsonBody(response) {
  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

function requestParams(session, operation, timeout) {
  ensureSession(session);
  return {
    headers: {
      Authorization: "Bearer " + session.accessToken,
      "Content-Type": "application/json",
      "X-Load-Test-Run": __ENV.LEARNFLOW_PERF_RUN_ID || "unversioned"
    },
    tags: { operation: operation },
    timeout: timeout
  };
}

function recordResponse(response, operation, expectedStatuses) {
  const ok = expectedStatuses.indexOf(response.status) >= 0;
  operationSuccess.add(ok, { operation: operation });
  operationDuration.add(response.timings.duration, { operation: operation });
  const assertions = {};
  assertions[operation + " returned expected status"] = function () { return ok; };
  check(response, assertions, { operation: operation });
  return ok;
}

function login(user, operation) {
  const response = http.post(
    baseUrl + "/api/auth/login",
    JSON.stringify({ username: user.username, password: user.password }),
    {
      headers: { "Content-Type": "application/json" },
      tags: { operation: operation },
      timeout: "5s"
    }
  );
  const ok = recordResponse(response, operation, [200]);
  const body = jsonBody(response);
  if (!ok || !body || !body.accessToken) {
    return null;
  }
  return {
    username: user.username,
    accessToken: body.accessToken,
    planId: Number(user.planId),
    dayId: Number(user.dayId),
    refreshAt: Date.now() + Math.max(
      30, Number(body.expiresInSeconds || 300) - 60
    ) * 1000
  };
}

export function setup() {
  const healthPath = testData.healthPath || "/health/ready";
  const health = http.get(baseUrl + healthPath, {
    tags: { operation: "setup_health" },
    timeout: "5s"
  });
  if (health.status !== 200) {
    fail("Target readiness check failed with status " + health.status);
  }

  const sessions = [];
  testData.users.forEach(function (user) {
    const session = login(user, "setup_login");
    if (!session) {
      fail("Unable to authenticate dedicated performance user " + user.username);
    }
    sessions.push(session);
  });
  return {
    sessions: sessions,
    environment: targetEnvironment || String(testData.environment || "local")
  };
}

function sessionFor(data) {
  return data.sessions[(__VU - 1) % data.sessions.length];
}

function ensureSession(session) {
  if (Date.now() < Number(session.refreshAt || 0)) {
    return;
  }
  const user = testData.users.find(function (candidate) {
    return candidate.username === session.username;
  });
  const fresh = user ? login(user, "token_refresh") : null;
  if (!fresh) {
    fail("Unable to refresh performance-test access token");
  }
  session.accessToken = fresh.accessToken;
  session.refreshAt = fresh.refreshAt;
}

export function loginScenario() {
  const user = testData.users[(__VU + __ITER - 1) % testData.users.length];
  login(user, "login");
}

export function apiReadScenario(data) {
  const session = sessionFor(data);
  const selector = (__VU + __ITER) % 3;
  let path = "/api/plan/recent?limit=5";
  if (selector === 1) {
    path = "/api/plan/" + session.planId;
  } else if (selector === 2) {
    path = "/api/plan/" + session.planId + "/progress";
  }
  const response = http.get(baseUrl + path, requestParams(session, "api_read", "3s"));
  recordResponse(response, "api_read", [200]);
}

export function ragScenario(data) {
  const session = sessionFor(data);
  const response = http.get(
    baseUrl + "/api/plan/" + session.planId + "/resources",
    requestParams(session, "rag", "16s")
  );
  recordResponse(response, "rag", [200]);
  sleep(1);
}

export function tutorScenario(data) {
  const session = sessionFor(data);
  const response = http.get(
    baseUrl + "/api/plan/day/" + session.dayId + "/exercises",
    requestParams(session, "tutor", "46s")
  );
  recordResponse(response, "tutor", [200]);
  sleep(1);
}

export function planScenario(data) {
  const session = sessionFor(data);
  const startedAt = Date.now();
  const idempotencyKey = [
    "perf", profileName, session.username, __VU, __ITER, startedAt
  ].join("-");

  const params = requestParams(session, "plan_submit", "5s");
  params.headers["Idempotency-Key"] = idempotencyKey;
  const response = http.post(
    baseUrl + "/api/plan/tasks",
    JSON.stringify({
      goalText: "性能测试专用 Java 学习计划 " + idempotencyKey,
      durationWeeks: 1,
      hoursPerDay: 1,
      level: "beginner",
      targetRole: "backend developer",
      preferredStyle: "practice",
      constraints: ["staging performance test"],
      finalDeliverable: "small demo"
    }),
    params
  );
  const accepted = recordResponse(response, "plan_submit", [202]);
  const body = jsonBody(response);
  if (!accepted || !body || !body.id) {
    planTaskSuccess.add(false);
    return;
  }

  let polls = 0;
  let completed = false;
  const deadline = startedAt + thresholds.planTask.p95EndToEndMilliseconds;
  while (Date.now() < deadline) {
    sleep(2);
    polls += 1;
    const taskResponse = http.get(
      baseUrl + "/api/tasks/" + body.id,
      requestParams(session, "task_poll", "3s")
    );
    if (!recordResponse(taskResponse, "task_poll", [200])) {
      break;
    }
    const task = jsonBody(taskResponse);
    if (!task) {
      break;
    }
    if (task.status === "SUCCEEDED") {
      completed = true;
      break;
    }
    if (["FAILED", "CANCELLED"].indexOf(task.status) >= 0) {
      break;
    }
  }
  planTaskPolls.add(polls);
  planTaskDuration.add(Date.now() - startedAt);
  planTaskSuccess.add(completed);
  check(completed, { "plan task completed successfully": function (value) { return value; } });
}

export function smokeWorkflow(data) {
  apiReadScenario(data);
  ragScenario(data);
  tutorScenario(data);
  planScenario(data);
}

export function handleSummary(data) {
  const artifact = {
    schemaVersion: 1,
    profile: profileName,
    environment: targetEnvironment || String(testData.environment || "local"),
    runId: __ENV.LEARNFLOW_PERF_RUN_ID || "unversioned",
    sourceRevision: __ENV.LEARNFLOW_RELEASE_VERSION || "unversioned",
    generatedAt: new Date().toISOString(),
    workload: profile,
    summary: data
  };
  const result = {};
  result[summaryPath] = JSON.stringify(artifact, null, 2);
  result.stdout = "LearnFlow performance summary written to " + summaryPath + "\n";
  return result;
}
