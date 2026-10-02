"""End-to-end checks against the disposable Compose topology (mock providers only)."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import time
from urllib import request, error

BASE = os.getenv("SISACAO_API_URL", "http://127.0.0.1:8080")
AGENTS = os.getenv("SISACAO_AGENTS_URL", "http://127.0.0.1:8081")
UI = os.getenv("SISACAO_UI_URL", "http://127.0.0.1:3000")
TOKEN = os.getenv("BRIDGE_TOKEN", "local-bridge-development-only")
EVIDENCE = Path(".local/smoke-run.json")


def http(path, method="GET", body=None, token=None, base=BASE):
    headers = {"Content-Type": "application/json"}
    if token is not None:
        headers["X-Bridge-Token"] = token
    req = request.Request(base + path, method=method, headers=headers,
                          data=json.dumps(body).encode() if body is not None else None)
    try:
        with request.urlopen(req, timeout=10) as response:
            data = response.read()
            return response.status, json.loads(data) if data else None
    except error.HTTPError as exc:
        return exc.code, None


def wait_ready():
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        try:
            if http("/actuator/health")[0] == 200 and http("/actuator/health", base=AGENTS)[0] == 200 \
                    and len(http("/api/market/ticks")[1]) >= 2 and http("/api/agents", base=UI)[0] == 200:
                return
        except (OSError, ValueError, TypeError):
            pass
        time.sleep(1)
    raise AssertionError("Topology did not become ready within 120s")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-persistence", action="store_true")
    args = parser.parse_args()
    wait_ready()
    status, config = http("/api/status")
    assert status == 200 and config == {"marketSource": "SIMULATED", "tradingEnabled": False}, config
    if args.verify_persistence:
        expected = json.loads(EVIDENCE.read_text())["runId"]
        assert any(run["id"] == expected for run in http("/api/runs")[1])
        print("PASS persisted run survives backend restart")
        return
    ticks = http("/api/market/ticks")[1]
    assert {"EURUSD", "GBPUSD"} <= {tick["symbol"] for tick in ticks}
    assert all(tick["source"] == "SIMULATED" for tick in ticks)
    print("PASS bridge → API → PostgreSQL → frontend proxy")
    # Briefly pin an existing simulated symbol; the bridge resumes updating it in 5s.
    # This avoids leaving a synthetic stale symbol that would block later analyses.
    now = datetime.now(timezone.utc) + timedelta(seconds=5)
    tick = {"symbol": "EURUSD", "bid": 1.2, "ask": 1.3, "observedAt": now.isoformat(), "source": "SIMULATED"}
    assert http("/api/market/ticks", "POST", tick)[0] == 401
    assert http("/api/market/ticks", "POST", tick, TOKEN)[0] == 202
    for update in ({"bid": -1}, {"ask": 1}, {"symbol": "../bad"}, {"source": "MT5"},
                   {"observedAt": (now + timedelta(minutes=1)).isoformat()}):
        assert http("/api/market/ticks", "POST", tick | update, TOKEN)[0] == 400, update
    for observed in (now, now - timedelta(seconds=1)):
        assert http("/api/market/ticks", "POST", tick | {"bid": 1.1, "observedAt": observed.isoformat()}, TOKEN)[0] == 202
    stored = next(row for row in http("/api/market/ticks")[1] if row["symbol"] == "EURUSD")
    assert stored["bid"] == 1.2
    print("PASS validation, token, source isolation and out-of-order/idempotent ingestion")
    agents = http("/api/agents")[1]
    assert len(agents) == 2
    for agent in agents:
        status, result = http(f'/api/agents/{agent["id"]}/runs', "POST")
        assert status == 200, (status, result)
        assert result["result"]["action"] == "HOLD" and result["provider"] == "mock"
        assert len(result["harnessSha256"]) == 64
        assert any(run["id"] == result["runId"] and run["status"] == "COMPLETED" for run in http("/api/runs")[1])
    assert http("/api/agents/unknown/runs", "POST")[0] == 404
    assert http("/api/orders", "POST", {})[0] == 404
    EVIDENCE.parent.mkdir(exist_ok=True)
    EVIDENCE.write_text(json.dumps({"runId": result["runId"]}))
    print("PASS both agents, version/hash, saved runs and absent trading endpoint")
    for path in ("/actuator/metrics/sisacao.runs", "/actuator/metrics/sisacao.ticks.received"):
        status, metric = http(path)
        assert status == 200 and any(tag["tag"] == "source" for tag in metric["availableTags"])
    print("PASS health and counters tagged by market source")


if __name__ == "__main__":
    main()
