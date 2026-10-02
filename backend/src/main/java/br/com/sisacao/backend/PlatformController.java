package br.com.sisacao.backend;

import br.com.sisacao.contracts.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PlatformController {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformController.class);
    private final PlatformRepository repository;
    private final AgentClient agents;
    private final MeterRegistry metrics;
    private final byte[] bridgeToken;
    private final MarketTick.Source source;

    public PlatformController(PlatformRepository repository, AgentClient agents, MeterRegistry metrics,
            @Value("${app.bridge-token}") String token, @Value("${app.market-source}") MarketTick.Source source) {
        if (token.isBlank()) throw new IllegalArgumentException("BRIDGE_TOKEN é obrigatório");
        this.repository = repository; this.agents = agents; this.metrics = metrics;
        this.bridgeToken = token.getBytes(StandardCharsets.UTF_8); this.source = source;
    }

    @GetMapping("/status")
    public Map<String, Object> status() { return Map.of("marketSource", source, "tradingEnabled", false); }

    @PostMapping("/market/ticks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingest(@RequestHeader(value = "X-Bridge-Token", defaultValue = "") String token, @Valid @RequestBody MarketTick tick) {
        if (!MessageDigest.isEqual(bridgeToken, token.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token da bridge inválido");
        if (tick.source() != source || tick.ask().compareTo(tick.bid()) < 0
                || tick.observedAt().isAfter(Instant.now().plusSeconds(30)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origem, preços ou horário inválidos");
        repository.saveTick(tick);
        metrics.counter("sisacao.ticks.received", "source", source.name()).increment();
    }

    @GetMapping("/market/ticks")
    public List<MarketTick> ticks() { return repository.ticks(source); }

    @GetMapping("/agents")
    public List<AgentDefinition> agents() { return agents.agents(); }

    @GetMapping("/runs")
    public List<PlatformRepository.RunView> runs() { return repository.runs(source); }

    @PostMapping("/agents/{agentId}/runs")
    public AnalysisResponse run(@PathVariable String agentId) {
        if (agents.agents().stream().noneMatch(agent -> agent.id().equals(agentId)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agente não encontrado");
        var snapshot = repository.ticks(source);
        var now = Instant.now();
        if (snapshot.isEmpty() || snapshot.stream().anyMatch(tick -> tick.observedAt().isBefore(now.minusSeconds(60))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aguardando cotações recentes da bridge");
        var id = UUID.randomUUID();
        long start = System.nanoTime();
        AnalysisResponse response;
        try {
            response = agents.analyze(agentId, new AnalysisRequest(id, snapshot));
            if (response == null || !id.equals(response.runId()) || !agentId.equals(response.agentId()) || response.result() == null
                    || !Set.of("mock", "codex").contains(response.provider())
                    || response.harnessVersion() == null || !response.harnessVersion().matches("v[0-9]{1,6}")
                    || response.harnessSha256() == null || !response.harnessSha256().matches("[a-f0-9]{64}"))
                throw new IllegalStateException("Resposta não correlacionada");
        } catch (RuntimeException error) {
            repository.saveRun(id, agentId, source, now, elapsed(start), snapshot, null, "Serviço de agentes indisponível ou resposta inválida");
            metrics.counter("sisacao.runs", "status", "FAILED", "source", source.name()).increment();
            LOG.warn("runId={} agentId={} status=FAILED errorType={}", id, agentId, error.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Análise falhou; consulte o histórico");
        }
        repository.saveRun(id, agentId, source, now, elapsed(start), snapshot, response, null);
        metrics.counter("sisacao.runs", "status", "COMPLETED", "source", source.name()).increment();
        LOG.info("runId={} agentId={} status=COMPLETED provider={}", id, agentId, response.provider());
        return response;
    }

    private long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
