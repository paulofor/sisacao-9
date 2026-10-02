package br.com.sisacao.backend;

import br.com.sisacao.contracts.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PlatformRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void saveTick(MarketTick tick) {
        jdbc.update("""
            INSERT INTO market_tick(symbol, source, bid, ask, observed_at) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(symbol, source) DO UPDATE SET bid=EXCLUDED.bid, ask=EXCLUDED.ask, observed_at=EXCLUDED.observed_at
            WHERE EXCLUDED.observed_at > market_tick.observed_at
            """, tick.symbol(), tick.source().name(), tick.bid(), tick.ask(), Timestamp.from(tick.observedAt()));
    }

    public List<MarketTick> ticks(MarketTick.Source source) {
        return jdbc.query("SELECT * FROM market_tick WHERE source=? ORDER BY symbol LIMIT 50",
            (rs, row) -> new MarketTick(rs.getString("symbol"), rs.getBigDecimal("bid"), rs.getBigDecimal("ask"),
                rs.getTimestamp("observed_at").toInstant(), MarketTick.Source.valueOf(rs.getString("source"))), source.name());
    }

    public void saveRun(UUID id, String agentId, MarketTick.Source source, Instant createdAt, long duration,
            List<MarketTick> snapshot, AnalysisResponse response, String error) {
        jdbc.update("""
            INSERT INTO agent_run(id, agent_id, source, status, created_at, duration_ms, snapshot_json, response_json, error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, id, agentId, source.name(), error == null ? "COMPLETED" : "FAILED", Timestamp.from(createdAt),
            duration, encode(snapshot), response == null ? null : encode(response), error);
    }

    public List<RunView> runs(MarketTick.Source source) {
        return jdbc.query("SELECT * FROM agent_run WHERE source=? ORDER BY created_at DESC LIMIT 20",
            (rs, row) -> new RunView(rs.getObject("id", UUID.class), rs.getString("agent_id"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("duration_ms"),
                decode(rs.getString("response_json")), rs.getString("error")), source.name());
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Falha ao serializar registro", e); }
    }

    private AnalysisResponse decode(String value) {
        if (value == null) return null;
        try { return json.readValue(value, AnalysisResponse.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Registro de análise inválido", e); }
    }

    public record RunView(UUID id, String agentId, String status, Instant createdAt, long durationMs,
            AnalysisResponse response, String error) {}
}
