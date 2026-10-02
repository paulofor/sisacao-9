CREATE TABLE market_tick (
    symbol VARCHAR(32) NOT NULL,
    source VARCHAR(16) NOT NULL CHECK (source IN ('SIMULATED', 'MT5')),
    bid NUMERIC(20,8) NOT NULL CHECK (bid > 0),
    ask NUMERIC(20,8) NOT NULL CHECK (ask >= bid),
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (symbol, source)
);

CREATE TABLE agent_run (
    id UUID PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    source VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('COMPLETED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    response_json TEXT,
    error VARCHAR(300)
);
CREATE INDEX agent_run_created_at_idx ON agent_run (created_at DESC);
